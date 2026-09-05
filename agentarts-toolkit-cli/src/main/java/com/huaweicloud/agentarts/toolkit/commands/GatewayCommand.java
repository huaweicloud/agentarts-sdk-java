package com.huaweicloud.agentarts.toolkit.commands;

import com.huaweicloud.agentarts.sdk.gateway.GatewayClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Map;

/**
 * Gateway management subcommand group.
 *
 * <p>CLI command: manage gateways and targets. Each subcommand builds an
 * {@link GatewayClient} (AK/SK read from the environment via {@code Constants})
 * and prints the API result as JSON to stdout.</p>
 */
@Command(
    name = "gateway",
    mixinStandardHelpOptions = true,
    description = "Gateway management",
    subcommands = {
        GatewayCommand.CreateGateway.class,
        GatewayCommand.UpdateGateway.class,
        GatewayCommand.DeleteGateway.class,
        GatewayCommand.GetGateway.class,
        GatewayCommand.ListGateways.class,
        GatewayCommand.CreateTarget.class,
        GatewayCommand.UpdateTarget.class,
        GatewayCommand.DeleteTarget.class,
        GatewayCommand.GetTarget.class,
        GatewayCommand.ListTargets.class
    }
)
public class GatewayCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use 'agentarts gateway --help' for available subcommands.");
    }

    @Command(name = "create-gateway", aliases = {"create"}, mixinStandardHelpOptions = true, description = "Create a gateway")
    static class CreateGateway implements Runnable {
        @Option(names = {"-n", "--name"}, description = "Gateway name") String name;
        @Option(names = {"-d", "--description"}, description = "Gateway description") String description;
        @Option(names = "--protocol-type", description = "Protocol type", defaultValue = "mcp") String protocolType;
        @Option(names = "--authorizer-type", description = "Authorizer type", defaultValue = "iam") String authorizerType;
        @Option(names = "--agency-name", description = "Agency name") String agencyName;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.createGateway(
                        name, description, protocolType, authorizerType, agencyName);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error creating gateway (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                CliSupport.printJson(result.getData());
            } catch (com.huaweicloud.agentarts.sdk.core.APIException e) {
                CliSupport.fail("Error creating gateway (HTTP "
                        + e.getStatusCode() + "): " + e.getMessage());
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error creating gateway: " + e.getMessage());
            }
        }
    }

    @Command(name = "update-gateway", aliases = {"update"}, mixinStandardHelpOptions = true, description = "Update a gateway")
    static class UpdateGateway implements Runnable {
        @Parameters(index = "0", description = "Gateway ID") String gatewayId;
        @Option(names = {"-d", "--description"}) String description;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.updateGateway(gatewayId, description);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error updating gateway (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                CliSupport.printJson(result.getData());
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error updating gateway: " + e.getMessage());
            }
        }
    }

    @Command(name = "delete-gateway", aliases = {"delete"}, mixinStandardHelpOptions = true, description = "Delete a gateway")
    static class DeleteGateway implements Runnable {
        @Parameters(index = "0", description = "Gateway ID") String gatewayId;
        @Option(names = {"-f", "--force"}, description = "Delete without confirmation") boolean force;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            if (!CliSupport.confirmDestructiveAction("delete gateway '" + gatewayId + "'", force)) {
                return;
            }
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.deleteGateway(gatewayId);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error deleting gateway (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                if (result.getData() != null) {
                    CliSupport.printJson(result.getData());
                } else {
                    System.out.println("Gateway deleted successfully: " + gatewayId);
                }
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error deleting gateway: " + e.getMessage());
            }
        }
    }

    @Command(name = "get-gateway", aliases = {"get"}, mixinStandardHelpOptions = true, description = "Get gateway details")
    static class GetGateway implements Runnable {
        @Parameters(index = "0", description = "Gateway ID") String gatewayId;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.getGateway(gatewayId);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error getting gateway (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                CliSupport.printJson(result.getData());
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error getting gateway: " + e.getMessage());
            }
        }
    }

    @Command(name = "list-gateways", aliases = {"list"}, mixinStandardHelpOptions = true, description = "List gateways")
    static class ListGateways implements Runnable {
        @Option(names = "--name") String name;
        @Option(names = "--status") String status;
        @Option(names = "--gateway-id") String gatewayId;
        @Option(names = "--limit") Integer limit;
        @Option(names = "--offset") Integer offset;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.listGateways(
                        name, status, gatewayId, null, null, null, null, limit, offset);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error listing gateways (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                CliSupport.printJson(result.getData());
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error listing gateways: " + e.getMessage());
            }
        }
    }

    @Command(name = "create-gateway-target", aliases = {"create-target"}, mixinStandardHelpOptions = true, description = "Create a gateway target")
    static class CreateTarget implements Runnable {
        @Parameters(index = "0", description = "Gateway ID") String gatewayId;
        @Option(names = {"-n", "--name"}) String name;
        @Option(names = {"-d", "--description"}) String description;
        @Option(names = "--target-configuration", required = true) String targetConfig;
        @Option(names = "--credential-provider-configuration") String credProviderConfig;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            Map<String, Object> targetConfigMap = CliSupport.parseJsonMap(targetConfig, "target-configuration");
            Map<String, Object> credConfigMap = CliSupport.parseJsonMap(credProviderConfig, "credential-provider-configuration");
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.createGatewayTarget(
                        gatewayId, name, description, targetConfigMap, credConfigMap);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error creating gateway target (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                CliSupport.printJson(result.getData());
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error creating gateway target: " + e.getMessage());
            }
        }
    }

    @Command(name = "update-gateway-target", aliases = {"update-target"}, mixinStandardHelpOptions = true, description = "Update a gateway target")
    static class UpdateTarget implements Runnable {
        @Parameters(index = "0", description = "Gateway ID") String gatewayId;
        @Parameters(index = "1", description = "Target ID") String targetId;
        @Option(names = {"-n", "--name"}) String name;
        @Option(names = {"-d", "--description"}) String description;
        @Option(names = "--target-configuration") String targetConfig;
        @Option(names = "--credential-provider-configuration") String credProviderConfig;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            Map<String, Object> targetConfigMap = CliSupport.parseJsonMap(targetConfig, "target-configuration");
            Map<String, Object> credConfigMap = CliSupport.parseJsonMap(credProviderConfig, "credential-provider-configuration");
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.updateGatewayTarget(
                        gatewayId, targetId, name, description, targetConfigMap, credConfigMap);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error updating gateway target (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                CliSupport.printJson(result.getData());
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error updating gateway target: " + e.getMessage());
            }
        }
    }

    @Command(name = "delete-gateway-target", aliases = {"delete-target"}, mixinStandardHelpOptions = true, description = "Delete a gateway target")
    static class DeleteTarget implements Runnable {
        @Parameters(index = "0", description = "Gateway ID") String gatewayId;
        @Parameters(index = "1", description = "Target ID") String targetId;
        @Option(names = {"-f", "--force"}, description = "Delete without confirmation") boolean force;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            if (!CliSupport.confirmDestructiveAction("delete gateway target '" + targetId + "'", force)) {
                return;
            }
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.deleteGatewayTarget(gatewayId, targetId);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error deleting gateway target (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                if (result.getData() != null) {
                    CliSupport.printJson(result.getData());
                } else {
                    System.out.println("Target deleted successfully: " + targetId);
                }
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error deleting gateway target: " + e.getMessage());
            }
        }
    }

    @Command(name = "get-gateway-target", aliases = {"get-target"}, mixinStandardHelpOptions = true, description = "Get gateway target details")
    static class GetTarget implements Runnable {
        @Parameters(index = "0", description = "Gateway ID") String gatewayId;
        @Parameters(index = "1", description = "Target ID") String targetId;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.getGatewayTarget(gatewayId, targetId);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error getting gateway target (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                CliSupport.printJson(result.getData());
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error getting gateway target: " + e.getMessage());
            }
        }
    }

    @Command(name = "list-gateway-targets", aliases = {"list-targets"}, mixinStandardHelpOptions = true, description = "List gateway targets")
    static class ListTargets implements Runnable {
        @Parameters(index = "0", description = "Gateway ID") String gatewayId;
        @Option(names = "--limit") Integer limit;
        @Option(names = "--offset") Integer offset;
        @Option(names = {"-k", "--skip-ssl-verification"}) boolean skipSsl;

        @Override public void run() {
            try (GatewayClient client = new GatewayClient(!skipSsl)) {
                RequestResult result = client.listGatewayTargets(gatewayId, limit, offset);
                if (!result.isSuccess()) {
                    CliSupport.fail("Error listing gateway targets (HTTP "
                            + result.getStatusCode() + "): " + result.getError());
                }
                CliSupport.printJson(result.getData());
            } catch (Exception e) {
                if (e instanceof CliSupport.CliFailure) throw (CliSupport.CliFailure) e;
                CliSupport.fail("Error listing gateway targets: " + e.getMessage());
            }
        }
    }
}
