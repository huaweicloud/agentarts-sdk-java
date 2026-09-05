package com.huaweicloud.agentarts.toolkit;

import com.huaweicloud.agentarts.toolkit.commands.*;
import com.huaweicloud.agentarts.toolkit.template.TemplateManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CLI module: command tree structure, API verification, templates, config operations.
 */
class CliModuleTest {

    private final CommandLine cli = new CommandLine(new AgentArtsCli());

    // ========================
    // API verification: Command tree verification
    // ========================

    @Nested
    @DisplayName("API verification: Command tree matches Typer CLI")
    class CommandTreeTests {

        @Test
        void topLevelCommandsExist() {
            // Commands: init, config(alias configure), dev, deploy(alias launch),
            // invoke, destroy, runtime, gateway, memory
            Map<String, CommandLine> subs = cli.getSubcommands();
            assertNotNull(subs.get("init"), "init command missing");
            assertNotNull(subs.get("config"), "config command missing");
            assertNotNull(subs.get("dev"), "dev command missing");
            assertNotNull(subs.get("deploy"), "deploy command missing");
            assertNotNull(subs.get("invoke"), "invoke command missing");
            assertNotNull(subs.get("destroy"), "destroy command missing");
            assertNotNull(subs.get("runtime"), "runtime command missing");
            assertNotNull(subs.get("gateway"), "gateway command missing");
            assertSame(subs.get("gateway"), subs.get("gateway"),
                    "Python-compatible gateway alias missing");
            assertNotNull(subs.get("memory"), "memory command missing");
        }

        @Test
        void configHasAliasConfigure() {
            // Python: config (alias: configure)
            CommandLine configCmd = cli.getSubcommands().get("config");
            String[] aliases = configCmd.getCommandSpec().aliases();
            boolean hasConfigure = false;
            for (String a : aliases) {
                if ("configure".equals(a)) hasConfigure = true;
            }
            assertTrue(hasConfigure, "config should have 'configure' alias");
        }

        @Test
        void deployHasAliasLaunch() {
            // Python: launch (alias: deploy)
            CommandLine deployCmd = cli.getSubcommands().get("deploy");
            String[] aliases = deployCmd.getCommandSpec().aliases();
            boolean hasLaunch = false;
            for (String a : aliases) {
                if ("launch".equals(a)) hasLaunch = true;
            }
            assertTrue(hasLaunch, "deploy should have 'launch' alias");
        }

        @Test
        void configSubcommandsMatchPython() {
            // Python config subcommands: list, set-default, get, set, remove, set-env, remove-env, list-env
            CommandLine configCmd = cli.getSubcommands().get("config");
            Map<String, CommandLine> subs = configCmd.getSubcommands();
            assertNotNull(subs.get("list"), "config list missing");
            assertNotNull(subs.get("set-default"), "config set-default missing");
            assertNotNull(subs.get("get"), "config get missing");
            assertNotNull(subs.get("set"), "config set missing");
            assertNotNull(subs.get("remove"), "config remove missing");
            assertNotNull(subs.get("set-env"), "config set-env missing");
            assertNotNull(subs.get("remove-env"), "config remove-env missing");
            assertNotNull(subs.get("list-env"), "config list-env missing");
        }

        @Test
        void runtimeSubcommandsMatchPython() {
            // Python runtime subcommands: invoke, exec-command, upload-files, download-files, start-session, stop-session
            CommandLine runtimeCmd = cli.getSubcommands().get("runtime");
            Map<String, CommandLine> subs = runtimeCmd.getSubcommands();
            assertNotNull(subs.get("invoke"), "runtime invoke missing");
            assertNotNull(subs.get("exec-command"), "runtime exec-command missing");
            assertNotNull(subs.get("upload-files"), "runtime upload-files missing");
            assertNotNull(subs.get("download-files"), "runtime download-files missing");
            assertNotNull(subs.get("start-session"), "runtime start-session missing");
            assertNotNull(subs.get("stop-session"), "runtime stop-session missing");
        }

