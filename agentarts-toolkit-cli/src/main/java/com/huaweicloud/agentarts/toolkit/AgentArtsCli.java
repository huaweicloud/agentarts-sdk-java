package com.huaweicloud.agentarts.toolkit;

import com.huaweicloud.agentarts.toolkit.commands.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * AgentArts CLI toolkit — Picocli-based command-line interface.
 *
 * <p>Provides commands for initializing, developing, deploying, invoking,
 * and managing AI agents on Huawei Cloud AgentArts.</p>
 *
 * <h2>Command tree:</h2>
 * <pre>
 * agentarts
 *   init          — Initialize a new project
 *   config        — Configuration management (alias: configure)
 *   dev           — Run local development server
 *   deploy        — Deploy agent to cloud (alias: launch)
 *   invoke        — Invoke agent with JSON payload
 *   destroy       — Destroy agent from cloud
 *   runtime       — Cloud runtime operations (invoke, exec-command, upload-files, download-files, start-session, stop-session)
 *   gateway   — Gateway management
 *   memory        — Memory space management
 * </pre>
 */
@Command(
    name = "agentarts",
    mixinStandardHelpOptions = true,
    version = "AgentArts CLI 0.1.0",
    description = "Build, deploy and manage AI agents with Huawei Cloud capabilities.",
    subcommands = {
        InitCommand.class,
        ConfigCommand.class,
        DevCommand.class,
        DeployCommand.class,
        InvokeCommand.class,
        DestroyCommand.class,
        RuntimeCommand.class,
        GatewayCommand.class,
        MemoryCommand.class
    }
)
public class AgentArtsCli implements Runnable {

    @Option(names = "--verbose", description = "Enable verbose logging (DEBUG level)")
    boolean verbose;

    @Override
    public void run() {
        // If no subcommand, print welcome banner
        System.out.println("AgentArts CLI — Build, deploy and manage AI agents.");
        System.out.println("Run 'agentarts --help' for available commands.");
    }

    public static void main(String[] args) {
        CommandLine cli = CliSupport.withCleanExit(new CommandLine(new AgentArtsCli()));
        int exitCode = cli.execute(args);
        System.exit(exitCode);
    }
}
