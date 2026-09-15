package com.huaweicloud.agentarts.sdk.tests.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.service.runtime.RuntimeClient;
import com.huaweicloud.agentarts.toolkit.AgentArtsCli;
import com.huaweicloud.agentarts.toolkit.commands.CliSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CLI runtime e2e against a Docker-deployed agent — mirrors
 * {@code tests/integration/toolkit/test_cli_deployed_runtime.py}.
 *
 * <p>Session-scoped setup runs {@code init → (enable file transfer) → deploy}
 * (Docker build + SWR push + cloud runtime create) once via the in-process
 * picocli CLI; the four tests reuse that live agent, and {@code destroy} +
 * {@code docker rmi} run as LIFO teardown.</p>
 *
 * <p>Gated behind cloud_credentials + ALLOW_CREATE + RUN_BILLABLE + Docker on
 * PATH, so it skips by default. LIFO teardown deletes the runtime agent, the
 * local image, and the SWR repo/namespace — no cloud residue.</p>
 */
@Tag("e2e")
@DisplayName("CLI Deployed Runtime E2E Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CliDeployedRuntimeE2ETest {

    private static CommandLine cli;
    private static E2EResourceRegistry registry;
    private static String runId;
    private static Path projectDir;
    private static String agentName;
    private static String region;
    private static String prevUserDir;
    private static boolean deployOk;

    private String stdout;
    private String stderr;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        assumeTrue(E2EConfig.hasCloudCredentials(),
                "Set HUAWEICLOUD_SDK_AK and HUAWEICLOUD_SDK_SK to run cloud tests");
        assumeTrue(E2EConfig.allowCreate(),
                "Set AGENTARTS_TEST_ALLOW_CREATE=1 to run deploy lifecycle tests");
        assumeTrue(E2EConfig.allowBillable(),
                "Set AGENTARTS_TEST_RUN_BILLABLE=1 to run billable deploy/invoke tests");
        assumeTrue(dockerAvailable(),
                "Docker is not available on PATH — skipping Docker deploy e2e");

        cli = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli()));
        cli.setOut(new PrintWriter(new StringWriter()));
        cli.setErr(new PrintWriter(new StringWriter()));
        registry = new E2EResourceRegistry();
        runId = E2EConfig.getRunId();
        region = E2EConfig.getRegion();
        agentName = E2EHelpers.uniqueName("cli-rt", runId);
        // CLI name validation: lowercase, digits, hyphens only — uniqueName already matches.
        projectDir = tempDir.resolve(agentName);

        // The deploy chain shells out to `mvn`/`docker` and reads .agentarts_config.yaml
        // from the CWD, so point user.dir at the project dir for the duration of the class.
        prevUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", projectDir.toAbsolutePath().toString());

        // Ensure the SDK runtime artifact is in the local Maven repo so the generated
        // project (which depends on agentarts-sdk-runtime:0.1.0) can build.
        ensureSdkInstalled();

        // 1. init (basic template) — writes a deploy-ready .agentarts_config.yaml + Dockerfile.
        int initExit = runCliStatic("init", "-n", agentName, "-t", "basic",
                "-p", tempDir.toAbsolutePath().toString(), "-r", region);
        assertEquals(0, initExit, "init should exit 0; stderr=" + lastStderr());

        // 2. Enable file transfer in the generated config (init writes enabled=false,
        //    and the backend rejects enabling it after the agent is created).
        enableFileTransfer(projectDir);

        // 3. deploy (Docker build + SWR push + create cloud runtime).
        int deployExit = runCliStatic("deploy", "--agent", agentName, "--mode", "cloud");
        if (deployExit != 0) {
            // Surface the real backend/build error but don't mask it as a test failure
            // of an unrelated assertion — fail the setup explicitly.
            System.setProperty("user.dir", prevUserDir);
            fail("deploy did not complete (exit " + deployExit + "); stderr=" + lastStderr()
                    + " stdout=" + lastStdout());
        }
        deployOk = true;

        // 4. Register LIFO cleanup: destroy the runtime agent, remove the local
        // image, and delete the SWR namespace/repo created by deploy (org/repo
        // both equal the agent name, auto-created per the basic template). SWR
        // cleanup is registered FIRST so it runs LAST in LIFO order — after the
        // runtime agent is gone, the SWR repo is deleted (must precede namespace
        // deletion, which requires an empty namespace), then the namespace.
        final String name = agentName;
        final String reg = region;
        registry.register(() -> {
            try (com.huaweicloud.agentarts.sdk.service.swr.SWRServiceClient swr =
                         new com.huaweicloud.agentarts.sdk.service.swr.SWRServiceClient(reg, true)) {
                com.huaweicloud.sdk.swr.v2.SwrClient sc = swr.getSyncClient();
                try {
                    sc.deleteRepo(new com.huaweicloud.sdk.swr.v2.model.DeleteRepoRequest()
                            .withNamespace(name).withRepository(name));
                } catch (Exception ignored) {
                    /* repo already gone */
                }
                try {
                    sc.deleteNamespaces(new com.huaweicloud.sdk.swr.v2.model.DeleteNamespacesRequest()
                            .withNamespace(name));
                } catch (Exception ignored) {
                    /* namespace already gone */
                }
            } catch (Exception ignored) {
                /* swr client failure — best-effort */
            }
        }, "swr-namespace:" + name);
        registry.register(() -> {
            try (RuntimeClient c = new RuntimeClient(reg, true)) {
                c.deleteAgentByName(name);
            } catch (Exception ignored) {
                /* already deleted */
            }
        }, "runtime-agent:" + name);
        registry.register(() -> {
            runProcess(Arrays.asList("docker", "rmi", "-f", name + ":latest"),
                    null, 30, "docker rmi");
        }, "docker-image:" + name);
    }

    @AfterAll
    static void tearDown() {
        if (prevUserDir != null) {
            System.setProperty("user.dir", prevUserDir);
        }
        if (registry != null) registry.cleanupAll();
    }

    /** Mirrors {@code test_deploy_succeeds}: the deploy created a cloud runtime;
     *  the config now carries an {@code agent_id}. */
    @Test
    @Order(1)
    @DisplayName("deploy creates a cloud runtime (config carries agent_id)")
    void test_deploy_succeeds() throws Exception {
        assertTrue(deployOk, "deploy should have succeeded in setUp");
        assertNotNull(agentName, "agent name should be set");
        assertFalse(agentName.isBlank(), "agent name should be non-empty");
        Path config = projectDir.resolve(".agentarts_config.yaml");
        assertTrue(Files.exists(config), "project config should exist after init/deploy");
        String cfg = Files.readString(config);
        assertTrue(cfg.contains("agent_id"),
                "config should carry agent_id after deploy; config=\n" + cfg);
        // And the agent_id value should be non-empty (not "agent_id: null").
        assertTrue(cfg.matches("(?s).*agent_id:\\s*[^\\sn][^\\n]*.*"),
                "agent_id should be a non-empty value, not null; config=\n" + cfg);
    }

    /** Mirrors {@code test_invoke_deployed_agent}: invoke the deployed agent via CLI. */
    @Test
    @Order(2)
    @DisplayName("invoke deployed agent via CLI")
    void test_invoke_deployed_agent() {
        int exit = runCli("invoke", "--agent", agentName, "--mode", "cloud",
                "{\"message\":\"hello from deployed e2e\"}");
        // Soft-skip on backend not-ready / routing errors rather than hard-failing,
        // matching the Python suite's tolerance for transient cloud states — but only
        // for non-assertion-level errors (exit non-zero with an explicit rejection).
        if (exit != 0 && (stderr.contains("404") || stderr.contains("not ready")
                || stderr.contains("not found") || stderr.contains("503"))) {
            assumeTrue(false, "Deployed agent not routable yet: " + stderr);
        }
        assertEquals(0, exit, "invoke should exit 0; stderr=" + stderr + " stdout=" + stdout);
        // The agent must actually return a real response, not just exit 0. The deployed
        // basic-template agent returns {"result":"Hello from <name>: hello from deployed e2e"},
        // so the response body must carry the echoed "hello" sentinel. Do NOT accept
        // {"response":...} / {"data":...} / {"ok":...} — those are fallback/envelope shapes
        // that would let a degraded (non-agent) 2xx body pass, the same hole that once hid
        // the dev echo-fallback bug.
        JsonNode node = null;
        try {
            node = JsonUtils.MAPPER.readTree(stdout);
        } catch (Exception e) {
            // stdout not JSON — fall through to the content-based check below.
        }
        assertNotNull(node, "invoke stdout should be parseable JSON; stdout=" + stdout);
        assertTrue(stdout.contains("hello"),
                "invoke response should echo back the 'hello' sentinel from the request; stdout=" + stdout);
    }

    /** Mirrors {@code test_runtime_session_on_deployed_agent}: start-session →
     *  exec-command → stop-session. */
    @Test
    @Order(3)
    @DisplayName("runtime start-session → exec-command → stop-session on deployed agent")
    void test_runtime_session_on_deployed_agent() {
        int start = runCli("runtime", "start-session", "--agent", agentName,
                "--region", region);
        if (start != 0 && (stderr.contains("404") || stderr.contains("not ready")
                || stderr.contains("503"))) {
            assumeTrue(false, "Deployed agent not routable yet: " + stderr);
        }
        assertEquals(0, start, "start-session should exit 0; stderr=" + stderr + " stdout=" + stdout);

        String sessionId = extractSessionId(stdout);
        assertNotNull(sessionId, "start-session should return a session_id; stdout=" + stdout);

        int exec = runCli("runtime", "exec-command", "--agent", agentName,
                "--region", region, "--session", sessionId, "echo aa-it");
        assertEquals(0, exec, "exec-command should exit 0; stderr=" + stderr + " stdout=" + stdout);
        assertTrue(stdout.contains("aa-it"),
                "exec-command stdout should contain the echoed 'aa-it'; stdout=" + stdout);

        int stop = runCli("runtime", "stop-session", "--agent", agentName,
                "--region", region, "--session", sessionId);
        assertEquals(0, stop, "stop-session should exit 0; stderr=" + stderr + " stdout=" + stdout);
    }

    /** Mirrors {@code test_runtime_file_transfer_on_deployed_agent}: upload → download.
     *  Soft-skips on 401 (IAM-only agent may need a bearer token for file transfer). */
    @Test
    @Order(4)
    @DisplayName("runtime upload-files → download-files on deployed agent")
    void test_runtime_file_transfer_on_deployed_agent() throws Exception {
        int start = runCli("runtime", "start-session", "--agent", agentName,
                "--region", region);
        assertEquals(0, start, "start-session should exit 0; stderr=" + stderr);
        String sessionId = extractSessionId(stdout);
        assertNotNull(sessionId, "start-session should return a session_id; stdout=" + stdout);

        Path localFile = Files.createTempFile("aa-it-upload-", ".txt");
        Files.writeString(localFile, "hello-aa-it");
        String remoteFile = "/home/user/" + localFile.getFileName();
        Path downloaded = Files.createTempFile("aa-it-download-", ".txt");
        try {
            int up = runCli("runtime", "upload-files", "--agent", agentName,
                    "--region", region, "--session", sessionId,
                    "--files", localFile.toAbsolutePath().toString(),
                    "--path", "/home/user/");
            if (up != 0 && (stderr.contains("401") || stdout.contains("401"))) {
                assumeTrue(false, "upload-files returned 401 (IAM-only agent likely needs a bearer token)");
            }
            assertEquals(0, up, "upload-files should exit 0; stderr=" + stderr + " stdout=" + stdout);

            int dl = runCli("runtime", "download-files", "--agent", agentName,
                    "--region", region, "--session", sessionId,
                    "--path", remoteFile,
                    "--output", downloaded.toAbsolutePath().toString());
            assertEquals(0, dl, "download-files should exit 0; stderr=" + stderr + " stdout=" + stdout);

            // Assert the round-trip content matches — never silently pass.
            String content = Files.readString(downloaded).trim();
            assertEquals("hello-aa-it", content,
                    "downloaded content should match uploaded; got='" + content + "'");
        } finally {
            Files.deleteIfExists(localFile);
            Files.deleteIfExists(downloaded);
            runCli("runtime", "stop-session", "--agent", agentName,
                    "--region", region, "--session", sessionId);
        }
    }

    // ========================
    // Helpers
    // ========================

    private int runCli(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int exit;
        try {
            exit = cli.execute(args);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        stdout = out.toString(StandardCharsets.UTF_8);
        stderr = err.toString(StandardCharsets.UTF_8);
        return exit;
    }

    private static String lastStdout;
    private static String lastStderr;

    private static int runCliStatic(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int exit;
        try {
            exit = cli.execute(args);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        lastStdout = out.toString(StandardCharsets.UTF_8);
        lastStderr = err.toString(StandardCharsets.UTF_8);
        return exit;
    }

    private static String lastStdout() { return lastStdout == null ? "" : lastStdout; }
    private static String lastStderr() { return lastStderr == null ? "" : lastStderr; }

    private static String extractSessionId(String text) {
        try {
            JsonNode node = JsonUtils.MAPPER.readTree(text);
            String s = findText(node, "session_id");
            if (s != null) return s;
            JsonNode data = node.get("data");
            if (data != null) return findText(data, "session_id");
        } catch (Exception ignored) {
            // not JSON
        }
        return null;
    }

    private static String findText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull() && !v.asText("").isEmpty()) {
                return v.asText();
            }
        }
        return null;
    }

    /** Enable file_transfer_config in the generated config (init writes enabled=false). */
    private static void enableFileTransfer(Path projectDir) throws Exception {
        Path cfg = projectDir.resolve(".agentarts_config.yaml");
        String text = Files.readString(cfg);
        // The generated config has `file_transfer_config:\n          enabled: false`.
        text = text.replaceFirst("file_transfer_config:\\s*\\n\\s*enabled:\\s*false",
                "file_transfer_config:\n          enabled: true");
        Files.writeString(cfg, text);
    }

    /** Check Docker is on PATH by running `docker --version`. */
    private static boolean dockerAvailable() {
        return runProcess(Arrays.asList("docker", "--version"), null, 15, "docker --version") == 0;
    }

    /**
     * Ensure the SDK runtime artifact (and its upstream modules) are installed to
     * the local Maven repo, so the generated project's {@code mvn package} can
     * resolve {@code agentarts-sdk-runtime:0.1.0}.
     */
    private static void ensureSdkInstalled() {
        // Locate the SDK root: walk up from the original user.dir until the runtime
        // module pom is a sibling.
        File dir = new File(prevUserDir).getAbsoluteFile();
        File root = null;
        for (File d = dir; d != null; d = d.getParentFile()) {
            if (new File(d, "agentarts-sdk-runtime/pom.xml").isFile()
                    && new File(d, "pom.xml").isFile()) {
                root = d;
                break;
            }
        }
        if (root == null) {
            System.out.println("[setup] Could not locate SDK root; assuming artifacts are in local repo.");
            return;
        }
        // Skip if the runtime jar is already in the local repo. Resolve the
        // *effective* local repo (it may be customized via ~/.m2/settings.xml
        // <localRepository> or -Dmaven.repo.local) rather than assuming the
        // default ~/.m2/repository — a hardcoded path would always miss on
        // machines with a custom local repo and trigger a redundant full
        // upstream rebuild on every run.
        File localRepo = resolveMavenLocalRepo();
        File localJar = new File(localRepo,
                "com/huaweicloud/agentarts/agentarts-sdk-runtime/"
                        + "0.1.0/agentarts-sdk-runtime-0.1.0.jar");
        if (localJar.isFile()) {
            System.out.println("[setup] agentarts-sdk-runtime already in local repo at " + localJar);
            return;
        }
        String mvn = findMvn();
        System.out.println("[setup] Installing SDK runtime to local repo (mvn install)...");
        int code = runProcess(Arrays.asList(mvn, "-q", "-B", "-DskipTests", "install",
                "-f", new File(root, "pom.xml").getAbsolutePath(),
                "-pl", "agentarts-sdk-runtime", "-am"),
                root, 600, "mvn install runtime");
        if (code != 0) {
            System.err.println("[setup] mvn install runtime failed (exit " + code
                    + ") — the generated project build may fail to resolve the SDK.");
        }
    }

    private static String findMvn() {
        String onPath = findOnPath("mvn.cmd");
        if (onPath != null) return onPath;
        onPath = findOnPath("mvn");
        if (onPath != null) return onPath;
        return "mvn";
    }

    private static String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String d : path.split(File.pathSeparator)) {
            if (d.isEmpty()) continue;
            File f = new File(d, name);
            if (f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    /**
     * Resolve the effective Maven local repository directory, in the same order
     * Maven does: {@code -Dmaven.repo.local} system property, then
     * {@code <localRepository>} in {@code ~/.m2/settings.xml}, then the global
     * {@code conf/settings.xml} (via {@code M2_HOME}/{@code MAVEN_HOME}), then
     * the default {@code ~/.m2/repository}.
     */
    private static File resolveMavenLocalRepo() {
        String prop = System.getProperty("maven.repo.local");
        if (prop != null && !prop.isBlank()) {
            return new File(prop.trim());
        }
        String home = System.getProperty("user.home");
        String fromUserSettings = readLocalRepositoryFromSettings(
                new File(home, ".m2" + File.separator + "settings.xml"));
        if (fromUserSettings != null) {
            return new File(fromUserSettings);
        }
        String mavenHome = System.getenv("M2_HOME");
        if (mavenHome == null || mavenHome.isBlank()) mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome != null && !mavenHome.isBlank()) {
            String fromGlobal = readLocalRepositoryFromSettings(
                    new File(mavenHome, "conf" + File.separator + "settings.xml"));
            if (fromGlobal != null) {
                return new File(fromGlobal);
            }
        }
        return new File(home, ".m2" + File.separator + "repository");
    }

    /** Extract {@code <localRepository>} from a Maven settings.xml (regex; comments ignored). */
    private static String readLocalRepositoryFromSettings(File settings) {
        if (settings == null || !settings.isFile()) return null;
        try {
            String content = Files.readString(settings.toPath());
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "<localRepository>\\s*([^<]+?)\\s*</localRepository>").matcher(content);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignored) {
            // unreadable settings — fall back to default
        }
        return null;
    }

    private static int runProcess(java.util.List<String> command, File workDir,
                                   int timeoutSec, String label) {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) pb.directory(workDir);
        pb.redirectErrorStream(true);
        Process process = null;
        try {
            process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("  [" + label + "] " + line);
                }
            }
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                System.err.println("  " + label + " timed out");
                return -1;
            }
            return process.exitValue();
        } catch (Exception e) {
            System.err.println("  " + label + " failed: " + e.getMessage());
            return -1;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