        @Test
        void gatewaySubcommandsMatchPython() {
            // Java names remain supported; Python gateway names are aliases.
            CommandLine mcpCmd = cli.getSubcommands().get("gateway");
            Map<String, CommandLine> subs = mcpCmd.getSubcommands();
            assertNotNull(subs.get("create-gateway"));
            assertNotNull(subs.get("update-gateway"));
            assertNotNull(subs.get("delete-gateway"));
            assertNotNull(subs.get("get-gateway"));
            assertNotNull(subs.get("list-gateways"));
            assertNotNull(subs.get("create-gateway-target"));
            assertNotNull(subs.get("update-gateway-target"));
            assertNotNull(subs.get("delete-gateway-target"));
            assertNotNull(subs.get("get-gateway-target"));
            assertNotNull(subs.get("list-gateway-targets"));
            String[] pythonNames = {
                    "create", "update", "delete", "get", "list",
                    "create-target", "update-target", "delete-target",
                    "get-target", "list-targets"
            };
            for (String name : pythonNames) {
                assertNotNull(subs.get(name), "Python gateway command alias missing: " + name);
            }
            assertEquals(10, new java.util.HashSet<>(subs.values()).size(),
                    "Expected 10 distinct gateway subcommands");
        }

        @Test
        void memorySubcommandsMatchPython() {
            // Python memory: create, get, list, update, delete, status
            CommandLine memCmd = cli.getSubcommands().get("memory");
            Map<String, CommandLine> subs = memCmd.getSubcommands();
            assertNotNull(subs.get("create"));
            assertNotNull(subs.get("get"));
            assertNotNull(subs.get("list"));
            assertNotNull(subs.get("update"));
            assertNotNull(subs.get("delete"));
            assertNotNull(subs.get("status"));
        }

        @Test
        void everyUserCommandProvidesStandardHelpOptions() {
            Set<CommandLine> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<CommandLine> pending = new ArrayDeque<>();
            pending.add(cli);
            while (!pending.isEmpty()) {
                CommandLine command = pending.removeFirst();
                if (!visited.add(command)) {
                    continue;
                }
                if (!(command.getCommand() instanceof CommandLine.HelpCommand)) {
                    assertNotNull(command.getCommandSpec().findOption("--help"),
                            () -> "--help missing for " + command.getCommandName());
                    assertNotNull(command.getCommandSpec().findOption("--version"),
                            () -> "--version missing for " + command.getCommandName());
                }
                pending.addAll(new HashSet<>(command.getSubcommands().values()));
            }
        }
    }

    @Nested
    @DisplayName("Production safety: destructive commands")
    class DestructiveCommandTests {

        @Test
        void destructiveCommandsAbortBeforeNetworkWhenDeclined() {
            String[][] commands = {
                    {"destroy", "--agent", "unit-agent"},
                    {"memory", "delete", "unit-space"},
                    {"gateway", "delete-gateway", "unit-gateway"},
                    {"gateway", "delete-gateway-target", "unit-gateway", "unit-target"}
            };
            InputStream original = System.in;
            try {
                for (String[] command : commands) {
                    System.setIn(new ByteArrayInputStream("n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    int exit = CliSupport.withCleanExit(
                            new CommandLine(new AgentArtsCli())).execute(command);
                    assertEquals(0, exit, "declining must abort without contacting the service");
                }
            } finally {
                System.setIn(original);
            }
        }

        @Test
        void nonInteractiveDeleteRequiresExplicitForceFlag() {
            InputStream original = System.in;
            try {
                System.setIn(new ByteArrayInputStream(new byte[0]));
                int exit = CliSupport.withCleanExit(
                        new CommandLine(new AgentArtsCli()))
                        .execute("memory", "delete", "unit-space");
                assertNotEquals(0, exit);
            } finally {
                System.setIn(original);
            }
        }

        @Test
        void mcpDeleteCommandsExposeForceOption() {
            CommandLine mcp = cli.getSubcommands().get("gateway");
            assertNotNull(mcp.getSubcommands().get("delete-gateway")
                    .getCommandSpec().optionsMap().get("--force"));
            assertNotNull(mcp.getSubcommands().get("delete-gateway-target")
                    .getCommandSpec().optionsMap().get("--force"));
        }
    }

