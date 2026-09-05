package com.huaweicloud.agentarts.toolkit.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.config.AgentArtsConfig;
import com.huaweicloud.agentarts.sdk.core.config.AgentArtsConfigList;
import com.huaweicloud.agentarts.sdk.core.config.InvokeConfig;
import com.huaweicloud.agentarts.sdk.core.config.RuntimeConfig;
import com.huaweicloud.agentarts.sdk.core.config.SWRConfig;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.service.runtime.RuntimeClient;
import com.huaweicloud.agentarts.sdk.service.runtime.model.AgentInfo;
import com.huaweicloud.agentarts.sdk.service.runtime.model.CreateAgentRequest;
import com.huaweicloud.agentarts.sdk.service.swr.SWRServiceClient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deploy operation: build → SWR push → runtime create.
 *
 * <p>Operation: build and deploy an agent to cloud or local environment.
 * Cloud flow: project build → docker build → SWR namespace/repo create →
 * SWR login secret → docker login/tag/push → runtime create-or-update.
 * The created agent id is written back to {@code .agentarts_config.yaml}.</p>
 */
public class DeployOperation {

    private static final ObjectMapper MAPPER = JsonUtils.MAPPER;

    /**
     * Deploy an agent to Huawei Cloud or run locally.
     *
     * @param agentName   agent name to deploy (uses default agent from config when null)
     * @param mode        deployment mode: "cloud" or "local"
     * @param imageTag    Docker image tag (default "latest")
     * @param localPort   port for local mode (nullable)
     * @param swrOrg      SWR organization (overrides config)
     * @param swrRepo     SWR repository (overrides config)
     * @param description agent description (overrides config)
     * @param skipBuild   skip Docker build/push, use the configured artifact URL directly
     * @param skipSsl     skip SSL verification
     * @param platform    Docker build platform (e.g. "linux/amd64", "linux/arm64"); defaults to "linux/arm64"
     * @return {@code true} on success
     */
    public static boolean deployProject(String agentName, String mode, String imageTag,
                                         Integer localPort, String swrOrg, String swrRepo,
                                         String description, boolean skipBuild, boolean skipSsl,
                                         String platform) throws Exception {
        boolean local = "local".equals(mode);
        if (!local && !"cloud".equals(mode)) {
            throw new IllegalArgumentException("mode must be 'cloud' or 'local', got: " + mode);
        }
        String tag = (imageTag == null || imageTag.isBlank()) ? "latest" : imageTag;
        if (!tag.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("Docker image tag is invalid: " + tag);
        }
        if (localPort != null && (localPort < 1 || localPort > 65535)) {
            throw new IllegalArgumentException("local port must be between 1 and 65535");
        }
        String actualPlatform = (platform != null && !platform.isBlank()) ? platform : "linux/arm64";
        if (!actualPlatform.matches("^[a-zA-Z0-9][a-zA-Z0-9._-]*/[a-zA-Z0-9][a-zA-Z0-9._-]*$")) {
            throw new IllegalArgumentException("platform must be in os/arch format (e.g. linux/amd64, linux/arm64)");
        }
        boolean verifySsl = !skipSsl;

        // Resolve the project dir against the live `user.dir` system property
        // instead of `Paths.get(".")` — Java caches the process CWD at JVM
        // startup, so a relative path ignores runtime `user.dir` updates (tests
        // redirect CWD by setting the property; real CLI use is unaffected since
        // `user.dir` equals the CWD at startup).
        Path projectDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        AgentArtsConfigList configList = ConfigOperation.loadConfig();
        String agentKey = resolveAgentKey(configList, agentName);
        if (agentKey == null) {
            throw new IllegalStateException("No agent specified and no default agent configured. "
                    + "Run 'agentarts init' or 'agentarts config add' first.");
        }
        AgentArtsConfig agentCfg = configList.getAgent(agentKey);
        if (agentCfg == null) {
            throw new IllegalStateException("Agent '" + agentKey + "' not found in .agentarts_config.yaml");
        }
        if (agentCfg.getBase() == null) {
            throw new IllegalStateException("Agent '" + agentKey + "' is missing its base configuration");
        }

        String actualAgentName = agentCfg.getBase().getName() != null
                ? agentCfg.getBase().getName() : agentKey;
        String region = (agentCfg.getBase().getRegion() != null && !agentCfg.getBase().getRegion().isBlank())
                ? agentCfg.getBase().getRegion() : Constants.getRegion();
        RuntimeConfig runtimeCfg = agentCfg.getRuntime() != null ? agentCfg.getRuntime() : new RuntimeConfig();
        int servicePort = (runtimeCfg.getInvokeConfig() != null) ? runtimeCfg.getInvokeConfig().getPort() : 8080;

        System.out.println("Deploy Configuration:");
        System.out.println("  Agent: " + actualAgentName);
        System.out.println("  Mode: " + mode);
        System.out.println("  Region: " + region);

        if (skipBuild && local) {
            throw new IllegalArgumentException("--skip-build is only supported for cloud mode");
        }

        if (!skipBuild && !dockerAvailable()) {
            throw new IllegalStateException("Docker is not available or not running. "
                    + "Start Docker and try again.");
        }
        if (!skipBuild && !Files.isRegularFile(projectDir.resolve("Dockerfile"))) {
            throw new IllegalStateException("Dockerfile not found in current directory. "
                    + "Run 'agentarts init' to generate one first.");
        }

        // ---- Local mode: build + run a container ----
        if (local) {
            String localImage = actualAgentName + ":" + tag;
            System.out.println("Step 1/2: Building Docker image " + localImage);
            if (!buildProjectAndImage(actualAgentName, tag, projectDir, region, actualPlatform)) {
                return false;
            }
            int port = localPort != null ? localPort : servicePort;
            System.out.println("Step 2/2: Starting local container on port " + port);
            return runContainer(actualAgentName, tag, port, projectDir);
        }

        // ---- Cloud mode ----
        String configArtifactUrl = null;
        if (runtimeCfg.getArtifactSource() != null) {
            Object url = runtimeCfg.getArtifactSource().get("url");
            if (url != null && !"".equals(url) && !"null".equals(url)) {
                configArtifactUrl = String.valueOf(url);
            }
        }

        String finalImage;
        if (skipBuild) {
            if (configArtifactUrl == null) {
                throw new IllegalStateException("No artifact URL found in configuration. "
                        + "When using --skip-build, set runtime.artifact_source.url in .agentarts_config.yaml.");
            }
            finalImage = configArtifactUrl;
            System.out.println("Step 1/1: Using configured image URL " + finalImage + " (--skip-build)");
        } else if (configArtifactUrl != null) {
            // A pre-configured image URL takes precedence over building.
            finalImage = configArtifactUrl;
            System.out.println("Using artifact_source.url from configuration: " + finalImage);
        } else {
            SWRConfig swrCfg = agentCfg.getSwrConfig() != null ? agentCfg.getSwrConfig() : new SWRConfig();
            String finalOrg = (swrOrg != null && !swrOrg.isBlank()) ? swrOrg : swrCfg.getOrganization();
            String finalRepo = (swrRepo != null && !swrRepo.isBlank()) ? swrRepo : swrCfg.getRepository();
            if (finalOrg == null || finalOrg.isBlank() || finalRepo == null || finalRepo.isBlank()) {
                throw new IllegalStateException("SWR organization and repository must be configured "
                        + "(--swr-org/--swr-repo or swr_config in .agentarts_config.yaml).");
            }

            String localImage = actualAgentName + ":" + tag;
            System.out.println("Step 1/5: Building Docker image " + localImage);
            if (!buildProjectAndImage(actualAgentName, tag, projectDir, region, actualPlatform)) {
                return false;
            }

            System.out.println("Step 2/5: Setting up SWR resources (org=" + finalOrg
                    + ", repo=" + finalRepo + ")");
            try (SWRServiceClient swr = new SWRServiceClient(region, !verifySsl)) {
                if (swrCfg.isOrganizationAutoCreate()) {
                    swr.createNamespaceIfNotExists(finalOrg);
                    System.out.println("  Organization ready: " + finalOrg);
                } else {
                    swr.showNamespace(new com.huaweicloud.sdk.swr.v2.model.ShowNamespaceRequest()
                            .withNamespace(finalOrg));
                    System.out.println("  Using existing organization: " + finalOrg);
                }
                if (swrCfg.isRepositoryAutoCreate()) {
                    swr.createRepoIfNotExists(finalOrg, finalRepo);
                    System.out.println("  Repository ready: " + finalOrg + "/" + finalRepo);
                } else {
                    swr.showRepository(new com.huaweicloud.sdk.swr.v2.model.ShowRepositoryRequest()
                            .withNamespace(finalOrg).withRepository(finalRepo));
                    System.out.println("  Using existing repository: " + finalOrg + "/" + finalRepo);
                }

                System.out.println("Step 3/5: Getting SWR credentials");
                String[] secret = swr.createSwrSecret();
                String loginServer = secret[0];
                String username = secret[1];
                String password = secret[2];
                if (username.isEmpty() || password.isEmpty()) {
                    throw new IllegalStateException("Failed to obtain SWR login credentials.");
                }

                if (!dockerLogin(loginServer, username, password, projectDir)) {
                    throw new IllegalStateException("Failed to login to SWR registry " + loginServer);
                }

                finalImage = swr.getFullImageName(finalOrg, finalRepo, tag);
                System.out.println("Step 4/5: Tagging and pushing image");
                System.out.println("  Source: " + localImage);
                System.out.println("  Target: " + finalImage);
                if (!dockerTag(localImage, finalImage, projectDir)) {
                    throw new IllegalStateException("Failed to tag image " + localImage + " -> " + finalImage);
                }
                if (!dockerPush(finalImage, projectDir)) {
                    throw new IllegalStateException("Failed to push image " + finalImage);
                }
                System.out.println("  Image deployed to " + finalImage);
            }
        }

        // ---- Runtime create-or-update ----
        System.out.println("Step 5/5: Creating AgentArts runtime for '" + actualAgentName + "'");
        AgentInfo agent = createAgentartsRuntime(actualAgentName, finalImage, region,
                runtimeCfg, description, verifySsl);
        if (agent == null) {
            return false;
        }
        String runtimeId = agent.getId();

        // Write agent_id back to config.
        AgentArtsConfigList fresh = ConfigOperation.loadConfig();
        AgentArtsConfig freshAgent = fresh.getAgent(agentKey);
        if (freshAgent != null && freshAgent.getRuntime() != null) {
            freshAgent.getRuntime().setAgentId(runtimeId);
            ConfigOperation.saveConfig(fresh);
            System.out.println("  Wrote agent_id=" + runtimeId + " to .agentarts_config.yaml");
        }

        System.out.println("Deployment complete!");
        System.out.println("  Agent Name: " + actualAgentName);
        System.out.println("  Runtime ID: " + runtimeId);
        System.out.println("  Image: " + finalImage);
        System.out.println("  Region: " + region);
        return true;
    }

