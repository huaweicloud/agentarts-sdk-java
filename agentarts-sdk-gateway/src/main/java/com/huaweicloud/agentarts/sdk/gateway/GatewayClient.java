package com.huaweicloud.agentarts.sdk.gateway;

import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.SignMode;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.core.util.UrlUtils;
import com.huaweicloud.agentarts.sdk.gateway.model.CreateGatewayRequest;
import com.huaweicloud.agentarts.sdk.gateway.model.CreateGatewayTargetRequest;
import com.huaweicloud.agentarts.sdk.gateway.model.UpdateGatewayRequest;
import com.huaweicloud.agentarts.sdk.gateway.model.UpdateGatewayTargetRequest;
import com.huaweicloud.agentarts.sdk.service.http.BaseHttpClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestConfig;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import com.huaweicloud.agentarts.sdk.service.auth.CredentialProviders;
import com.huaweicloud.sdk.core.ClientBuilder;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.ICredentialProvider;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.iam.v5.IamClient;
import com.huaweicloud.sdk.iam.v5.model.Agency;
import com.huaweicloud.sdk.iam.v5.model.AttachAgencyPolicyReqBody;
import com.huaweicloud.sdk.iam.v5.model.AttachAgencyPolicyV5Request;
import com.huaweicloud.sdk.iam.v5.model.CreateAgencyReqBody;
import com.huaweicloud.sdk.iam.v5.model.CreateAgencyV5Request;
import com.huaweicloud.sdk.iam.v5.model.CreateAgencyV5Response;
import com.huaweicloud.sdk.iam.v5.model.ListAgenciesV5Request;
import com.huaweicloud.sdk.iam.v5.model.ListAgenciesV5Response;
import com.huaweicloud.sdk.iam.v5.model.ListPoliciesV5Request;
import com.huaweicloud.sdk.iam.v5.model.ListPoliciesV5Response;
import com.huaweicloud.sdk.iam.v5.model.PageInfo;
import com.huaweicloud.sdk.iam.v5.model.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Gateway client for managing gateways and targets via AK/SK signed requests.
 * Always uses AK/SK signing (SDK-HMAC-SHA256). Base URL is control plane + /v1/core.
 *
 * <p>When creating a gateway with no explicit {@code agencyName}, an IAM agency
 * named {@value #DEFAULT_AGENCY_NAME} is
 * automatically created (or reused if it already exists).</p>
 *
 * <h2>Gateway management:</h2>
 * create/update/delete/get/list_gateway
 *
 * <h2>Target management:</h2>
 * create/update/delete/get/list_gateway_target
 */
public class GatewayClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayClient.class);

    /** Default IAM agency name for Gateway. */
    public static final String DEFAULT_AGENCY_NAME = "AgentArtsCoreGateway";

    /** System policy attached to the auto-created agency so it can fetch resource credentials. */
    public static final String IDENTITY_AGENCY_POLICY_NAME = "AgentArtsCoreGatewayIdentityAgencyPolicy";

    /**
     * Build the IAM trust policy JSON string granting the AgentArts sandbox service
     * principal permission to assume the agency. Matches the reference implementation's
     * {@code sts:agencies:assume} trust policy (not resource actions).
     */
    private static String buildTrustPolicy() {
        // Trust policy: allow service.WorkloadSandboxMetadata to assume the agency.
        Map<String, Object> statement = new HashMap<>();
        statement.put("Action", List.of("sts:agencies:assume"));
        statement.put("Effect", "Allow");
        statement.put("Principal", Map.of("Service", List.of("service.WorkloadSandboxMetadata")));
        Map<String, Object> trustPolicy = new HashMap<>();
        trustPolicy.put("Version", "5.0");
        trustPolicy.put("Statement", List.of(statement));
        try {
            return JsonUtils.MAPPER.writeValueAsString(trustPolicy);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize IAM trust policy", e);
        }
    }

    private final BaseHttpClient httpClient;
    private final boolean verifySsl;
    private final ICredentialProvider credentialProvider;
    private final Supplier<IamClient> iamClientFactory;

    public GatewayClient(boolean verifySsl) {
        this(verifySsl, CredentialProviders.defaultBasicProvider());
    }

    /**
     * Create a client with an explicit credential provider.
     */
    public GatewayClient(boolean verifySsl, ICredentialProvider credentialProvider) {
        this(verifySsl, credentialProvider, null);
    }

    GatewayClient(boolean verifySsl, ICredentialProvider credentialProvider,
                     BaseHttpClient httpClient) {
        this(verifySsl, credentialProvider, httpClient, null);
    }

    GatewayClient(boolean verifySsl, ICredentialProvider credentialProvider,
                     BaseHttpClient httpClient, Supplier<IamClient> iamClientFactory) {
        this.verifySsl = verifySsl;
        this.credentialProvider = java.util.Objects.requireNonNull(
                credentialProvider, "credentialProvider must not be null");
        this.iamClientFactory = iamClientFactory != null ? iamClientFactory : this::buildIamClient;
        if (httpClient != null) {
            this.httpClient = httpClient;
        } else {
            String endpoint = Constants.getControlPlaneEndpoint() + "/v1/core";
            RequestConfig config = RequestConfig.builder()
                    .baseUrl(endpoint)
                    .verifySsl(verifySsl)
                    .build();
            this.httpClient = new BaseHttpClient(
                    config, true, SignMode.SDK_HMAC_SHA256, Constants.getRegion(), credentialProvider);
        }
    }

    public GatewayClient() {
        this(true);
    }

    // ========================
    // Gateway CRUD
    // ========================

    /**
     * Create a gateway with all parameters.
     *
     * <p>When {@code agencyName} is null, an IAM agency named
     * {@value #DEFAULT_AGENCY_NAME} is automatically created
     * (or reused if it already exists), matching the Python SDK behavior.</p>
     */
    public RequestResult createGateway(String name, String description,
                                           String protocolType, String authorizerType,
                                           String agencyName) {
        return createGateway(name, description, protocolType, authorizerType, agencyName,
                null, null, null, null, null);
    }

    /**
     * Create a gateway with the complete set of options supported by the service.
     */
    public RequestResult createGateway(
            String name,
            String description,
            String protocolType,
            String authorizerType,
            String agencyName,
            Map<String, Object> authorizerConfiguration,
            Map<String, Object> protocolConfiguration,
            Map<String, Object> logDeliveryConfiguration,
            Map<String, Object> outboundNetworkConfiguration,
            List<Map<String, String>> tags) {
        String resolvedName = name != null ? name : randomName("gateway-");
        String protocol = protocolType != null ? protocolType : "mcp";
        String authorizer = authorizerType != null ? authorizerType : "iam";

        // The gateway uses this agency for resource credentials independently of
        // its inbound authorizer, matching the reference SDK's behavior.
        String resolvedAgencyName = agencyName;
        if (agencyName == null) {
            resolvedAgencyName = ensureIamAgency();
        }

        CreateGatewayRequest req = new CreateGatewayRequest()
                .withName(resolvedName)
                .withDescription(description)
                .withProtocolType(protocol)
                .withAuthorizerType(authorizer)
                .withAgencyName(resolvedAgencyName)
                .withAuthorizerConfiguration(authorizerConfiguration)
                .withProtocolConfiguration(protocolConfiguration)
                .withLogDeliveryConfiguration(logDeliveryConfiguration != null
                        ? logDeliveryConfiguration : Map.of("enabled", false))
                .withOutboundNetworkConfiguration(outboundNetworkConfiguration != null
                        ? outboundNetworkConfiguration : Map.of("network_mode", "public"))
                .withTags(tags);
        return httpClient.post("/gateways", null, req).block();
    }

    public RequestResult createGateway(String name, String description) {
        return createGateway(name, description, "mcp", "iam", null);
    }

    public RequestResult updateGateway(String gatewayId, String description) {
        return updateGateway(gatewayId, description, null, null, null);
    }

    /**
     * Update a gateway. At least one updateable field must be supplied.
     */
    public RequestResult updateGateway(
            String gatewayId,
            String description,
            Map<String, Object> protocolConfiguration,
            Map<String, Object> logDeliveryConfiguration,
            List<Map<String, String>> tags) {
        if (description == null && protocolConfiguration == null
                && logDeliveryConfiguration == null && tags == null) {
            throw new IllegalArgumentException("At least one gateway field must be provided for update");
        }
        UpdateGatewayRequest req = new UpdateGatewayRequest()
                .withDescription(description)
                .withProtocolConfiguration(protocolConfiguration)
                .withLogDeliveryConfiguration(logDeliveryConfiguration)
                .withTags(tags);
        return httpClient.put(
                "/gateways/" + UrlUtils.encodePathSegment(gatewayId, "gatewayId"), null, req).block();
    }

    public RequestResult deleteGateway(String gatewayId) {
        return httpClient.delete(
                "/gateways/" + UrlUtils.encodePathSegment(gatewayId, "gatewayId")).block();
    }

    public RequestResult getGateway(String gatewayId) {
        return httpClient.get(
                "/gateways/" + UrlUtils.encodePathSegment(gatewayId, "gatewayId")).block();
    }

    public RequestResult listGateways(String name, Integer limit, Integer offset) {
        return listGateways(name, null, null, null, null, null, null, limit, offset);
    }

    /**
     * List gateways using the complete set of service-side filters.
     */
    public RequestResult listGateways(
            String name,
            String status,
            String gatewayId,
            List<String> tagKeyExists,
            List<String> tagKeyMatches,
            List<String> tagValueMatches,
            String tagMatchPolicy,
            Integer limit,
            Integer offset) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        if (name != null) query.put("name", List.of(name));
        if (status != null) query.put("status", List.of(status));
        if (gatewayId != null) query.put("gateway_id", List.of(gatewayId));
        if (tagKeyExists != null) query.put("tag_key_exists", List.copyOf(tagKeyExists));
        if (tagKeyMatches != null) query.put("tag_key_matches", List.copyOf(tagKeyMatches));
        if (tagValueMatches != null) query.put("tag_value_matches", List.copyOf(tagValueMatches));
        if (tagMatchPolicy != null) query.put("tag_match_policy", List.of(tagMatchPolicy));
        if (limit != null) query.put("limit", List.of(String.valueOf(limit)));
        if (offset != null) query.put("offset", List.of(String.valueOf(offset)));
        return httpClient.request("GET", "/gateways", null, null, query).block();
    }

    public RequestResult listGateways() {
        return listGateways(null, null, null);
    }

    // ========================
    // Target CRUD
    // ========================

    public RequestResult createGatewayTarget(String gatewayId, String name,
                                                  String description,
                                                  Map<String, Object> targetConfiguration,
                                                  Map<String, Object> credentialProviderConfiguration) {
        // Default credential_provider_configuration to {"credential_provider_type": "none"}
        // when not provided, matching the reference implementation (the backend rejects a
        // target with neither a target_configuration nor a credential provider).
        Map<String, Object> credConfig = credentialProviderConfiguration != null
                ? credentialProviderConfiguration
                : Map.of("credential_provider_type", "none");
        CreateGatewayTargetRequest req = new CreateGatewayTargetRequest()
                .withName(name != null ? name : randomName("target-"))
                .withDescription(description)
                .withTargetConfiguration(targetConfiguration)
                .withCredentialProviderConfiguration(credConfig);
        return httpClient.post(
                "/gateways/" + UrlUtils.encodePathSegment(gatewayId, "gatewayId")
                        + "/targets", null, req).block();
    }

    public RequestResult createGatewayTarget(String gatewayId, String name, String description) {
        return createGatewayTarget(gatewayId, name, description, null, null);
    }

    public RequestResult updateGatewayTarget(String gatewayId, String targetId,
                                                   String name, String description,
                                                   Map<String, Object> targetConfiguration,
                                                   Map<String, Object> credentialProviderConfiguration) {
        if (name == null && description == null && targetConfiguration == null
                && credentialProviderConfiguration == null) {
            throw new IllegalArgumentException("At least one gateway target field must be provided for update");
        }
        UpdateGatewayTargetRequest req = new UpdateGatewayTargetRequest()
                .withName(name)
                .withDescription(description)
                .withTargetConfiguration(targetConfiguration)
                .withCredentialProviderConfiguration(credentialProviderConfiguration);
        return httpClient.put(
                "/gateways/" + UrlUtils.encodePathSegment(gatewayId, "gatewayId")
                        + "/targets/" + UrlUtils.encodePathSegment(targetId, "targetId"), null, req).block();
    }

    public RequestResult deleteGatewayTarget(String gatewayId, String targetId) {
        return httpClient.delete(
                "/gateways/" + UrlUtils.encodePathSegment(gatewayId, "gatewayId")
                        + "/targets/" + UrlUtils.encodePathSegment(targetId, "targetId")).block();
    }

    public RequestResult getGatewayTarget(String gatewayId, String targetId) {
        return httpClient.get(
                "/gateways/" + UrlUtils.encodePathSegment(gatewayId, "gatewayId")
                        + "/targets/" + UrlUtils.encodePathSegment(targetId, "targetId")).block();
    }

    public RequestResult listGatewayTargets(String gatewayId, Integer limit, Integer offset) {
        Map<String, List<String>> query = new HashMap<>();
        if (limit != null) query.put("limit", List.of(String.valueOf(limit)));
        if (offset != null) query.put("offset", List.of(String.valueOf(offset)));
        return httpClient.request(
                "GET", "/gateways/" + UrlUtils.encodePathSegment(gatewayId, "gatewayId")
                        + "/targets", null, null, query).block();
    }

    public RequestResult listGatewayTargets(String gatewayId) {
        return listGatewayTargets(gatewayId, null, null);
    }

    @Override
    public void close() {
        httpClient.close();
    }

    // ========================
    // Internal helpers
    // ========================

    private static String randomName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * Ensure the default IAM agency exists with the identity policy attached,
     * mirroring the reference {@code create_agency_with_policy} flow: create the
     * agency (or reuse it on 409), look up the system policy by name, attach it
     * (ignore 409 if already attached). Returns the agency name; the agency is
     * left in place (shared across gateways, intentionally not deleted).
     */
    private String ensureIamAgency() {
        try {
            IamClient iamClient = iamClientFactory.get();
            String agencyId = resolveAgencyId(iamClient);
            if (agencyId == null) {
                throw new IllegalStateException(
                        "IAM agency was not returned and could not be resolved: " + DEFAULT_AGENCY_NAME);
            }
            attachIdentityPolicy(iamClient, agencyId);
        } catch (Exception e) {
            if (isConflict(e)) {
                LOG.debug("IAM agency {} already exists or policy already attached",
                        DEFAULT_AGENCY_NAME);
            } else {
                throw new IllegalStateException(
                        "Failed to ensure IAM agency " + DEFAULT_AGENCY_NAME, e);
            }
        }
        return DEFAULT_AGENCY_NAME;
    }

    /** True if the exception is a 409 Conflict (agency/policy already exists). */
    private static boolean isConflict(Exception e) {
        if (e instanceof com.huaweicloud.sdk.core.exception.ServiceResponseException) {
            return ((com.huaweicloud.sdk.core.exception.ServiceResponseException) e)
                    .getHttpStatusCode() == 409;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("409") || msg.contains("already exists");
    }

    private IamClient buildIamClient() {
        // BasicCredentials (not GlobalCredentials) — the IAM v5 agency API requires it.
        BasicCredentials credentials = CredentialProviders.resolveBasic(credentialProvider);
        HttpConfig httpConfig = HttpConfig.getDefaultHttpConfig();
        httpConfig.setIgnoreSSLVerification(!verifySsl);
        String endpoint = Constants.getIamEndpoint(Constants.getRegion());
        return new ClientBuilder<>(IamClient::new)
                .withCredential(credentials)
                .withHttpConfig(httpConfig)
                .withEndpoint(endpoint)
                .build();
    }

    /**
     * Create the agency, or — on 409 (already exists) — look it up by name.
     * Returns the agency id, or null if it cannot be resolved.
     */
    private String resolveAgencyId(IamClient iamClient) {
        CreateAgencyReqBody body = new CreateAgencyReqBody()
                .withAgencyName(DEFAULT_AGENCY_NAME)
                .withTrustPolicy(buildTrustPolicy());
        CreateAgencyV5Request request = new CreateAgencyV5Request().withBody(body);
        try {
            CreateAgencyV5Response response = iamClient.createAgencyV5(request);
            // CreateAgencyV5Response nests the agency under .agency (the bc280d3 fix:
            // the create call succeeds but agency_id is on the nested agency object).
            if (response != null && response.getAgency() != null) {
                LOG.info("Created IAM agency: {}", DEFAULT_AGENCY_NAME);
                return response.getAgency().getAgencyId();
            }
            return null;
        } catch (Exception e) {
            if (!isConflict(e)) {
                throw e;
            }
            // Agency already exists — find its id via list_agencies.
            LOG.debug("IAM agency {} already exists; resolving id", DEFAULT_AGENCY_NAME);
            String marker = null;
            Set<String> visitedMarkers = new HashSet<>();
            while (true) {
                ListAgenciesV5Request listReq = new ListAgenciesV5Request().withLimit(200);
                if (marker != null) {
                    listReq.withMarker(marker);
                }
                ListAgenciesV5Response listResp = iamClient.listAgenciesV5(listReq);
                if (listResp == null || listResp.getAgencies() == null) {
                    return null;
                }
                for (Agency agency : listResp.getAgencies()) {
                    if (DEFAULT_AGENCY_NAME.equals(agency.getAgencyName())) {
                        return agency.getAgencyId();
                    }
                }
                marker = nextMarker(listResp.getPageInfo(), visitedMarkers);
                if (marker == null) {
                    return null;
                }
            }
        }
    }

    /**
     * Find the system identity policy by name (paged) and attach it to the agency.
     * A 409 on attach means it is already attached and is ignored.
     */
    private void attachIdentityPolicy(IamClient iamClient, String agencyId) {
        String policyId = findSystemPolicyId(iamClient, IDENTITY_AGENCY_POLICY_NAME);
        if (policyId == null) {
            throw new IllegalStateException(
                    "Required IAM system policy was not found: " + IDENTITY_AGENCY_POLICY_NAME);
        }
        try {
            AttachAgencyPolicyV5Request attachReq = new AttachAgencyPolicyV5Request()
                    .withPolicyId(policyId)
                    .withBody(new AttachAgencyPolicyReqBody().withAgencyId(agencyId));
            iamClient.attachAgencyPolicyV5(attachReq);
            LOG.info("Attached policy {} to agency {}", IDENTITY_AGENCY_POLICY_NAME, DEFAULT_AGENCY_NAME);
        } catch (Exception e) {
            if (isConflict(e)) {
                LOG.debug("Policy {} already attached to agency {}",
                        IDENTITY_AGENCY_POLICY_NAME, DEFAULT_AGENCY_NAME);
            } else {
                throw e;
            }
        }
    }

    private String findSystemPolicyId(IamClient iamClient, String policyName) {
        String marker = null;
        Set<String> visitedMarkers = new HashSet<>();
        while (true) {
            ListPoliciesV5Request req = new ListPoliciesV5Request()
                    .withPolicyType(ListPoliciesV5Request.PolicyTypeEnum.SYSTEM)
                    .withLimit(200);
            if (marker != null) {
                req.withMarker(marker);
            }
            ListPoliciesV5Response resp = iamClient.listPoliciesV5(req);
            if (resp == null || resp.getPolicies() == null) {
                return null;
            }
            for (Policy policy : resp.getPolicies()) {
                if (policyName.equals(policy.getPolicyName())) {
                    return policy.getPolicyId();
                }
            }
            marker = nextMarker(resp.getPageInfo(), visitedMarkers);
            if (marker == null) {
                return null;
            }
        }
    }

    private static String nextMarker(PageInfo pageInfo, Set<String> visitedMarkers) {
        if (pageInfo == null || !JsonUtils.isNotBlank(pageInfo.getNextMarker())) {
            return null;
        }
        String marker = pageInfo.getNextMarker();
        if (!visitedMarkers.add(marker)) {
            throw new IllegalStateException("IAM pagination returned a repeated marker");
        }
        return marker;
    }
}