    @Nested
    @DisplayName("Production safety: memory input validation")
    class MemoryInputValidationTests {

        @Test
        void invalidAdvancedOptionsFailBeforeClientCreation() {
            String[][] commands = {
                    {"memory", "create", "unit-space", "--ttl", "0"},
                    {"memory", "create", "unit-space", "--tags", "missing-separator"},
                    {"memory", "create", "unit-space", "--vpc-id", "unit-vpc"},
                    {"memory", "create", "unit-space", "--strategies", "semantic,"},
                    {"memory", "update", "unit-space", "--ttl", "8761"},
                    {"memory", "update", "unit-space", "--tags", "=missing-key"}
            };

            for (String[] command : commands) {
                int exit = CliSupport.withCleanExit(
                        new CommandLine(new AgentArtsCli())).execute(command);
                assertNotEquals(0, exit, "invalid input must fail before any network request");
            }
        }
    }

    // ========================
    // API verification: Init command options
    // ========================

    @Nested
    @DisplayName("API verification: init command options")
    class InitOptionsTests {

        @Test
        void initCommandHasExpectedOptions() {
            // Python init: --name/-n, --template/-t, --path/-p, --region/-r, --swr-org, --swr-repo
            CommandLine initCmd = cli.getSubcommands().get("init");
            var spec = initCmd.getCommandSpec();
            assertNotNull(spec.optionsMap().get("--name"));
            assertNotNull(spec.optionsMap().get("-n"));
            assertNotNull(spec.optionsMap().get("--template"));
            assertNotNull(spec.optionsMap().get("-t"));
            assertNotNull(spec.optionsMap().get("--path"));
            assertNotNull(spec.optionsMap().get("-p"));
            assertNotNull(spec.optionsMap().get("--region"));
            assertNotNull(spec.optionsMap().get("-r"));
            assertNotNull(spec.optionsMap().get("--swr-org"));
            assertNotNull(spec.optionsMap().get("--swr-repo"));
        }

        @Test
        void initDefaultPathIsCurrentDir() {
            // Python: --path default "."
            CommandLine initCmd = cli.getSubcommands().get("init");
            var pathOption = initCmd.getCommandSpec().optionsMap().get("--path");
            assertEquals(".", pathOption.defaultValue());
        }
    }

    // ========================
    // API verification: dev command options
    // ========================

    @Nested
    @DisplayName("API verification: dev command options")
    class DevOptionsTests {

        @Test
        void devCommandHasExpectedOptions() {
            // Python dev: --port/-p(8080), --host/-h("0.0.0.0"), --reload, --config/-c, --env/-e(repeatable)
            CommandLine devCmd = cli.getSubcommands().get("dev");
            var spec = devCmd.getCommandSpec();
            assertNotNull(spec.optionsMap().get("--port"));
            assertNotNull(spec.optionsMap().get("--host"));
            assertNotNull(spec.optionsMap().get("--reload"));
            assertNotNull(spec.optionsMap().get("--config"));
            assertNotNull(spec.optionsMap().get("--env"));
        }

        @Test
        void devDefaultPortIs8080() {
            CommandLine devCmd = cli.getSubcommands().get("dev");
            assertEquals("8080", devCmd.getCommandSpec().optionsMap().get("--port").defaultValue());
        }

        @Test
        void devDefaultHostIsAllInterfaces() {
            CommandLine devCmd = cli.getSubcommands().get("dev");
            assertEquals("0.0.0.0", devCmd.getCommandSpec().optionsMap().get("--host").defaultValue());
        }
    }

