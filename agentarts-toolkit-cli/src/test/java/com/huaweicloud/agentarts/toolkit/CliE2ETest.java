package com.huaweicloud.agentarts.toolkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.agentarts.toolkit.commands.CliSupport;
import com.huaweicloud.agentarts.toolkit.operations.ConfigOperation;
import com.huaweicloud.agentarts.toolkit.operations.InitOperation;
import com.huaweicloud.agentarts.toolkit.template.TemplateManager;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the CLI toolkit.
 *
 * <p>These tests verify the complete CLI workflow:
 * <ul>
 *   <li>Init operation: project scaffolding with template rendering</li>
 *   <li>Config operation: full CRUD lifecycle with YAML persistence</li>
 *   <li>CLI command execution: Picocli command parsing + execution + output</li>
 *   <li>Full workflow: init → config → verify integration</li>
 * </ul>
 */
class CliE2ETest {

    @Nested
    class InvokeOperationE2E {

        private PrintStream originalOut;
        private PrintStream originalErr;
        private ByteArrayOutputStream output;

        @BeforeEach
        void captureOutput() {
            originalOut = System.out;
            originalErr = System.err;
            output = new ByteArrayOutputStream();
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        }

        @AfterEach
        void restoreOutput() {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        /**
         * Point the runtime data-plane base URL at a local HttpServer via the
         * {@code AGENTARTS_RUNTIME_DATA_ENDPOINT} system property (the CLI's
         * {@code --endpoint} option stays a query-parameter name, matching the
         * Python SDK). Pass {@code null} to clear.
         */
        private static void setEnv(String key, String value) {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        }

        @Test
        void localInvokeThreadsAllOptionsAndPrintsJson() throws Exception {
            AtomicReference<String> path = new AtomicReference<>();
            AtomicReference<String> query = new AtomicReference<>();
            AtomicReference<String> authorization = new AtomicReference<>();
            AtomicReference<String> session = new AtomicReference<>();
            AtomicReference<String> user = new AtomicReference<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                path.set(exchange.getRequestURI().getPath());
                query.set(exchange.getRequestURI().getRawQuery());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                session.set(exchange.getRequestHeaders().getFirst("x-hw-agentarts-session-id"));
                user.set(exchange.getRequestHeaders().getFirst("X-HW-AgentGateway-User-Id"));
                byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            try {
                int exit = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli())).execute(
                        "invoke", "{}", "--mode", "local",
                        "--port", String.valueOf(server.getAddress().getPort()),
                        "--custom-path", "/stream/", "--endpoint", "named endpoint",
                        "--session", "unit-session", "--bearer-token", "unit-token",
                        "--user-id", "unit-user");
                assertEquals(0, exit);
                assertEquals("/invocations/stream", path.get());
                assertEquals("endpoint=named%20endpoint", query.get());
                assertEquals("Bearer unit-token", authorization.get());
                assertEquals("unit-session", session.get());
                assertEquals("unit-user", user.get());
                assertTrue(output.toString(StandardCharsets.UTF_8).contains("\"ok\" : true"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        void localInvokeStreamsResponseLines() throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/invocations", exchange -> {
                byte[] response = "data: one\n\ndata: two\n".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            try {
                int exit = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli())).execute(
                        "invoke", "{}", "--mode", "local",
                        "--port", String.valueOf(server.getAddress().getPort()));
                assertEquals(0, exit);
                String text = output.toString(StandardCharsets.UTF_8);
                assertTrue(text.contains("data: one"));
                assertTrue(text.contains("data: two"));
            } finally {
                server.stop(0);
            }
        }

