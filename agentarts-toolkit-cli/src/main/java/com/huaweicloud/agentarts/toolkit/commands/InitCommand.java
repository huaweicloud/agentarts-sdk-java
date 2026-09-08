package com.huaweicloud.agentarts.toolkit.commands;

import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.toolkit.operations.InitOperation;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Initialize a new AgentArts project.
 *
 * <p>CLI command: initialize a new AgentArts project. When {@code --name} is
 * omitted and a console is attached, the command prompts interactively for the
 * project name with a generated default ({@code myagent-xxxxx}); pressing
 * Enter (or EOF) accepts the default. The same applies to {@code --region},
 * which defaults to {@code cn-southwest-2}. In a non-interactive context
 * (piped input, CI, IDE test harness) a missing {@code --name} errors with
 * exit code 2 and {@code --region} falls back to its default.</p>
 */
@Command(name = "init", mixinStandardHelpOptions = true,
        description = "Initialize a new AgentArts project")
public class InitCommand implements Callable<Integer> {

    @Option(names = {"-n", "--name"}, description = "Project name")
    String name;

    @Option(names = {"-t", "--template"}, description = "Project template: basic, agentscope")
    String template;

    @Option(names = {"-p", "--path"}, description = "Project path", defaultValue = ".")
    String path;

    @Option(names = {"-r", "--region"}, description = "Huawei Cloud region")
    String region;

    @Option(names = "--swr-org", description = "SWR organization")
    String swrOrg;

    @Option(names = "--swr-repo", description = "SWR repository")
    String swrRepo;

    @Override
    public Integer call() {
        // --name is the only field without a default. When it is omitted the
        // command is "interactive init": prompt on the console for the project
        // name (matching the documented `agentarts init` flow). If no console
        // is attached (piped input, CI, IDE test harness), fall back to a hard
        // error so the caller still gets a non-zero exit and no half-created
        // project.
        if (name == null || name.isEmpty()) {
            java.io.Console console = System.console();
            if (console == null) {
                System.err.println("Error: --name is required (no interactive console; pass -n <name>)");
                return 2;
            }
            // Prompt with a generated default (myagent-xxxxx); empty input or
            // EOF (Ctrl-D) accepts the default, matching the Python SDK.
            String defaultName = InitOperation.generateDefaultAgentName();
            name = console.readLine("Enter project name [%s]: ", defaultName);
            if (name == null || name.isBlank()) {
                name = defaultName;
            }
            name = name.trim();
        }
        // Validate name: lowercase letters, digits, hyphens
        String normalized = name.toLowerCase();
        if (!normalized.matches("[a-z0-9][a-z0-9-]{0,62}")) {
            System.err.println("Error: name must be 1-63 lowercase letters, digits, or hyphens "
                    + "and start with a letter or digit");
            return 2;
        }
        name = normalized;
        if (template == null) template = "basic";
        // --region: prompt with the default when omitted and a console is
        // attached; otherwise (CI, piped input) fall back to the default.
        if (region == null || region.isEmpty()) {
            String defaultRegion = Constants.DEFAULT_REGION;
            java.io.Console console = System.console();
            if (console != null) {
                region = console.readLine("Region [%s]: ", defaultRegion);
                if (region == null || region.isBlank()) {
                    region = defaultRegion;
                }
                region = region.trim();
            } else {
                region = defaultRegion;
            }
        }

        try {
            InitOperation.initProject(template, name, path, region, swrOrg, swrRepo);
            // Resolve the display path to the actual project directory: relative
            // inputs (e.g. ".") are shown as their absolute form so the message
            // points at the real location of the created files.
            Path projectDir = Paths.get(path).toAbsolutePath().normalize().resolve(name);
            System.out.println("Project '" + name + "' initialized successfully at " + projectDir);
            System.out.println();
            System.out.println("Project structure:");
            System.out.println("  " + name + "/");
            System.out.println("  " + "├── .agentarts_config.yaml           # Configuration");
            System.out.println("  " + "├── Dockerfile                       # Docker build file");
            System.out.println("  " + "├── pom.xml                          # Maven build file");
            System.out.println("  " + "└── src/main/java/com/example/       # Agent entry class");
            System.out.println();
            System.out.println("Next Steps - How to Deploy Agent");
            System.out.println();
            System.out.println("Step 1: Navigate to project directory");
            System.out.println("    cd " + name);
            System.out.println();
            System.out.println("Step 2: Build the project");
            System.out.println("    mvn compile");
            System.out.println();
            System.out.println("Step 3: Edit src/main/java/com/example/Agent.java to implement your agent logic");
            System.out.println();
            System.out.println("Step 4: Edit .agentarts_config.yaml and run config command (optional)");
            System.out.println("    Modify configuration as needed, then:");
            System.out.println("    agentarts config");
            System.out.println();
            System.out.println("Step 5: Deploy to Huawei Cloud");
            System.out.println("    agentarts deploy");
            return 0;
        } catch (Exception e) {
            System.err.println("Error initializing project: " + e.getMessage());
            return 1;
        }
    }
}
