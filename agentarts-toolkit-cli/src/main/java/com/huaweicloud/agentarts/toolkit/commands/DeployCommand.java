package com.huaweicloud.agentarts.toolkit.commands;

import com.huaweicloud.agentarts.toolkit.operations.DeployOperation;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Deploy agent to Huawei Cloud (alias: launch).
 *
 * <p>CLI command: deploy agent to cloud or local environment.
 * Flow: docker build → SWR createNamespace/createRepo → createAuthorizationToken →
 * runtime create with V11 signing.</p>
 */
@Command(name = "deploy", aliases = {"launch"}, mixinStandardHelpOptions = true,
        description = "Deploy agent to Huawei Cloud")
public class DeployCommand implements Runnable {

    @Option(names = {"-a", "--agent"}, description = "Agent name (uses default if not specified)")
    String agentName;

    @Option(names = {"-m", "--mode"}, description = "Deploy mode: 'local' or 'cloud'", defaultValue = "cloud")
    String mode;

    @Option(names = {"-t", "--tag"}, description = "Docker image tag", defaultValue = "latest")
    String imageTag;

    @Option(names = {"-l", "--local-port"}, description = "Local port mapping (for local mode)")
    Integer localPort;

    @Option(names = "--swr-org", description = "SWR organization (overrides config)")
    String swrOrg;

    @Option(names = "--swr-repo", description = "SWR repository (overrides config)")
    String swrRepo;

    @Option(names = {"-d", "--description"}, description = "Agent description")
    String description;

    @Option(names = "--skip-build", description = "Skip build/push, use config URL directly")
    boolean skipBuild;

    @Option(names = {"-k", "--skip-ssl-verification"}, description = "Skip SSL certificate verification")
    boolean skipSsl;

    @Option(names = "--platform", description = "Docker build platform (e.g. linux/amd64, linux/arm64)",
            defaultValue = "linux/arm64")
    String platform;

    @Override
    public void run() {
        if (!"cloud".equals(mode) && !"local".equals(mode)) {
            CliSupport.fail("mode must be 'cloud' or 'local'");
            return;
        }
        try {
            boolean ok = DeployOperation.deployProject(agentName, mode, imageTag, localPort,
                    swrOrg, swrRepo, description, skipBuild, skipSsl, platform);
            if (!ok) {
                CliSupport.fail("deploy did not complete successfully");
            }
        } catch (Exception e) {
            CliSupport.fail("Error deploying: " + e.getMessage());
        }
    }
}