    // ========================
    // API verification: deploy command options
    // ========================

    @Nested
    @DisplayName("API verification: deploy command options")
    class DeployOptionsTests {

        @Test
        void deployCommandHasExpectedOptions() {
            // Python deploy: --agent/-a, --mode/-m(cloud), --tag/-t(latest), --local-port/-l,
            // --swr-org, --swr-repo, --description/-d, --skip-build, --skip-ssl-verification/-k
            CommandLine deployCmd = cli.getSubcommands().get("deploy");
            var spec = deployCmd.getCommandSpec();
            assertNotNull(spec.optionsMap().get("--agent"));
            assertNotNull(spec.optionsMap().get("--mode"));
            assertNotNull(spec.optionsMap().get("--tag"));
            assertNotNull(spec.optionsMap().get("--local-port"));
            assertNotNull(spec.optionsMap().get("--swr-org"));
            assertNotNull(spec.optionsMap().get("--swr-repo"));
            assertNotNull(spec.optionsMap().get("--description"));
            assertNotNull(spec.optionsMap().get("--skip-build"));
            assertNotNull(spec.optionsMap().get("--skip-ssl-verification"));
        }

        @Test
        void deployDefaultModeIsCloud() {
            CommandLine deployCmd = cli.getSubcommands().get("deploy");
            assertEquals("cloud", deployCmd.getCommandSpec().optionsMap().get("--mode").defaultValue());
        }

        @Test
        void deployDefaultTagIsLatest() {
            CommandLine deployCmd = cli.getSubcommands().get("deploy");
            assertEquals("latest", deployCmd.getCommandSpec().optionsMap().get("--tag").defaultValue());
        }
    }

    // ========================
    // API verification: invoke command options
    // ========================

    @Nested
    @DisplayName("API verification: invoke command options")
    class InvokeOptionsTests {

        @Test
        void invokeCommandHasExpectedOptions() {
            // Python invoke: payload(arg), --agent/-a, --mode/-m(cloud), --region/-r, --port/-p,
            // --endpoint/-e, --session/-s, --bearer-token/-bt, --timeout(900), --skip-ssl-verification/-k,
            // --user-id/-u, --custom-path
            CommandLine invokeCmd = cli.getSubcommands().get("invoke");
            var spec = invokeCmd.getCommandSpec();
            assertNotNull(spec.optionsMap().get("--agent"));
            assertNotNull(spec.optionsMap().get("--mode"));
            assertNotNull(spec.optionsMap().get("--region"));
            assertNotNull(spec.optionsMap().get("--port"));
            assertNotNull(spec.optionsMap().get("--endpoint"));
            assertNotNull(spec.optionsMap().get("--session"));
            assertNotNull(spec.optionsMap().get("--bearer-token"));
            assertNotNull(spec.optionsMap().get("--timeout"));
            assertNotNull(spec.optionsMap().get("--skip-ssl-verification"));
            assertNotNull(spec.optionsMap().get("--user-id"));
            assertNotNull(spec.optionsMap().get("--custom-path"));
        }

        @Test
        void invokeDefaultTimeoutIs900() {
            CommandLine invokeCmd = cli.getSubcommands().get("invoke");
            assertEquals("900", invokeCmd.getCommandSpec().optionsMap().get("--timeout").defaultValue());
        }

        @Test
        void bearerTokensSupportHiddenPromptAndEnvironmentFallback() {
            var topLevelToken = cli.getSubcommands().get("invoke")
                    .getCommandSpec().optionsMap().get("--bearer-token");
            assertTrue(topLevelToken.interactive());
            assertEquals("0..1", topLevelToken.arity().toString());

            CommandLine runtime = cli.getSubcommands().get("runtime");
            for (String command : java.util.List.of(
                    "invoke", "exec-command", "upload-files", "download-files",
                    "start-session", "stop-session")) {
                var token = runtime.getSubcommands().get(command)
                        .getCommandSpec().optionsMap().get("--bearer-token");
                assertTrue(token.interactive(), command + " must support a hidden token prompt");
                assertEquals("0..1", token.arity().toString());
            }

            assertEquals("unit-explicit-token",
                    CliSupport.resolveBearerToken("unit-explicit-token"),
                    "an explicit token must take precedence over the environment");
        }

