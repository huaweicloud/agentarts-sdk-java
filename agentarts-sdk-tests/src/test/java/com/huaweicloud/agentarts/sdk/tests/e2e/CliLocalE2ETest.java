package com.huaweicloud.agentarts.sdk.tests.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.toolkit.AgentArtsCli;
import com.huaweicloud.agentarts.toolkit.operations.ConfigOperation;
import com.huaweicloud.agentarts.toolkit.operations.DevOperation;
import com.huaweicloud.agentarts.toolkit.operations.InitOperation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Local CLI e2e tests — mirrors {@code tests/integration/toolkit/test_cli_local.py}.
 *
 * <p>Default tier — no credentials, no Docker, no cloud. Invokes the picocli
 * {@link AgentArtsCli} in-process for {@code init} (asserting on generated
 * files and non-zero exit on validation failure) and drives the {@code dev}
 * subcommand (backed by {@link com.huaweicloud.agentarts.toolkit.operations.DevOperation})
 * to assert {@code /ping} and {@code POST /invocations} succeed.</p>
 */
@Tag("e2e")
@DisplayName("CLI Local E2E Tests")
class CliLocalE2ETest {

    private CommandLine cli;

    @BeforeEach
    void setUp() {
        cli = new CommandLine(new AgentArtsCli());
        cli.setOut(new PrintWriter(new StringWriter()));
        cli.setErr(new PrintWriter(new StringWriter()));
    }

    // ---------------------------------------------------------------
    // Smoke (mirrors test_cli_version / test_cli_help)
    // ---------------------------------------------------------------