        @Test
        void unsafeLocalInvokeOptionsFailBeforeNetwork() {
            CommandLine cli = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli()));
            assertNotEquals(0, cli.execute(
                    "invoke", "{}", "--mode", "local", "--custom-path", "../admin"));
            assertNotEquals(0, cli.execute(
                    "invoke", "{}", "--mode", "local", "--timeout", "0"));
            assertNotEquals(0, cli.execute(
                    "invoke", "{}", "--mode", "local", "--port", "65536"));
        }

        @Test
        void runtimeExecPreservesQuotedArguments() throws Exception {
            AtomicReference<String> requestBody = new AtomicReference<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/runtimes/unit-agent/commands", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            try {
                setEnv("AGENTARTS_RUNTIME_DATA_ENDPOINT", "http://127.0.0.1:" + server.getAddress().getPort());
                int exit = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli())).execute(
                        "runtime", "exec-command", "printf 'hello world'",
                        "--agent", "unit-agent", "--endpoint", "named-endpoint",
                        "--bearer-token", "unit-token");
                assertEquals(0, exit);
                JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(requestBody.get());
                assertEquals("printf", body.path("command").get(0).asText());
                assertEquals("hello world", body.path("command").get(1).asText());
            } finally {
                server.stop(0);
                setEnv("AGENTARTS_RUNTIME_DATA_ENDPOINT", null);
            }
        }

        @Test
        void runtimeExecStreamsChunkedNdjsonAndSetsProtocolHeader() throws Exception {
            AtomicReference<String> commandType = new AtomicReference<>();
            AtomicReference<String> requestBody = new AtomicReference<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/runtimes/unit-agent/commands", exchange -> {
                commandType.set(exchange.getRequestHeaders().getFirst("Command-Type"));
                requestBody.set(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = ("{\"stdout\":\"one\"}\n"
                        + "{\"exit_code\":0}\n").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            try {
                setEnv("AGENTARTS_RUNTIME_DATA_ENDPOINT", "http://127.0.0.1:" + server.getAddress().getPort());
                int exit = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli())).execute(
                        "runtime", "exec-command", "echo one",
                        "--agent", "unit-agent", "--endpoint", "named-endpoint",
                        "--bearer-token", "unit-token", "--chunked", "--timeout", "5");
                assertEquals(0, exit);
                assertEquals("chunked", commandType.get());
                JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(requestBody.get());
                assertFalse(body.has("chunked"));
                String text = output.toString(StandardCharsets.UTF_8);
                assertTrue(text.contains("{\"stdout\":\"one\"}"));
                assertTrue(text.contains("{\"exit_code\":0}"));
            } finally {
                server.stop(0);
                setEnv("AGENTARTS_RUNTIME_DATA_ENDPOINT", null);
            }
        }

        @Test
        void runtimeDownloadPreservesBinaryBytesAndRequiresForceToReplace(
                @TempDir Path tempDir) throws Exception {
            byte[] response = new byte[] {0, 1, 2, (byte) 0x80, (byte) 0xff};
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/runtimes/unit-agent/download-files", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            Path outputPath = tempDir.resolve("nested/download.bin");
            setEnv("AGENTARTS_RUNTIME_DATA_ENDPOINT", "http://127.0.0.1:" + server.getAddress().getPort());
            CommandLine cli = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli()));
            try {
                int firstExit = cli.execute(
                        "runtime", "download-files", "--agent", "unit-agent",
                        "--session", "unit-session", "--path", "/remote/download.bin",
                        "--output", outputPath.toString(), "--endpoint", "named-endpoint",
                        "--bearer-token", "unit-token");
                assertEquals(0, firstExit);
                assertArrayEquals(response, Files.readAllBytes(outputPath));

                Files.write(outputPath, new byte[] {42});
                assertNotEquals(0, cli.execute(
                        "runtime", "download-files", "--agent", "unit-agent",
                        "--session", "unit-session", "--path", "/remote/download.bin",
                        "--output", outputPath.toString(), "--endpoint", "named-endpoint",
                        "--bearer-token", "unit-token"));
                assertArrayEquals(new byte[] {42}, Files.readAllBytes(outputPath));

                assertEquals(0, cli.execute(
                        "runtime", "download-files", "--agent", "unit-agent",
                        "--session", "unit-session", "--path", "/remote/download.bin",
                        "--output", outputPath.toString(), "--endpoint", "named-endpoint",
                        "--bearer-token", "unit-token", "--force"));
                assertArrayEquals(response, Files.readAllBytes(outputPath));
            } finally {
                server.stop(0);
                setEnv("AGENTARTS_RUNTIME_DATA_ENDPOINT", null);
            }
        }

        @Test
        void runtimeFileTransfersRejectInvalidTimeoutBeforeNetwork(@TempDir Path tempDir)
                throws IOException {
            Path input = tempDir.resolve("input.txt");
            Files.writeString(input, "content");
            CommandLine cli = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli()));

            assertNotEquals(0, cli.execute(
                    "runtime", "upload-files", "--agent", "unit-agent",
                    "--session", "unit-session", "--files", input.toString(),
                    "--timeout", "0", "--bearer-token", "unit-token"));
            assertNotEquals(0, cli.execute(
                    "runtime", "download-files", "--agent", "unit-agent",
                    "--session", "unit-session", "--path", "/remote/file",
                    "--timeout", "0", "--bearer-token", "unit-token"));
        }
    }

    // ============================================================
    // Init Operation E2E
    // ============================================================

    @Nested
    class InitOperationE2E {

        @Test
        void initCreatesFullProjectStructure(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("basic", "my-agent", tempDir.toString(),
                    "cn-southwest-2", "my-org", "my-repo");

            Path projectDir = tempDir.resolve("my-agent");
            assertTrue(Files.isDirectory(projectDir), "Project directory should exist");

            // Verify all expected files
            assertTrue(Files.isRegularFile(projectDir.resolve("pom.xml")), "pom.xml missing");
            assertTrue(Files.isRegularFile(projectDir.resolve(".agentarts_config.yaml")), "config.yaml missing");
            assertTrue(Files.isRegularFile(projectDir.resolve("Dockerfile")), "Dockerfile missing");
            assertTrue(Files.isRegularFile(
                    projectDir.resolve("src/main/java/com/example/Agent.java")), "Agent.java missing");
        }

        @Test
        void initPomXmlContainsCorrectValues(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("basic", "test-proj", tempDir.toString(),
                    "cn-north-4", null, null);

            String pom = Files.readString(tempDir.resolve("test-proj/pom.xml"));
            assertTrue(pom.contains("<artifactId>test-proj</artifactId>"),
                    "pom.xml should contain project name as artifactId");
        }

        @Test
        void initAgentJavaContainsCorrectClassName(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("basic", "demo", tempDir.toString(),
                    "cn-southwest-2", null, null);

            String agent = Files.readString(
                    tempDir.resolve("demo/src/main/java/com/example/Agent.java"));
            // The public class is named `Agent` to match the file name (Agent.java),
            // which is required for the generated project to compile.
            assertTrue(agent.contains("public class Agent"),
                    "Agent class should be named Agent to match its file");
            assertTrue(agent.contains("AgentArtsRuntimeApp"),
                    "Should reference AgentArtsRuntimeApp");
        }

        @Test
        void initConfigYamlContainsAllLayers(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("basic", "cfg-test", tempDir.toString(),
                    "cn-southwest-2", "org1", "repo1");

            String config = Files.readString(tempDir.resolve("cfg-test/.agentarts_config.yaml"));
            assertTrue(config.contains("base:"), "Missing base layer");
            assertTrue(config.contains("swr_config:"), "Missing swr_config layer");
            assertTrue(config.contains("runtime:"), "Missing runtime layer");
            assertTrue(config.contains("name: cfg-test"), "Missing project name");
            assertTrue(config.contains("region: cn-southwest-2"), "Missing region");
            assertTrue(config.contains("organization: org1"), "Missing SWR org");
            assertTrue(config.contains("repository: repo1"), "Missing SWR repo");
        }

        @Test
        void initConfigYamlHasJavaDefaults(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("basic", "java-test", tempDir.toString(),
                    "cn-southwest-2", null, null);

            String config = Files.readString(tempDir.resolve("java-test/.agentarts_config.yaml"));
            assertTrue(config.contains("language: java17"), "Missing language default");
            assertTrue(config.contains("base_image: eclipse-temurin:17-jre"), "Missing base_image default");
            assertTrue(config.contains("platform: linux/amd64"), "Missing platform default");
            assertTrue(config.contains("container_runtime: docker"), "Missing container_runtime default");
        }

        @Test
        void initDockerfileContainsCorrectImage(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("basic", "docker-test", tempDir.toString(),
                    "cn-southwest-2", null, null);

            String dockerfile = Files.readString(tempDir.resolve("docker-test/Dockerfile"));
            assertTrue(dockerfile.contains("eclipse-temurin:17-jre"), "Missing base image");
            assertTrue(dockerfile.contains("EXPOSE 8080"), "Missing expose");
        }

        @Test
        void defaultAgentNameMatchesPythonFormat() {
            // Mirrors the Python SDK's generate_default_agent_name():
            // "myagent-" + 5 random lowercase letters/digits.
            String name = InitOperation.generateDefaultAgentName();
            assertTrue(name.matches("myagent-[a-z0-9]{5}"),
                    "default name should be myagent-<5 random alnum chars>, got: " + name);
        }

        @Test
        void initSwrDefaultsFollowPythonSdkRules(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("basic", "default-swr", tempDir.toString(),
                    "cn-southwest-2", null, null);

            String config = Files.readString(tempDir.resolve("default-swr/.agentarts_config.yaml"));
            // swr_repo defaults to "agent_<name>" (Python: swr_repo or f"agent_{name}")
            assertTrue(config.contains("repository: agent_default-swr"),
                    "SWR repo should default to agent_<name>. config=" + config);
            // swr_org defaults to "agentarts-<region_short>-<ak_hash8>-org"
            // (Python: generate_swr_org_name(region)); the ak hash is md5(ak)[:8],
            // or 8 random chars when no AK is configured, so match the shape.
            assertTrue(config.lines().anyMatch(line -> line.trim().matches(
                            "organization: agentarts-cnso2-[a-z0-9]{8}-org")),
                    "SWR org should follow agentarts-<region>-<hash>-org pattern. config=" + config);
        }
    }

    // ============================================================
    // Config Operation E2E (picocli CLI path — mirrors
    // tests/integration/toolkit/test_cli_local.py config tests)
    // ============================================================

    @Nested
    class ConfigOperationE2E {

        @TempDir
        Path tempDir;

        private Path configFile;
        private CommandLine cli;
        private ByteArrayOutputStream outBuf;
        private ByteArrayOutputStream errBuf;
        private PrintStream origOut;
        private PrintStream origErr;

        @BeforeEach
        void setUp() {
            configFile = tempDir.resolve(".agentarts_config.yaml");
            // Redirect ConfigOperation's config file to the temp dir — the Java
            // analog of the Python tests' monkeypatch.chdir(tmp_project).
            ConfigOperation.setConfigFileOverride(configFile.toFile());
            cli = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli()));
            // ConfigOperation writes through System.out/err (not picocli's
            // PrintWriter), so capture the System streams directly.
            outBuf = new ByteArrayOutputStream();
            errBuf = new ByteArrayOutputStream();
            origOut = System.out;
            origErr = System.err;
            System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        }

        @AfterEach
        void tearDown() {
            ConfigOperation.clearConfigFileOverride();
            System.setOut(origOut);
            System.setErr(origErr);
        }

        private String stdout() {
            return outBuf.toString(StandardCharsets.UTF_8);
        }

        private String readConfig() {
            try {
                return Files.readString(configFile);
            } catch (IOException e) {
                fail("Failed to read config file: " + e.getMessage());
                return "";
            }
        }

        /** Mirrors {@code test_config_add_writes_yaml_and_lists}: the
         *  {@code config -n <name> -e ... -r ...} callback path adds an agent,
         *  writes {@code .agentarts_config.yaml}, and {@code config list} exits 0. */
        @Test
        void configAddWritesYamlAndLists() {
            int exit = cli.execute("config",
                    "-n", "myagent",
                    "-e", "com.example.MyAgent",
                    "-r", "cn-southwest-2",
                    "-d", "pom.xml",
                    "--swr-org", "o",
                    "--swr-repo", "r");
            assertEquals(0, exit, "config -n should add an agent. err=" + errBuf);
            assertTrue(Files.exists(configFile), "config file should be created");
            String yaml = readConfig();
            assertTrue(yaml.contains("myagent"), "config should contain the agent name");

            outBuf.reset();
            int listExit = cli.execute("config", "list");
            assertEquals(0, listExit, "config list should exit 0. err=" + errBuf);
            assertTrue(stdout().contains("myagent"), "config list should list the agent");
        }

        /** Mirrors {@code test_config_set_get_roundtrip}: {@code config set
         *  base.description hello} persists to YAML and {@code config get
         *  base.description} prints the value. */
        @Test
        void configSetGetRoundtrip() {
            assertEquals(0, cli.execute("config", "-n", "myagent",
                    "-e", "com.example.MyAgent", "-r", "cn-southwest-2"),
                    "add agent should exit 0. err=" + errBuf);

            int setExit = cli.execute("config", "set", "base.description", "hello",
                    "-a", "myagent");
            assertEquals(0, setExit, "config set should exit 0. err=" + errBuf);
            assertTrue(readConfig().contains("hello"),
                    "config file should contain the set value");

            outBuf.reset();
            int getExit = cli.execute("config", "get", "base.description", "-a", "myagent");
            assertEquals(0, getExit, "config get should exit 0. err=" + errBuf);
            assertTrue(stdout().contains("hello"),
                    "config get should print the persisted value");
        }

        /** Mirrors {@code test_config_env_lifecycle}: set-env writes the var,
         *  list-env exits 0, remove-env removes it from the YAML. */
        @Test
        void configEnvLifecycle() {
            cli.execute("config", "-n", "myagent", "-e", "com.example.MyAgent",
                    "-r", "cn-southwest-2");

            assertEquals(0, cli.execute("config", "set-env", "MY_VAR", "val", "-a", "myagent"),
                    "set-env should exit 0. err=" + errBuf);
            String yaml = readConfig();
            assertTrue(yaml.contains("MY_VAR"), "config should contain the env var name");
            assertTrue(yaml.contains("val"), "config should contain the env var value");

            outBuf.reset();
            assertEquals(0, cli.execute("config", "list-env", "-a", "myagent"),
                    "list-env should exit 0. err=" + errBuf);

            assertEquals(0, cli.execute("config", "remove-env", "MY_VAR", "-a", "myagent"),
                    "remove-env should exit 0. err=" + errBuf);
            assertFalse(readConfig().contains("MY_VAR"),
                    "config should no longer contain the removed env var");
        }

        @Test
        void configCommandsNeverEchoCredentialValues() {
            String credentialMarker = "unit-sensitive-value";
            cli.execute("config", "-n", "myagent", "-e", "com.example.MyAgent");
            outBuf.reset();

            assertEquals(0, cli.execute(
                    "config", "set-env", "SERVICE_API_KEY", credentialMarker,
                    "-a", "myagent"));
            assertFalse(stdout().contains(credentialMarker));
            assertTrue(stdout().contains("[REDACTED]"));

            outBuf.reset();
            assertEquals(0, cli.execute("config", "list-env", "-a", "myagent"));
            assertFalse(stdout().contains(credentialMarker));
            assertTrue(stdout().contains("SERVICE_API_KEY=[REDACTED]"));

            outBuf.reset();
            assertEquals(0, cli.execute(
                    "config", "get", "runtime.environment_variables.SERVICE_API_KEY",
                    "-a", "myagent"));
            assertEquals("[REDACTED]", stdout().trim());

            outBuf.reset();
            assertEquals(0, cli.execute("config", "get", "-a", "myagent"));
            assertFalse(stdout().contains(credentialMarker));
        }

        @Test
        void sensitiveConfigValuesCanBeReadFromStdin() {
            String marker = "unit-piped-sensitive-value";
            assertEquals(0, cli.execute("config", "-n", "myagent"));
            outBuf.reset();
            InputStream originalInput = System.in;
            try {
                System.setIn(new ByteArrayInputStream(
                        (marker + "\n").getBytes(StandardCharsets.UTF_8)));
                assertEquals(0, cli.execute(
                        "config", "set-env", "SERVICE_TOKEN", "--agent", "myagent"));
            } finally {
                System.setIn(originalInput);
            }

            assertFalse(stdout().contains(marker));
            assertTrue(stdout().contains("[REDACTED]"));
            assertTrue(readConfig().contains(marker), "the piped value must be persisted exactly");
        }

        /** Mirrors {@code test_config_set_default_and_remove}: adding two agents,
         *  setting the default, and removing one leaves the survivor in the YAML. */
        @Test
        void configSetDefaultAndRemove() {
            cli.execute("config", "-n", "a1", "-e", "com.example.A1Agent",
                    "-r", "cn-southwest-2");
            cli.execute("config", "-n", "a2", "-e", "com.example.A2Agent",
                    "-r", "cn-southwest-2");

            assertEquals(0, cli.execute("config", "set-default", "a2"),
                    "set-default should exit 0. err=" + errBuf);
            assertEquals(0, cli.execute("config", "remove", "a1"),
                    "remove should exit 0. err=" + errBuf);

            String remaining = readConfig();
            // Assert by agent map key (indented "  a2:" line), not bare substring:
            // Java's default `language: java17` contains the substring "a1", so a
            // naive `contains("a1")` would false-positive (Python avoids this only
            // because its language default is `python3`).
            assertTrue(remaining.contains("\n  a2:"), "remaining config should still contain agent a2. yaml=" + remaining);
            assertFalse(remaining.contains("\n  a1:"), "remaining config should not contain agent a1. yaml=" + remaining);
            // set-default must actually have written default_agent — not just exited 0.
            // Jackson quotes the YAML scalar, so accept both `default_agent: a2` and
            // `default_agent: "a2"`, anchored to a whole line so "a2foo" can't sneak in.
            assertTrue(remaining.lines()
                            .anyMatch(l -> l.matches("^default_agent:\\s*\"?a2\"?\\s*$")),
                    "set-default a2 should have set default_agent to a2. yaml=" + remaining);
        }

        /** Verifies config persists across CLI-driven add/list/get cycles. */
        @Test
        void configPersistenceAcrossLoads() {
            cli.execute("config", "-n", "agent-0", "-e", "com.example.Agent0",
                    "-r", "cn-southwest-2");
            cli.execute("config", "-n", "agent-1", "-e", "com.example.Agent1",
                    "-r", "cn-north-4");
            cli.execute("config", "-n", "agent-2", "-e", "com.example.Agent2",
                    "-r", "cn-east-3");

            outBuf.reset();
            cli.execute("config", "list");
            String listing = stdout();
            assertTrue(listing.contains("agent-0"), "list should include agent-0");
            assertTrue(listing.contains("agent-1"), "list should include agent-1");
            assertTrue(listing.contains("agent-2"), "list should include agent-2");

            outBuf.reset();
            cli.execute("config", "get", "base.region", "-a", "agent-1");
            assertTrue(stdout().contains("cn-north-4"),
                    "config get base.region should return the persisted region");
        }

        @Test
        void malformedConfigFailsClosedWithoutOverwritingFile() throws IOException {
            String malformed = "agents: [unterminated";
            Files.writeString(configFile, malformed, StandardCharsets.UTF_8);

            int exit = cli.execute("config", "list");

            assertNotEquals(0, exit, "malformed configuration must fail the command");
            assertEquals(malformed, Files.readString(configFile, StandardCharsets.UTF_8),
                    "a failed read must never replace the user's configuration");
            try (DirectoryStream<Path> leftovers = Files.newDirectoryStream(
                    tempDir, ".agentarts-config-*.tmp")) {
                assertFalse(leftovers.iterator().hasNext(),
                        "atomic configuration writes must not leave temporary files behind");
            }
        }

        @Test
        void missingConfigTargetsReturnNonZero() {
            assertNotEquals(0, cli.execute("config", "set-default", "missing"));
            assertNotEquals(0, cli.execute(
                    "config", "get", "base.region", "--agent", "missing"));
            assertNotEquals(0, cli.execute(
                    "config", "set", "base.description", "value", "--agent", "missing"));
            assertNotEquals(0, cli.execute("config", "remove", "missing"));
            assertNotEquals(0, cli.execute(
                    "config", "remove-env", "MISSING", "--agent", "missing"));
        }

        @Test
        void renamingAgentUpdatesMapKeyAndDefaultSelector() {
            assertEquals(0, cli.execute("config", "-n", "old-name"));
            assertEquals(0, cli.execute(
                    "config", "set", "base.name", "new-name", "--agent", "old-name"));

            String yaml = readConfig();
            assertTrue(yaml.contains("\n  new-name:"));
            assertFalse(yaml.contains("\n  old-name:"));
            assertTrue(yaml.lines().anyMatch(
                    line -> line.matches("^default_agent:\\s*\"?new-name\"?\\s*$")));
            assertEquals(0, cli.execute(
                    "config", "get", "base.name", "--agent", "new-name"));
        }
    }

    // ============================================================
    // CLI Command Execution E2E (via Picocli)
    // ============================================================

    @Nested
    class CliExecutionE2E {

        private CommandLine cli;

        @BeforeEach
        void setUp() {
            cli = new CommandLine(new AgentArtsCli());
        }

        @Test
        void helpListsAllTopLevelCommands() {
            StringWriter sw = new StringWriter();
            cli.setOut(new PrintWriter(sw));
            cli.execute("--help");
            String output = sw.toString();

            // All 9 commands should appear in help
            assertTrue(output.contains("init"), "Missing 'init' in help");
            assertTrue(output.contains("config"), "Missing 'config' in help");
            assertTrue(output.contains("dev"), "Missing 'dev' in help");
            assertTrue(output.contains("deploy"), "Missing 'deploy' in help");
            assertTrue(output.contains("invoke"), "Missing 'invoke' in help");
            assertTrue(output.contains("destroy"), "Missing 'destroy' in help");
            assertTrue(output.contains("runtime"), "Missing 'runtime' in help");
            assertTrue(output.contains("gateway"), "Missing 'gateway' in help");
            assertTrue(output.contains("memory"), "Missing 'memory' in help");
        }

        @Test
        void initHelpShowsOptions() {
            // Use usage() directly to get subcommand help text
            StringWriter sw = new StringWriter();
            cli.getSubcommands().get("init").usage(new PrintWriter(sw));
            String output = sw.toString();

            assertTrue(output.contains("--name") || output.contains("-n"), "Missing --name option");
            assertTrue(output.contains("--template") || output.contains("-t"), "Missing --template option");
            assertTrue(output.contains("--path") || output.contains("-p"), "Missing --path option");
            assertTrue(output.contains("--region") || output.contains("-r"), "Missing --region option");
        }

        @Test
        void configHelpShowsSubcommands() {
            StringWriter sw = new StringWriter();
            cli.getSubcommands().get("config").usage(new PrintWriter(sw));
            String output = sw.toString();

            assertTrue(output.contains("list"), "Missing 'list' subcommand");
            assertTrue(output.contains("set-default"), "Missing 'set-default' subcommand");
            assertTrue(output.contains("get"), "Missing 'get' subcommand");
            assertTrue(output.contains("set"), "Missing 'set' subcommand");
            assertTrue(output.contains("remove"), "Missing 'remove' subcommand");
            assertTrue(output.contains("set-env"), "Missing 'set-env' subcommand");
            assertTrue(output.contains("remove-env"), "Missing 'remove-env' subcommand");
            assertTrue(output.contains("list-env"), "Missing 'list-env' subcommand");
        }

        @Test
        void runtimeHelpShowsSubcommands() {
            StringWriter sw = new StringWriter();
            cli.getSubcommands().get("runtime").usage(new PrintWriter(sw));
            String output = sw.toString();

            assertTrue(output.contains("invoke"), "Missing 'invoke' subcommand");
            assertTrue(output.contains("exec-command"), "Missing 'exec-command' subcommand");
            assertTrue(output.contains("upload-files"), "Missing 'upload-files' subcommand");
            assertTrue(output.contains("download-files"), "Missing 'download-files' subcommand");
            assertTrue(output.contains("start-session"), "Missing 'start-session' subcommand");
            assertTrue(output.contains("stop-session"), "Missing 'stop-session' subcommand");
        }

        @Test
        void gatewayHelpShowsSubcommands() {
            StringWriter sw = new StringWriter();
            cli.getSubcommands().get("gateway").usage(new PrintWriter(sw));
            String output = sw.toString();

            assertTrue(output.contains("create-gateway"));
            assertTrue(output.contains("update-gateway"));
            assertTrue(output.contains("delete-gateway"));
            assertTrue(output.contains("get-gateway"));
            assertTrue(output.contains("list-gateways"));
            assertTrue(output.contains("create-gateway-target"));
            assertTrue(output.contains("update-gateway-target"));
            assertTrue(output.contains("delete-gateway-target"));
            assertTrue(output.contains("get-gateway-target"));
            assertTrue(output.contains("list-gateway-targets"));
        }

        @Test
        void memoryHelpShowsSubcommands() {
            StringWriter sw = new StringWriter();
            cli.getSubcommands().get("memory").usage(new PrintWriter(sw));
            String output = sw.toString();

            assertTrue(output.contains("create"));
            assertTrue(output.contains("get"));
            assertTrue(output.contains("list"));
            assertTrue(output.contains("update"));
            assertTrue(output.contains("delete"));
            assertTrue(output.contains("status"));
        }

        @Test
        void unknownCommandReturnsNonZero() {
            StringWriter sw = new StringWriter();
            StringWriter errSw = new StringWriter();
            cli.setOut(new PrintWriter(sw));
            cli.setErr(new PrintWriter(errSw));
            int exitCode = cli.execute("nonexistent-command");
            assertNotEquals(0, exitCode);
        }
    }

    // ============================================================
    // Template rendering E2E
    // ============================================================

    @Nested
    class TemplateRenderingE2E {

        @Test
        void allTemplatesLoadWithoutError() throws Exception {
            // loadTemplate throws on a missing template, so a clean return
            // through these calls IS the check that every template is present.
            TemplateManager.loadTemplate("basic/Agent.java.tpl");
            TemplateManager.loadTemplate("agentscope/Agent.java.tpl");
            TemplateManager.loadTemplate("agentscope/pom.xml.tpl");
            TemplateManager.loadTemplate("basic/pom.xml.tpl");
            TemplateManager.loadTemplate("basic/config.yaml.tpl");
            TemplateManager.loadTemplate("docker/Dockerfile.tpl");
        }

        @Test
        void basicAgentTemplateSubstitutesCorrectly(@TempDir Path tempDir) throws Exception {
            Path output = tempDir.resolve("Agent.java");
            TemplateManager.renderToFile("basic/Agent.java.tpl", output, "name", "weather");

            String content = Files.readString(output);
            // The public class is `Agent` (matches the file name); the project name is
            // substituted into the welcome/invoke message.
            assertTrue(content.contains("public class Agent"),
                    "Class should be named Agent to match its file");
            assertFalse(content.contains("{{ name }}"), "Placeholder should be replaced");
            assertFalse(content.contains("{{name}}"), "Placeholder should be replaced");
        }

        @Test
        void agentscopeTemplateUsesProductionRuntimeContract(@TempDir Path tempDir) throws Exception {
            Path output = tempDir.resolve("Agent.java");
            TemplateManager.renderToFile("agentscope/Agent.java.tpl", output, "name", "weather");

            String content = Files.readString(output);
            assertTrue(content.contains("public class Agent"));
            assertTrue(content.contains("AgentscopeSessionExecutor"));
            assertTrue(content.contains("runUntilShutdown"));
            assertTrue(content.contains("registerManagedResource"));
            assertFalse(content.contains("TODO"));
            assertFalse(content.contains("{{ name }}"));
        }

        @Test
        void configTemplateSubstitutesAllVars(@TempDir Path tempDir) throws Exception {
            Path output = tempDir.resolve("config.yaml");
            TemplateManager.renderToFile("basic/config.yaml.tpl", output,
                    "name", "my-proj", "region", "cn-north-4",
                    "swr_org", "my-org", "swr_repo", "my-repo");

            String content = Files.readString(output);
            assertTrue(content.contains("name: my-proj"), "Name not substituted");
            assertTrue(content.contains("region: cn-north-4"), "Region not substituted");
            assertTrue(content.contains("organization: my-org"), "SWR org not substituted");
            assertTrue(content.contains("repository: my-repo"), "SWR repo not substituted");
            assertFalse(content.contains("{{ "), "Unresolved placeholders remain");
        }

        @Test
        void pomTemplateSubstitutesName(@TempDir Path tempDir) throws Exception {
            Path output = tempDir.resolve("pom.xml");
            TemplateManager.renderToFile("basic/pom.xml.tpl", output,
                    "name", "cool-project", "region", "cn-southwest-2");

            String content = Files.readString(output);
            assertTrue(content.contains("<artifactId>cool-project</artifactId>"),
                    "Name should be in artifactId");
        }

        @Test
        void dockerfileTemplateSubstitutesName(@TempDir Path tempDir) throws Exception {
            Path output = tempDir.resolve("Dockerfile");
            TemplateManager.renderToFile("docker/Dockerfile.tpl", output, "name", "svc");

            String content = Files.readString(output);
            assertTrue(content.contains("COPY target/svc.jar app.jar"),
                    "Name should be substituted into the COPY line");
            assertTrue(content.contains("eclipse-temurin:17-jre"), "Base image should be present");
        }

        @Test
        void missingTemplateThrowsIOException() {
            assertThrows(IOException.class, () ->
                    TemplateManager.loadTemplate("nonexistent/template.tpl"));
        }

        @Test
        void renderWithEmptyMapReturnsOriginal() {
            String template = "Hello {{ name }}";
            String result = TemplateManager.render(template, java.util.Map.of());
            assertEquals("Hello {{ name }}", result);
        }

        @Test
        void renderHandlesBothSpacingFormats() {
            String template = "{{ spaced }} and {{nospace}}";
            String result = TemplateManager.render(template,
                    java.util.Map.of("spaced", "A", "nospace", "B"));
            assertEquals("A and B", result);
        }
    }

    // ============================================================
    // Full workflow E2E: init → verify generated config
    // ============================================================

    @Nested
    class FullWorkflowE2E {

        @Test
        void initGeneratesBuildableProjectStructure(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("basic", "buildable", tempDir.toString(),
                    "cn-southwest-2", null, null);

            Path projectDir = tempDir.resolve("buildable");

            // Verify the generated Java file references the runtime SDK and the
            // substituted project name (specific identifiers — not generic Java
            // keywords that any .java file would contain).
            String agentJava = Files.readString(projectDir.resolve("src/main/java/com/example/Agent.java"));
            assertTrue(agentJava.contains("public class Agent"), "Should declare public class Agent");
            assertTrue(agentJava.contains("AgentArtsRuntimeApp"),
                    "Should reference AgentArtsRuntimeApp from the runtime SDK");
            assertTrue(agentJava.contains("buildable"),
                    "Project name should be substituted into the generated source");

            // Verify pom.xml is valid XML structure
            String pomXml = Files.readString(projectDir.resolve("pom.xml"));
            assertTrue(pomXml.contains("<?xml"), "Should be XML");
            assertTrue(pomXml.contains("<project"), "Should have project root element");
            assertTrue(pomXml.contains("<dependencies>"), "Should have dependencies");
            assertTrue(pomXml.contains("agentarts-sdk-runtime"), "Should depend on runtime SDK");
        }

        @Test
        void generatedAgentscopeSourceCompiles(@TempDir Path tempDir) throws Exception {
            InitOperation.initProject("agentscope", "compiled-agent", tempDir.toString(),
                    "cn-southwest-2", null, null);
            Path source = tempDir.resolve(
                    "compiled-agent/src/main/java/com/example/Agent.java");
            Path classes = tempDir.resolve("compiled-agent/target/test-classes");
            Files.createDirectories(classes);
            javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
            assertNotNull(compiler, "Template compilation requires a JDK");
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

            StringBuilder classpathBuilder = new StringBuilder();
            classpathBuilder.append(System.getProperty("java.class.path"));
            // 1. 获取基础类路径
            String baseClasspath = System.getProperty("java.class.path");
            classpathBuilder.append(baseClasspath);

            // 2. 兼容 Maven Surefire/Failsafe 插件在测试时生成的特定类路径属性
            String surefireClasspath = System.getProperty("surefire.test.class.path");
            if (surefireClasspath != null && !surefireClasspath.isEmpty()) {
                classpathBuilder.append(java.io.File.pathSeparator).append(surefireClasspath);
            }

            // 3. 兜底逻辑：如果在 IDE 中运行，AppClassLoader 的 ClassPath 实际已包含在 java.class.path 中
            // 但如果是在复杂的多模块 Maven 命令行中，需要把当前类所在目录也加进去
            java.net.URL currentClassLoc = this.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (currentClassLoc != null) {
                classpathBuilder.append(java.io.File.pathSeparator).append(currentClassLoc.getPath());
            }
            System.out.println(classpathBuilder.toString());

            int exitCode = compiler.run(null, output, output,
                    "-proc:none",
                    "--release", "17",
                    "-classpath", classpathBuilder.toString(),
                    "-d", classes.toString(),
                    source.toString());

            assertEquals(0, exitCode,
                    () -> "Generated AgentScope source did not compile:\n" + output);
        }

        @Test
        void multipleInitDifferentTemplates(@TempDir Path tempDir) throws Exception {
            // Init basic template
            InitOperation.initProject("basic", "proj-basic", tempDir.toString(),
                    "cn-southwest-2", null, null);

            // Init agentscope template
            InitOperation.initProject("agentscope", "proj-agentscope", tempDir.toString(),
                    "cn-southwest-2", null, null);

            // Both should exist independently
            assertTrue(Files.isDirectory(tempDir.resolve("proj-basic")));
            assertTrue(Files.isDirectory(tempDir.resolve("proj-agentscope")));

            // Each should have its own files
            assertTrue(Files.exists(tempDir.resolve("proj-basic/pom.xml")));
            assertTrue(Files.exists(tempDir.resolve("proj-agentscope/pom.xml")));

            // Agent files should differ
            String basicAgent = Files.readString(
                    tempDir.resolve("proj-basic/src/main/java/com/example/Agent.java"));
            String asAgent = Files.readString(
                    tempDir.resolve("proj-agentscope/src/main/java/com/example/Agent.java"));
            assertNotEquals(basicAgent, asAgent, "Different templates should produce different output");
            String agentscopePom = Files.readString(tempDir.resolve("proj-agentscope/pom.xml"));
            assertTrue(agentscopePom.contains("agentarts-sdk-integration-agentscope"));
            assertTrue(agentscopePom.contains("agentscope-harness"));
            assertFalse(agentscopePom.contains("agentarts-sdk-runtime</artifactId>"));
        }
    }

    // ============================================================
    // Input validation E2E
    // ============================================================

    @Nested
    class InputValidationE2E {

        @Test
        void initRejectsUnsupportedTemplatesAndExistingProjects(@TempDir Path tempDir)
                throws Exception {
            assertThrows(IllegalArgumentException.class,
                    () -> InitOperation.initProject(
                            "../basic", "safe", tempDir.toString(),
                            "cn-southwest-2", null, null));
            InitOperation.initProject("basic", "safe", tempDir.toString(),
                    "cn-southwest-2", null, null);
            assertThrows(java.nio.file.FileAlreadyExistsException.class,
                    () -> InitOperation.initProject(
                            "basic", "safe", tempDir.toString(),
                            "cn-southwest-2", null, null));
            try (var entries = Files.list(tempDir)) {
                assertEquals(1, entries
                        .filter(path -> path.getFileName().toString().startsWith("safe"))
                        .count());
            }
        }

        @Test
        void initPrintsErrorWhenNameMissing(@TempDir Path tempDir) throws Exception {
            // InitCommand returns a non-zero exit code on validation failure
            // (missing --name), and must not create any project directory.
            CommandLine cli = new CommandLine(new AgentArtsCli());
            int exitCode = cli.execute("init", "--path", tempDir.toString());
            assertNotEquals(0, exitCode, "init with missing --name should exit non-zero");
            // No project directory should be created since --name was not provided
            assertEquals(0, Files.list(tempDir).count(),
                    "no files/dirs should be created when --name is missing");
        }

        @Test
        void initLowercasesNameBeforeValidation(@TempDir Path tempDir) throws Exception {
            // InitCommand lowercases the name before regex validation
            CommandLine cli = new CommandLine(new AgentArtsCli());
            cli.execute("init", "--name", "MyProject", "--path", tempDir.toString());

            // The name should be lowercased to "myproject"
            assertTrue(Files.isDirectory(tempDir.resolve("myproject")),
                    "Project should be created with lowercased name");
        }

        @Test
        void initCreatesProjectWithDefaults(@TempDir Path tempDir) throws Exception {
            CommandLine cli = new CommandLine(new AgentArtsCli());
            cli.execute("init", "--name", "test-proj", "--path", tempDir.toString());

            Path projDir = tempDir.resolve("test-proj");
            assertTrue(Files.isDirectory(projDir));
            assertTrue(Files.exists(projDir.resolve("pom.xml")));
            assertTrue(Files.exists(projDir.resolve("Dockerfile")));
            assertTrue(Files.exists(projDir.resolve(".agentarts_config.yaml")));
        }
    }
}