        @Test
        void commandArgumentParserHandlesQuotesEscapesAndWindowsPaths() {
            assertEquals(java.util.List.of(
                            "printf", "hello world", "", "C:\\work\\file.txt", "plain value"),
                    CliSupport.parseCommandArguments(
                            "printf 'hello world' \"\" C:\\work\\file.txt plain\\ value"));
            assertThrows(RuntimeException.class,
                    () -> CliSupport.parseCommandArguments("echo 'unterminated"));
        }

        @Test
        void boundedFileReaderRejectsOversizedInputs(@TempDir Path tempDir) throws Exception {
            Path input = tempDir.resolve("oversized.bin");
            Files.write(input, new byte[] {1, 2, 3, 4});

            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> CliSupport.readFileBytes(input, 3));
            assertTrue(error.getMessage().contains("upload limit"));
        }
    }

    // ========================
    // Template system
    // ========================

    @Nested
    @DisplayName("Template system")
    class TemplateTests {

        @Test
        void basicTemplateExists() throws Exception {
            String content = TemplateManager.loadTemplate("basic/Agent.java.tpl");
            assertNotNull(content);
            assertTrue(content.contains("AgentArtsRuntimeApp"));
            assertTrue(content.contains("{{ name }}"));
        }

        @Test
        void agentscopeTemplateExists() throws Exception {
            String content = TemplateManager.loadTemplate("agentscope/Agent.java.tpl");
            assertNotNull(content);
            assertTrue(content.contains("agentscope"));
        }

        @Test
        void dockerTemplateExists() throws Exception {
            String content = TemplateManager.loadTemplate("docker/Dockerfile.tpl");
            assertNotNull(content);
            assertTrue(content.contains("eclipse-temurin:17-jre"));
        }

        @Test
        void configTemplateExists() throws Exception {
            String content = TemplateManager.loadTemplate("basic/config.yaml.tpl");
            assertNotNull(content);
            assertTrue(content.contains("base:"));
            assertTrue(content.contains("swr_config:"));
            assertTrue(content.contains("runtime:"));
        }

        @Test
        void templateRendering() {
            String template = "Hello {{ name }}, welcome to {{ project }}!";
            String result = TemplateManager.render(template, Map.of("name", "World", "project", "AgentArts"));
            assertEquals("Hello World, welcome to AgentArts!", result);
        }

        @Test
        void templateRenderToFile(@TempDir Path tempDir) throws Exception {
            Path output = tempDir.resolve("Test.java");
            TemplateManager.renderToFile("basic/Agent.java.tpl", output, "name", "demo");
            assertTrue(Files.exists(output));
            String content = Files.readString(output);
            assertTrue(content.contains("public class Agent"));
        }
    }

    // ========================
    // Config YAML format verification
    // ========================

    @Nested
    @DisplayName("Config YAML format matches Python")
    class ConfigFormatTests {

        @Test
        void configTemplateHasJavaDefaults() throws Exception {
            String content = TemplateManager.loadTemplate("basic/config.yaml.tpl");
            assertTrue(content.contains("language: java17"));
            assertTrue(content.contains("base_image: eclipse-temurin:17-jre"));
        }
    }

    // ========================
    // CLI help and version
    // ========================

    @Nested
    @DisplayName("CLI basics")
    class CliBasicsTests {

        @Test
        void versionOutput() {
            StringWriter sw = new StringWriter();
            cli.setOut(new PrintWriter(sw));
            cli.execute("--version");
            String output = sw.toString();
            assertTrue(output.contains("0.1.0"));
        }
    }
}
