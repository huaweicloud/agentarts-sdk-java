package com.huaweicloud.agentarts.sdk.gateway;

import com.huaweicloud.agentarts.sdk.gateway.model.CreateGatewayRequest;
import com.huaweicloud.agentarts.sdk.gateway.model.CreateGatewayTargetRequest;
import com.huaweicloud.agentarts.sdk.gateway.model.UpdateGatewayRequest;
import com.huaweicloud.agentarts.sdk.gateway.model.UpdateGatewayTargetRequest;
import com.huaweicloud.agentarts.sdk.service.http.BaseHttpClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import com.huaweicloud.sdk.core.auth.ICredentialProvider;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;
import com.huaweicloud.sdk.iam.v5.IamClient;
import com.huaweicloud.sdk.iam.v5.model.Agency;
import com.huaweicloud.sdk.iam.v5.model.ListAgenciesV5Response;
import com.huaweicloud.sdk.iam.v5.model.PageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GatewayClientTest {

    private BaseHttpClient httpClient;
    private GatewayClient client;
    private RequestResult success;

    @BeforeEach
    void setUp() {
        httpClient = mock(BaseHttpClient.class);
        success = RequestResult.builder().success(true).statusCode(200).build();
        client = new GatewayClient(true, mock(ICredentialProvider.class), httpClient);
    }

    @Test
    void executesGatewayCrudWithDefaultsAndQueryParameters() {
        when(httpClient.post(eq("/gateways"), isNull(), any()))
                .thenReturn(Mono.just(success));
        when(httpClient.put(eq("/gateways/gateway"), isNull(), any()))
                .thenReturn(Mono.just(success));
        when(httpClient.get("/gateways/gateway")).thenReturn(Mono.just(success));
        when(httpClient.delete("/gateways/gateway")).thenReturn(Mono.just(success));
        when(httpClient.request(eq("GET"), eq("/gateways"), isNull(), isNull(), anyMap()))
                .thenReturn(Mono.just(success));

        assertSame(success, client.createGateway("name", "description", null, "none", "agency"));
        assertSame(success, client.updateGateway("gateway", "updated"));
        assertSame(success, client.getGateway("gateway"));
        assertSame(success, client.deleteGateway("gateway"));
        assertSame(success, client.listGateways("name", 20, 10));

        var create = ArgumentCaptor.forClass(CreateGatewayRequest.class);
        verify(httpClient).post(eq("/gateways"), isNull(), create.capture());
        assertEquals("name", create.getValue().getName());
        assertEquals("mcp", create.getValue().getProtocolType());
        assertEquals("none", create.getValue().getAuthorizerType());
        assertEquals("agency", create.getValue().getAgencyName());
        assertEquals(Map.of("enabled", false), create.getValue().getLogDeliveryConfiguration());
        assertEquals(Map.of("network_mode", "public"),
                create.getValue().getOutboundNetworkConfiguration());

        var update = ArgumentCaptor.forClass(UpdateGatewayRequest.class);
        verify(httpClient).put(eq("/gateways/gateway"), isNull(), update.capture());
        assertEquals("updated", update.getValue().getDescription());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> query = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).request(eq("GET"), eq("/gateways"), isNull(), isNull(), query.capture());
        assertEquals(List.of("name"), query.getValue().get("name"));
        assertEquals(List.of("20"), query.getValue().get("limit"));
        assertEquals(List.of("10"), query.getValue().get("offset"));
    }

    @Test
    void sendsCompleteGatewayConfigurationAndFilters() {
        when(httpClient.post(eq("/gateways"), isNull(), any())).thenReturn(Mono.just(success));
        when(httpClient.put(eq("/gateways/gateway"), isNull(), any())).thenReturn(Mono.just(success));
        when(httpClient.request(eq("GET"), eq("/gateways"), isNull(), isNull(), anyMap()))
                .thenReturn(Mono.just(success));

        Map<String, Object> authorizer = Map.of("issuer", "issuer.example.test");
        Map<String, Object> protocol = Map.of("version", "2025-06-18");
        Map<String, Object> logging = Map.of("enabled", true);
        Map<String, Object> outbound = Map.of("network_mode", "private");
        List<Map<String, String>> tags = List.of(Map.of("key", "env", "value", "test"));

        assertSame(success, client.createGateway(
                null, "description", "mcp", "none", "agency",
                authorizer, protocol, logging, outbound, tags));
        assertSame(success, client.updateGateway(
                "gateway", null, protocol, logging, tags));
        assertSame(success, client.listGateways(
                "name", "RUNNING", "gateway", List.of("owner"),
                List.of("env"), List.of("test"), "ALL", 25, 5));

        var create = ArgumentCaptor.forClass(CreateGatewayRequest.class);
        verify(httpClient).post(eq("/gateways"), isNull(), create.capture());
        assertTrue(create.getValue().getName().matches("gateway-[0-9a-f]{8}"));
        assertEquals(authorizer, create.getValue().getAuthorizerConfiguration());
        assertEquals(protocol, create.getValue().getProtocolConfiguration());
        assertEquals(logging, create.getValue().getLogDeliveryConfiguration());
        assertEquals(outbound, create.getValue().getOutboundNetworkConfiguration());
        assertEquals(tags, create.getValue().getTags());

        var update = ArgumentCaptor.forClass(UpdateGatewayRequest.class);
        verify(httpClient).put(eq("/gateways/gateway"), isNull(), update.capture());
        assertEquals(protocol, update.getValue().getProtocolConfiguration());
        assertEquals(logging, update.getValue().getLogDeliveryConfiguration());
        assertEquals(tags, update.getValue().getTags());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> query = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).request(eq("GET"), eq("/gateways"), isNull(), isNull(), query.capture());
        assertEquals(List.of("name"), query.getValue().get("name"));
        assertEquals(List.of("RUNNING"), query.getValue().get("status"));
        assertEquals(List.of("gateway"), query.getValue().get("gateway_id"));
        assertEquals(List.of("owner"), query.getValue().get("tag_key_exists"));
        assertEquals(List.of("env"), query.getValue().get("tag_key_matches"));
        assertEquals(List.of("test"), query.getValue().get("tag_value_matches"));
        assertEquals(List.of("ALL"), query.getValue().get("tag_match_policy"));
        assertEquals(List.of("25"), query.getValue().get("limit"));
        assertEquals(List.of("5"), query.getValue().get("offset"));
    }

    @Test
    void executesTargetCrudAndAppliesSafeCredentialProviderDefault() {
        String targets = "/gateways/gateway/targets";
        String target = targets + "/target";
        when(httpClient.post(eq(targets), isNull(), any())).thenReturn(Mono.just(success));
        when(httpClient.put(eq(target), isNull(), any())).thenReturn(Mono.just(success));
        when(httpClient.get(target)).thenReturn(Mono.just(success));
        when(httpClient.delete(target)).thenReturn(Mono.just(success));
        when(httpClient.request(eq("GET"), eq(targets), isNull(), isNull(), anyMap()))
                .thenReturn(Mono.just(success));

        assertSame(success, client.createGatewayTarget(
                "gateway", "name", "description", Map.of("url", "https://example.test"), null));
        assertSame(success, client.updateGatewayTarget(
                "gateway", "target", "updated", "description",
                Map.of("url", "https://updated.example.test"), Map.of("type", "configured")));
        assertSame(success, client.getGatewayTarget("gateway", "target"));
        assertSame(success, client.deleteGatewayTarget("gateway", "target"));
        assertSame(success, client.listGatewayTargets("gateway", 50, 5));

        var create = ArgumentCaptor.forClass(CreateGatewayTargetRequest.class);
        verify(httpClient).post(eq(targets), isNull(), create.capture());
        assertEquals("name", create.getValue().getName());
        assertEquals(Map.of("credential_provider_type", "none"),
                create.getValue().getCredentialProviderConfiguration());

        var update = ArgumentCaptor.forClass(UpdateGatewayTargetRequest.class);
        verify(httpClient).put(eq(target), isNull(), update.capture());
        assertEquals("updated", update.getValue().getName());
        assertEquals(Map.of("type", "configured"),
                update.getValue().getCredentialProviderConfiguration());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> query = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).request(eq("GET"), eq(targets), isNull(), isNull(), query.capture());
        assertEquals(List.of("50"), query.getValue().get("limit"));
        assertEquals(List.of("5"), query.getValue().get("offset"));
    }

    @Test
    void encodesUntrustedGatewayAndTargetIdentifiers() {
        String path = "/gateways/..%2Fgateway/targets/target%3Fx%3D1";
        when(httpClient.get(path)).thenReturn(Mono.just(success));

        assertSame(success, client.getGatewayTarget("../gateway", "target?x=1"));
        verify(httpClient).get(path);
        assertThrows(IllegalArgumentException.class, () -> client.getGateway(" "));
    }

    @Test
    void generatesDefaultTargetNameAndRejectsEmptyUpdates() {
        String targets = "/gateways/gateway/targets";
        when(httpClient.post(eq(targets), isNull(), any())).thenReturn(Mono.just(success));

        assertSame(success, client.createGatewayTarget("gateway", null, null, null, null));

        var create = ArgumentCaptor.forClass(CreateGatewayTargetRequest.class);
        verify(httpClient).post(eq(targets), isNull(), create.capture());
        assertTrue(create.getValue().getName().matches("target-[0-9a-f]{8}"));
        assertEquals(Map.of("credential_provider_type", "none"),
                create.getValue().getCredentialProviderConfiguration());

        assertThrows(IllegalArgumentException.class,
                () -> client.updateGateway("gateway", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> client.updateGatewayTarget("gateway", "target", null, null, null, null));
        verify(httpClient, never()).put(anyString(), any(), any());
    }

    @Test
    void failsClosedWhenAutomaticAgencyProvisioningFails() {
        IamClient iamClient = mock(IamClient.class);
        when(iamClient.createAgencyV5(any())).thenThrow(new IllegalStateException("IAM unavailable"));
        client = new GatewayClient(
                true, mock(ICredentialProvider.class), httpClient, () -> iamClient);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.createGateway("name", "description", "mcp", "iam", null));

        assertTrue(error.getMessage().contains("Failed to ensure IAM agency"));
        verify(httpClient, never()).post(anyString(), any(), any());
    }

    @Test
    void automaticAgencyProvisioningFailsFastOnRepeatedPaginationMarker() {
        IamClient iamClient = mock(IamClient.class);
        when(iamClient.createAgencyV5(any()))
                .thenThrow(new ServiceResponseException(409, "conflict", "", ""));
        when(iamClient.listAgenciesV5(any())).thenReturn(new ListAgenciesV5Response()
                .withAgencies(List.of(new Agency().withAgencyName("other")))
                .withPageInfo(new PageInfo().withNextMarker("same")));
        client = new GatewayClient(
                true, mock(ICredentialProvider.class), httpClient, () -> iamClient);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.createGateway("name", "description", "mcp", "iam", null));

        assertTrue(error.getMessage().contains("Failed to ensure IAM agency"));
        assertNotNull(error.getCause());
        assertEquals("IAM pagination returned a repeated marker", error.getCause().getMessage());
        verify(iamClient, times(2)).listAgenciesV5(any());
        verify(httpClient, never()).post(anyString(), any(), any());
    }

    @Test
    void listOverloadsSendEmptyQueryMapsAndCloseTransport() {
        when(httpClient.request(anyString(), anyString(), isNull(), isNull(), anyMap()))
                .thenReturn(Mono.just(success));

        assertSame(success, client.listGateways());
        assertSame(success, client.listGatewayTargets("gateway"));
        client.close();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> query = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).request(eq("GET"), eq("/gateways"), isNull(), isNull(), query.capture());
        assertTrue(query.getValue().isEmpty());
        verify(httpClient).request(eq("GET"), eq("/gateways/gateway/targets"),
                isNull(), isNull(), query.capture());
        assertTrue(query.getValue().isEmpty());
        verify(httpClient).close();
    }
}