    /**
     * Create or update the AgentArts runtime via the control plane.
     */
    static AgentInfo createAgentartsRuntime(String agentName, String swrImage, String region,
                                            RuntimeConfig runtimeCfg, String description,
                                            boolean verifySsl) {
        try (RuntimeClient client = new RuntimeClient(region, verifySsl)) {
            CreateAgentRequest req = buildCreateRequest(agentName, swrImage, runtimeCfg, description);
            return client.createOrUpdateAgent(agentName, req);
        } catch (Exception e) {
            System.err.println("Failed to create runtime: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static CreateAgentRequest buildCreateRequest(String agentName, String swrImage,
                                                  RuntimeConfig runtimeCfg, String description) {
        // artifact_source: config map (with url overridden) or default {url, commands:[]}
        Map<String, Object> artifactSource = runtimeCfg.getArtifactSource() != null
                ? new LinkedHashMap<>(runtimeCfg.getArtifactSource()) : new LinkedHashMap<>();
        artifactSource.put("url", swrImage);
        if (!artifactSource.containsKey("commands")) {
            artifactSource.put("commands", List.of());
        }

        // invoke_config: from config or default
        InvokeConfig ic = runtimeCfg.getInvokeConfig();
        Map<String, Object> invokeConfig = new LinkedHashMap<>();
        invokeConfig.put("protocol", (ic != null && ic.getProtocol() != null && !ic.getProtocol().isBlank())
                ? ic.getProtocol() : "HTTP");
        invokeConfig.put("port", ic != null ? ic.getPort() : 8080);
        Map<String, Object> ftc;
        if (ic != null && ic.getFileTransferConfig() != null && !ic.getFileTransferConfig().isEmpty()) {
            ftc = new LinkedHashMap<>(ic.getFileTransferConfig());
        } else {
            ftc = new LinkedHashMap<>();
            ftc.put("enabled", false);
        }
        if (!ftc.containsKey("enabled")) {
            ftc.put("enabled", false);
        }
        invokeConfig.put("file_transfer_config", ftc);
        invokeConfig.put("url_match_type", (ic != null && ic.getUrlMatchType() != null && !ic.getUrlMatchType().isBlank())
                ? ic.getUrlMatchType() : "ACCURATE_MATCH");

        String desc = (description != null && !description.isBlank())
                ? description
                : "Agent created by AgentArts SDK Toolkit, deployed from " + swrImage;

        CreateAgentRequest req = new CreateAgentRequest()
                .withName(agentName)
                .withDescription(desc)
                .withArtifactSource(artifactSource)
                .withInvokeConfig(invokeConfig)
                .withNetworkConfig(runtimeCfg.getNetworkConfig())
                .withObservability(runtimeCfg.getObservability())
                .withExecutionAgencyName(runtimeCfg.getExecutionAgencyName())
                .withAgentGatewayId(runtimeCfg.getAgentGatewayId())
                .withIdentityConfiguration(runtimeCfg.getIdentityConfiguration());

        // environment_variables: Map<String,String> -> [{key,value}]
        if (runtimeCfg.getEnvironmentVariables() != null && !runtimeCfg.getEnvironmentVariables().isEmpty()) {
            List<Map<String, String>> envVars = new ArrayList<>();
            runtimeCfg.getEnvironmentVariables().forEach((k, v) -> {
                if (k != null && v != null) {
                    Map<String, String> kv = new LinkedHashMap<>();
                    kv.put("key", k);
                    kv.put("value", v);
                    envVars.add(kv);
                }
            });
            req.withEnvironmentVariables(envVars);
        }
        // tags: Map<String,String> -> [{key,value}]
        if (runtimeCfg.getTags() != null && !runtimeCfg.getTags().isEmpty()) {
            List<Map<String, String>> tags = new ArrayList<>();
            runtimeCfg.getTags().forEach((k, v) -> {
                if (k != null && v != null) {
                    Map<String, String> kv = new LinkedHashMap<>();
                    kv.put("key", k);
                    kv.put("value", v);
                    tags.add(kv);
                }
            });
            req.withTags(tags);
        }
        if (runtimeCfg.getArch() != null && !runtimeCfg.getArch().isBlank()) {
            req.withArch(runtimeCfg.getArch());
        }
        return req;
    }

    static String resolveAgentKey(AgentArtsConfigList configList, String agentName) {
        if (agentName != null && !agentName.isBlank()) {
            if (configList.getAgent(agentName) != null) {
                return agentName;
            }
            // Fall back to the default agent (the name may be the runtime name, not the config key).
        }
        String def = configList.getDefaultAgent();
        if (def != null && configList.getAgent(def) != null) {
            return def;
        }
        // First agent in the map.
        if (!configList.getAgents().isEmpty()) {
            return configList.getAgents().keySet().iterator().next();
        }
        return null;
    }

    // ========================
    // Project + Docker build
    // ========================

    /**
     * Build the project artifact (mvn package) then the Docker image.
     * The generated Dockerfile expects {@code target/<name>.jar}, so the project
     * must be packaged first.
     */
    private static boolean buildProjectAndImage(String agentName, String tag, Path projectDir,
                                                 String region, String platform) {
        // 1. Maven package (produces the fat jar via the shade plugin).
        if (Files.isRegularFile(projectDir.resolve("pom.xml"))) {
            // On Windows a bare `mvn` (no extension) is a bash shell script that
            // CreateProcess cannot execute (error 193); `mvn.cmd` is the native
            // entry point. On POSIX `mvn.cmd` does not exist, so fall back to `mvn`.
            String mvn = findOnPath("mvn.cmd");
            if (mvn == null) {
                mvn = findOnPath("mvn");
            }
            if (mvn == null) {
                // Best-effort: assume mvn is invokable by name on PATH (PATHEXT
                // resolves `mvn` -> `mvn.cmd` on Windows when no full path is given).
                mvn = "mvn";
            }
            System.out.println("  Building project artifact (mvn package)...");
            int code = runProcess(Arrays.asList(mvn, "-q", "-B", "-DskipTests", "package"),
                    projectDir.toFile(), null, "mvn package");
            if (code != 0) {
                System.err.println("  mvn package failed (exit " + code + ")");
                return false;
            }
        }
        // 2. docker build. Disable buildx provenance/attestation and pin a single
        // platform: Docker Desktop's default buildx builder emits an OCI manifest
        // LIST (with an attestation manifest) which SWR cannot parse — pushes fail
        // with "Invalid image, fail to parse 'manifest.json'". A single-platform
        // build with --provenance=false yields a classic Docker v2 schema-2
        // manifest that SWR accepts.
        int code = runProcess(Arrays.asList("docker", "build", "--provenance=false",
                "--platform=" + platform, "-t", agentName + ":" + tag, "."),
                projectDir.toFile(), null, "docker build");
        if (code != 0) {
            System.err.println("  docker build failed (exit " + code + ")");
            return false;
        }
        return true;
    }

    private static boolean runContainer(String imageName, String tag, int port, Path projectDir) {
        // Stop any prior container with the same name, then run a new one.
        runProcess(Arrays.asList("docker", "rm", "-f", imageName),
                projectDir.toFile(), null, "docker rm");
        int code = runProcess(Arrays.asList("docker", "run", "-d", "--rm",
                "--name", imageName, "-p", port + ":8080", imageName + ":" + tag),
                projectDir.toFile(), null, "docker run");
        if (code != 0) {
            System.err.println("  docker run failed (exit " + code + ")");
            return false;
        }
        System.out.println("  Container " + imageName + " running on http://localhost:" + port);
        return true;
    }

    private static boolean dockerLogin(String server, String username, String password, Path projectDir) {
        // `docker login` reads the password from stdin to avoid leaking it on the command line.
        int code = runProcess(Arrays.asList("docker", "login", "-u", username,
                "--password-stdin", server), projectDir.toFile(), password, "docker login");
        return code == 0;
    }

    private static boolean dockerTag(String source, String target, Path projectDir) {
        return runProcess(Arrays.asList("docker", "tag", source, target),
                projectDir.toFile(), null, "docker tag") == 0;
    }

    private static boolean dockerPush(String image, Path projectDir) {
        return runProcess(Arrays.asList("docker", "push", image),
                projectDir.toFile(), null, "docker push") == 0;
    }

    // ========================
    // Process helpers
    // ========================

    static boolean dockerAvailable() {
        return runProcess(Arrays.asList("docker", "version", "--format", "{{.Client.Version}}"),
                null, null, "docker version") == 0;
    }

    private static String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isEmpty()) continue;
            File f = new File(dir, name);
            if (f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    /**
     * Run a process, streaming stdout/stderr to the console, optionally piping
     * a string to stdin. Returns the exit code.
     */
    private static int runProcess(List<String> command, File workDir, String stdin, String label) {
        return ProcessRunner.run(command, workDir, stdin, label,
                ProcessRunner.configuredTimeout());
    }
}