    /** Mirrors {@code test_cli_version}: {@code --version} exits 0 and the
     *  output mentions {@code agentarts} or a {@code 0.} version string. */
    @Test
    @DisplayName("CLI --version exits 0 and prints the version")
    void test_cli_version() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine versionCli = new CommandLine(new AgentArtsCli());
        versionCli.setOut(new PrintWriter(out, true));
        versionCli.setErr(new PrintWriter(err, true));
        int exitCode = versionCli.execute("--version");
        String combined = out.toString() + err.toString();
        assertEquals(0, exitCode, "--version should exit 0; out=" + combined);
        assertTrue(combined.toLowerCase().contains("agentarts") || combined.contains("0."),
                "--version output should mention 'agentarts' or a version, got: " + combined);
    }

    /** Mirrors {@code test_cli_help}: {@code --help} exits 0. */
    @Test
    @DisplayName("CLI --help exits 0")
    void test_cli_help() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine helpCli = new CommandLine(new AgentArtsCli());
        helpCli.setOut(new PrintWriter(out, true));
        helpCli.setErr(new PrintWriter(err, true));
        int exitCode = helpCli.execute("--help");
        assertEquals(0, exitCode, "--help should exit 0; out=" + out + err);
    }

    // ---------------------------------------------------------------
    // init
    // ---------------------------------------------------------------

    /** Mirrors {@code test_init_creates_project_files} (basic template). The
     *  Java toolkit only ships {@code basic}/{@code agentscope} templates, so
     *  only {@code basic} is asserted here; the unsupported-template gap is
     *  covered by the dedicated tests below. */
    @Test
    @DisplayName("CLI init creates the expected project files (basic template)")
    void test_init_creates_project_files(@TempDir Path tmp) throws Exception {
        int exitCode = cli.execute("init",
                "--name", "myagent",
                "--template", "basic",
                "--region", "cn-southwest-2",
                "--path", tmp.toString());
        assertEquals(0, exitCode, "init should exit 0");
        Path project = tmp.resolve("myagent");
        assertTrue(Files.isRegularFile(project.resolve("pom.xml")), "pom.xml missing");
        assertTrue(Files.isRegularFile(project.resolve(".agentarts_config.yaml")),
                ".agentarts_config.yaml missing");
        assertTrue(Files.isRegularFile(project.resolve("Dockerfile")), "Dockerfile missing");
        assertTrue(Files.isRegularFile(
                project.resolve("src/main/java/com/example/Agent.java")), "Agent.java missing");
    }

    /** Mirrors {@code test_init_path_option}: {@code init --path <target>}
     *  creates the project in the target dir (not cwd). */
    @Test
    @DisplayName("CLI init --path creates project in the target directory")
    void test_init_path_option(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("sub");
        Files.createDirectories(target);

        int exitCode = cli.execute("init",
                "--name", "myagent",
                "--template", "basic",
                "--region", "cn-southwest-2",
                "--path", target.toString());
        assertEquals(0, exitCode, "CLI init should exit 0");

        Path project = target.resolve("myagent");
        assertTrue(Files.isDirectory(project), "project directory should exist");
        assertTrue(Files.isRegularFile(project.resolve("pom.xml")), "pom.xml missing");
        assertTrue(Files.isRegularFile(project.resolve(".agentarts_config.yaml")),
                ".agentarts_config.yaml missing");
        assertTrue(Files.isRegularFile(project.resolve("Dockerfile")), "Dockerfile missing");
        assertTrue(Files.isRegularFile(
                project.resolve("src/main/java/com/example/Agent.java")), "Agent.java missing");
    }

    /** Mirrors {@code test_init_invalid_name_fails}: a name with invalid chars
     *  must exit non-zero and not create a project. The Java {@code InitCommand}
     *  lowercases the name then validates against {@code [a-z0-9-]+}. */
    @Test
    @DisplayName("CLI init with invalid name does not create a project")
    void test_init_invalid_name_fails(@TempDir Path tmp) {
        int exitCode = cli.execute("init",
                "--name", "Bad_Name!",
                "--template", "basic",
                "--region", "cn-southwest-2",
                "--path", tmp.toString());
        // InitCommand now returns a non-zero exit code on validation failure.
        assertNotEquals(0, exitCode, "init with an invalid name should exit non-zero");
        assertFalse(Files.isDirectory(tmp.resolve("Bad_Name!")),
                "invalid-name project should not be created");
        // The lowercased+validated form must also be absent
        assertFalse(Files.isDirectory(tmp.resolve("bad_name!")),
                "invalid-name project (lowercased) should not be created");
    }

    // ---------------------------------------------------------------
    // config (mirrors test_config_*)
    // ---------------------------------------------------------------

    /** Redirect {@link ConfigOperation}'s config file to a temp path for the
     *  duration of one test (the Java analog of the Python tests'
     *  {@code monkeypatch.chdir(tmp_project)}). picocli executes synchronously
     *  on the calling thread, so the thread-local override is visible to the
     *  {@code config} subcommand's {@link ConfigOperation} calls. */
    private void withConfigOverride(Path tmp, Runnable body) {
        File cfg = tmp.resolve(".agentarts_config.yaml").toFile();
        ConfigOperation.setConfigFileOverride(cfg);
        try {
            body.run();
        } finally {
            ConfigOperation.clearConfigFileOverride();
        }
    }

    /** Mirrors {@code test_config_add_writes_yaml_and_lists}: {@code config -n}
     *  writes {@code .agentarts_config.yaml} containing the agent name, and
     *  {@code config list} exits 0. */
    @Test
    @DisplayName("config add writes YAML and config list exits 0")
    void test_config_add_writes_yaml_and_lists(@TempDir Path tmp) {
        withConfigOverride(tmp, () -> {
            int add = cli.execute("config",
                    "-n", "myagent",
                    "-e", "com.example.MyAgent",
                    "-r", "cn-southwest-2",
                    "-d", "pom.xml",
                    "--swr-org", "o",
                    "--swr-repo", "r");
            assertEquals(0, add, "config add should exit 0");
            Path cfg = tmp.resolve(".agentarts_config.yaml");
            assertTrue(Files.exists(cfg), ".agentarts_config.yaml should be created");
            String yaml = readString(cfg);
            assertTrue(yaml.contains("myagent"), "YAML should contain the agent name; got:\n" + yaml);
            int list = cli.execute("config", "list");
            assertEquals(0, list, "config list should exit 0");
        });
    }

    /** Mirrors {@code test_config_set_get_roundtrip}: {@code config set} writes
     *  the value into the YAML and {@code config get} exits 0. */
    @Test
    @DisplayName("config set/get round-trips a dotted value")
    void test_config_set_get_roundtrip(@TempDir Path tmp) {
        withConfigOverride(tmp, () -> {
            assertEquals(0, cli.execute("config",
                    "-n", "myagent", "-e", "com.example.MyAgent",
                    "-r", "cn-southwest-2", "-d", "pom.xml",
                    "--swr-org", "o", "--swr-repo", "r"),
                    "config add should exit 0");
            int set = cli.execute("config", "set", "base.description", "hello", "-a", "myagent");
            assertEquals(0, set, "config set should exit 0");
            String yaml = readString(tmp.resolve(".agentarts_config.yaml"));
            assertTrue(yaml.contains("hello"), "YAML should contain the set value; got:\n" + yaml);
            int get = cli.execute("config", "get", "base.description", "-a", "myagent");
            assertEquals(0, get, "config get should exit 0");
        });
    }

    /** Mirrors {@code test_config_env_lifecycle}: {@code set-env} writes the
     *  var, {@code list-env} exits 0, {@code remove-env} removes it. */
    @Test
    @DisplayName("config set-env / list-env / remove-env lifecycle")
    void test_config_env_lifecycle(@TempDir Path tmp) {
        withConfigOverride(tmp, () -> {
            assertEquals(0, cli.execute("config",
                    "-n", "myagent", "-e", "com.example.MyAgent",
                    "-r", "cn-southwest-2", "-d", "pom.xml",
                    "--swr-org", "o", "--swr-repo", "r"),
                    "config add should exit 0");
            assertEquals(0, cli.execute("config", "set-env", "MY_VAR", "val", "-a", "myagent"),
                    "set-env should exit 0");
            String yaml = readString(tmp.resolve(".agentarts_config.yaml"));
            assertTrue(yaml.contains("MY_VAR"), "YAML should contain MY_VAR; got:\n" + yaml);
            assertTrue(yaml.contains("val"), "YAML should contain the env value; got:\n" + yaml);
            assertEquals(0, cli.execute("config", "list-env", "-a", "myagent"),
                    "list-env should exit 0");
            assertEquals(0, cli.execute("config", "remove-env", "MY_VAR", "-a", "myagent"),
                    "remove-env should exit 0");
            String after = readString(tmp.resolve(".agentarts_config.yaml"));
            assertFalse(after.contains("MY_VAR"), "MY_VAR should be gone after remove-env; got:\n" + after);
        });
    }

    /** Mirrors {@code test_config_set_default_and_remove}: add two agents,
     *  {@code set-default a2}, {@code remove a1}; a2 remains, a1 is gone. */
    @Test
    @DisplayName("config set-default / remove manage multiple agents")
    void test_config_set_default_and_remove(@TempDir Path tmp) {
        withConfigOverride(tmp, () -> {
            assertEquals(0, cli.execute("config",
                    "-n", "a1", "-e", "com.example.A1Agent",
                    "-r", "cn-southwest-2", "-d", "pom.xml",
                    "--swr-org", "o", "--swr-repo", "r"),
                    "config add a1 should exit 0");
            assertEquals(0, cli.execute("config",
                    "-n", "a2", "-e", "com.example.A2Agent",
                    "-r", "cn-southwest-2", "-d", "pom.xml",
                    "--swr-org", "o", "--swr-repo", "r"),
                    "config add a2 should exit 0");
            assertEquals(0, cli.execute("config", "set-default", "a2"),
                    "set-default should exit 0");
            assertEquals(0, cli.execute("config", "remove", "a1"),
                    "remove should exit 0");
            // Assert the semantic intent (a1's agent entry is gone, a2 remains)
            // rather than a raw substring: the YAML carries fields like
            // `language: "java17"` whose text contains "a1" as a substring.
            var cfgList = ConfigOperation.loadConfig();
            assertNotNull(cfgList.getAgent("a2"), "a2 should remain after remove");
            assertNull(cfgList.getAgent("a1"), "a1 should be removed from the agents map");
        });
    }

    private static String readString(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("Could not read " + p + ": " + e.getMessage());
            return "";
        }
    }

    // ---------------------------------------------------------------
    // dev (blocking server — driven via the `dev` CLI subcommand)
    // ---------------------------------------------------------------

    /** Drives the {@code dev} CLI subcommand in a background thread (which runs
     *  {@link com.huaweicloud.agentarts.toolkit.operations.DevOperation#runDevServer}),
     *  polls {@code /ping} on the bound port, POSTs {@code /invocations}, asserts the
     *  real entrypoint ran, then stops the dev server by interrupting the thread.</p>
     *
     *  <p>The scaffolded {@code Agent.java} exposes {@code createApp()}; rather than
     *  compile the project inside the test, the config's entrypoint is pointed at
     *  {@link DevTestAgent} (a fixture on the test classpath with the same factory
     *  contract). {@code DevOperation} loads it via its URLClassLoader parent and
     *  invokes {@code createApp()} — verifying the dev server actually drives the
     *  user's agent, not the built-in echo fallback.</p>
     */
    @Test
    @DisplayName("dev server loads the agent's createApp() factory and serves /ping and POST /invocations")
    void test_dev_server_drives_real_agent(@TempDir Path tmp) throws Exception {
        // 1. Scaffold a basic project so .agentarts_config.yaml exists.
        InitOperation.initProject("basic", "myagent", tmp.toString(),
                "cn-southwest-2", null, null);
        Path project = tmp.resolve("myagent");
        assertTrue(Files.isRegularFile(project.resolve("pom.xml")));

        // 2. Point the config's entrypoint at the test-fixture agent (same createApp()
        //    contract as the scaffolded Agent). The fixture is on the test classpath,
        //    so DevOperation resolves it without compiling the project.
        rewriteEntrypoint(project, DevTestAgent.class.getName());
        prepareClasspathOnlyProject(project);
        copyFixtureClass(project, DevTestAgent.class);

        try (DevHandle dev = startDev(project)) {
            // 3. POST /invocations on the dev server and assert the REAL entrypoint ran.
            JsonNode resp = postInvocation(dev.port, "{\"message\":\"hello from dev e2e\"}");
            assertTrue(resp.has("result"), "real entrypoint should return 'result'; got: " + resp);
            assertEquals("dev-test: hello from dev e2e", resp.get("result").asText(),
                    "dev server should drive the agent's createApp() factory, not the echo fallback");
        }
    }

    /** Missing entrypoints fail closed instead of silently starting an echo agent. */
    @Test
    @DisplayName("dev server fails closed when the configured entrypoint class is missing")
    void test_dev_server_rejects_missing_entrypoint(@TempDir Path tmp) throws Exception {
        InitOperation.initProject("basic", "myagent", tmp.toString(),
                "cn-southwest-2", null, null);
        Path project = tmp.resolve("myagent");
        prepareClasspathOnlyProject(project);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DevOperation.runDevServer(findAvailablePort(), "127.0.0.1",
                        false, null, project.toString(), new String[0]));
        assertTrue(failure.getMessage().contains("exited with code"));
    }

    /**
     * Exercise the dev-server runtime path without resolving a previously installed
     * SDK snapshot from the local Maven repository. Template compilation is covered
     * independently by the CLI module's generated-source contract tests.
     */
    private static void prepareClasspathOnlyProject(Path project) throws IOException {
        Files.delete(project.resolve("pom.xml"));
        Files.createDirectories(project.resolve("target/classes"));
    }

    private static void copyFixtureClass(Path project, Class<?> fixture) throws IOException {
        String resource = fixture.getName().replace('.', '/') + ".class";
        Path destination = project.resolve("target/classes").resolve(resource);
        Files.createDirectories(destination.getParent());
        try (var input = fixture.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Test fixture class not found: " + resource);
            }
            Files.copy(input, destination);
        }
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Rewrite {@code base.entrypoint} in the project's .agentarts_config.yaml. */
    private void rewriteEntrypoint(Path project, String entrypoint) throws IOException {
        Path cfg = project.resolve(".agentarts_config.yaml");
        String content = Files.readString(cfg, StandardCharsets.UTF_8);
        content = content.replaceFirst("(?m)^(\\s*entrypoint:\\s*).*$", "$1" + entrypoint);
        Files.writeString(cfg, content, StandardCharsets.UTF_8);
    }

    /** Handle keeping the dev server alive until closed. */
    private static final class DevHandle implements AutoCloseable {
        final int port;
        final Thread thread;
        final PrintStream origOut;
        final PrintStream origErr;
        String stdout = "";
        String stderr = "";

        DevHandle(int port, Thread thread, PrintStream origOut, PrintStream origErr) {
            this.port = port;
            this.thread = thread;
            this.origOut = origOut;
            this.origErr = origErr;
        }

        @Override
        public void close() {
            thread.interrupt();
            try { thread.join(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }

    /** Launch {@code dev} in a daemon thread against {@code project}, wait for the
     *  listening banner and a healthy /ping, and return a handle that keeps the
     *  server alive until closed. */
    private DevHandle startDev(Path project) throws Exception {
        CommandLine devCli = new CommandLine(new AgentArtsCli());
        int port = findAvailablePort();
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream capturedOut = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        PrintStream capturedErr = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        System.setOut(capturedOut);
        System.setErr(capturedErr);

        Thread devThread = new Thread(() -> devCli.execute("dev",
                "--port", Integer.toString(port),
                "--host", "127.0.0.1",
                "--path", project.toString()));
        devThread.setDaemon(true);
        devThread.start();

        capturedOut.flush();
        capturedErr.flush();
        String outStr = outBuf.toString(StandardCharsets.UTF_8);
        String errStr = errBuf.toString(StandardCharsets.UTF_8);

        // Poll /ping until healthy.
        boolean pingOk = false;
        for (int i = 0; i < 40; i++) {
            try {
                HttpURLConnection c = (HttpURLConnection)
                        new URL("http://127.0.0.1:" + port + "/ping").openConnection();
                c.setConnectTimeout(1000);
                c.setReadTimeout(1000);
                if (c.getResponseCode() == 200) { pingOk = true; c.disconnect(); break; }
                c.disconnect();
            } catch (Exception e) { Thread.sleep(250); }
        }
        if (!pingOk) {
            // Stop the server before failing so we don't leak a background thread.
            devThread.interrupt();
            try { devThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.setOut(origOut);
            System.setErr(origErr);
        }
        assertTrue(pingOk, "dev server did not come up (GET /ping). stdout=" + outStr + " err=" + errStr);

        DevHandle handle = new DevHandle(port, devThread, origOut, origErr);
        handle.stdout = outStr;
        handle.stderr = errStr;
        return handle;
    }

    /** POST a JSON body to /invocations on the dev server and return the parsed body. */
    private JsonNode postInvocation(int port, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                new URL("http://127.0.0.1:" + port + "/invocations").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        assertEquals(200, conn.getResponseCode(), "POST /invocations should return 200");
        JsonNode resp = JsonUtils.MAPPER.readTree(
                new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        conn.disconnect();
        return resp;
    }

    // ---------------------------------------------------------------
    // Unsupported framework templates fail early with a stable argument error.
    // ---------------------------------------------------------------

    /** The Java toolkit intentionally ships {@code basic} and {@code agentscope}
     *  templates (see
     *  {@code agentarts-toolkit-cli/src/main/resources/templates/}). These
     *  tests verify excluded framework templates fail before writing files. */
    @Test
    @DisplayName("langgraph template is not supported (throws)")
    void test_langgraph_template_not_supported(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () ->
                InitOperation.initProject("langgraph", "x", tmp.toString(),
                        "cn-southwest-2", null, null));
    }

    @Test
    @DisplayName("langchain template is not supported (throws)")
    void test_langchain_template_not_supported(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () ->
                InitOperation.initProject("langchain", "x", tmp.toString(),
                        "cn-southwest-2", null, null));
    }

    @Test
    @DisplayName("google-adk template is not supported (throws)")
    void test_google_adk_template_not_supported(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () ->
                InitOperation.initProject("google-adk", "x", tmp.toString(),
                        "cn-southwest-2", null, null));
    }
}
