package com.huaweicloud.agentarts.sdk.tools;

import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.util.JsonUtils;
import com.huaweicloud.agentarts.sdk.service.http.BaseHttpClient;
import com.huaweicloud.agentarts.sdk.service.http.RequestResult;
import com.huaweicloud.agentarts.sdk.tools.model.CodeInterpreterInvokeRequest;
import com.huaweicloud.agentarts.sdk.tools.model.CreateCodeInterpreterRequest;
import com.huaweicloud.agentarts.sdk.tools.model.StartCodeInterpreterSessionRequest;
import com.huaweicloud.agentarts.sdk.tools.model.UpdateCodeInterpreterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CodeInterpreterClientTest {

    private BaseHttpClient controlClient;
    private BaseHttpClient dataClient;
    private CodeInterpreterClient client;

    @BeforeEach
    void setUp() {
        controlClient = mock(BaseHttpClient.class);
        dataClient = mock(BaseHttpClient.class);
        client = new CodeInterpreterClient(
                "test-region", "https://example.test", "API_KEY", true,
                controlClient, dataClient);
    }

    @Test
    void executesControlPlaneCrudAndParsesTypedResponses() throws Exception {
        when(controlClient.post(eq("/code-interpreters"), isNull(), any()))
                .thenReturn(Mono.just(success("{\"id\":\"created\",\"name\":\"valid-name\"}")));
        when(controlClient.request(eq("GET"), eq("/code-interpreters"),
                isNull(), isNull(), anyMap()))
                .thenReturn(Mono.just(success("{\"items\":[],\"total_count\":0}")));
        when(controlClient.put(eq("/code-interpreters/interpreter"), isNull(), any()))
                .thenReturn(Mono.just(success("{\"id\":\"interpreter\",\"description\":\"updated\"}")));
        when(controlClient.get("/code-interpreters/interpreter"))
                .thenReturn(Mono.just(success("{\"id\":\"interpreter\"}")));
        when(controlClient.delete("/code-interpreters/interpreter"))
                .thenReturn(Mono.just(success("{}")));

        var created = client.createCodeInterpreter(new CreateCodeInterpreterRequest()
                .withName("valid-name").withAuthType("IAM"));
        assertEquals("created", created.getId());
        assertEquals(0, client.listCodeInterpreters("name&limit=999", 20, 5).getTotalCount());
        assertEquals("updated", client.updateCodeInterpreter(
                "interpreter", Map.of("logs", true), List.of(Map.of("key", "value")))
                .getDescription());
        assertEquals("interpreter", client.getCodeInterpreter("interpreter").getId());
        client.deleteCodeInterpreter("interpreter");

        var create = ArgumentCaptor.forClass(CreateCodeInterpreterRequest.class);
        verify(controlClient).post(eq("/code-interpreters"), isNull(), create.capture());
        assertEquals("IAM", create.getValue().getAuthType());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> query = ArgumentCaptor.forClass(Map.class);
        verify(controlClient).request(eq("GET"), eq("/code-interpreters"),
                isNull(), isNull(), query.capture());
        assertEquals(List.of("name&limit=999"), query.getValue().get("name"));
        assertEquals(List.of("20"), query.getValue().get("limit"));
        assertEquals(List.of("5"), query.getValue().get("offset"));

        var update = ArgumentCaptor.forClass(UpdateCodeInterpreterRequest.class);
        verify(controlClient).put(eq("/code-interpreters/interpreter"), isNull(), update.capture());
        assertEquals(Map.of("logs", true), update.getValue().getObservability());
        assertEquals(List.of(Map.of("key", "value")), update.getValue().getTags());
    }

    @Test
    void managesSessionLifecycleAndSessionHeaders() throws Exception {
        String base = "/v1/code-interpreters/interpreter";
        when(dataClient.put(eq(base + "/sessions-start"), isNull(), any()))
                .thenReturn(Mono.just(success("{\"session_id\":\"session\"}")));
        when(dataClient.get(eq(base + "/sessions-get"), anyMap()))
                .thenReturn(Mono.just(success(
                        "{\"code_interpreter_id\":\"id\",\"session_id\":\"session\"}")));
        when(dataClient.put(eq(base + "/sessions-stop"), anyMap(), isNull()))
                .thenReturn(Mono.just(success("{}")));

        assertEquals("session", client.startSession("interpreter", "session-name", 600));
        assertEquals("interpreter", client.getCodeInterpreterName());
        assertEquals("session", client.getSessionId());
        assertEquals("session", client.getSession("interpreter", null).getSessionId());
        assertTrue(client.stopSession());
        assertNull(client.getCodeInterpreterName());
        assertNull(client.getSessionId());
        assertTrue(client.stopSession());

        var start = ArgumentCaptor.forClass(StartCodeInterpreterSessionRequest.class);
        verify(dataClient).put(eq(base + "/sessions-start"), isNull(), start.capture());
        assertEquals("session-name", start.getValue().getName());
        assertEquals(600, start.getValue().getSessionTimeout());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(dataClient).get(eq(base + "/sessions-get"), headers.capture());
        assertEquals("session", headers.getValue().get(Constants.CODE_INTERPRETER_SESSION_HEADER));
    }

    @Test
    void encodesUntrustedResourceNamesInRequestPaths() throws Exception {
        when(controlClient.get("/code-interpreters/..%2Fcontrol%20name"))
                .thenReturn(Mono.just(success("{\"id\":\"encoded\"}")));
        when(dataClient.put(eq("/v1/code-interpreters/..%2Fdata%20name/sessions-start"),
                isNull(), any()))
                .thenReturn(Mono.just(success("{\"session_id\":\"session\"}")));

        assertEquals("encoded", client.getCodeInterpreter("../control name").getId());
        assertEquals("session", client.startSession("../data name", null, null));

        verify(controlClient).get("/code-interpreters/..%2Fcontrol%20name");
        verify(dataClient).put(eq("/v1/code-interpreters/..%2Fdata%20name/sessions-start"),
                isNull(), any());
        assertThrows(IllegalArgumentException.class, () -> client.getCodeInterpreter(" "));
        assertThrows(IllegalArgumentException.class, () -> client.startSession("", null, null));
    }

    @Test
    void buildsInvokeRequestsForHighLevelOperations() throws Exception {
        activateSession();
        when(dataClient.post(eq("/v1/code-interpreters/interpreter/invoke"), anyMap(), any()))
                .thenReturn(Mono.just(success("{\"ok\":true}")));

        assertEquals(true, client.executeCode("print(1)").get("ok"));
        assertEquals(true, client.executeCommand("python -V").get("ok"));
        assertEquals(true, client.uploadFile("nested/../notes.txt", "content", "ignored").get("ok"));
        assertEquals(true, client.uploadFiles(List.of(
                Map.of("path", "/home/user/a.txt", "content", "a"))).get("ok"));
        assertEquals(true, client.installPackages(List.of("requests==2.32.0"), true).get("ok"));
        assertEquals(true, client.clearContext().get("ok"));

        var requests = ArgumentCaptor.forClass(CodeInterpreterInvokeRequest.class);
        verify(dataClient, times(6)).post(
                eq("/v1/code-interpreters/interpreter/invoke"), anyMap(), requests.capture());
        assertEquals(List.of(
                        "execute_code", "execute_command", "write_files",
                        "write_files", "execute_command", "execute_code"),
                requests.getAllValues().stream()
                        .map(CodeInterpreterInvokeRequest::getOperateType).toList());
        assertEquals("/home/user/notes.txt",
                firstWritePath(requests.getAllValues().get(2)));
        assertEquals("pip install requests==2.32.0 --upgrade",
                requests.getAllValues().get(4).getArguments().get("command"));
    }

    @Test
    void extractsTextImageAndResourceDownloads() throws Exception {
        activateSession();
        when(dataClient.post(eq("/v1/code-interpreters/interpreter/invoke"), anyMap(), any()))
                .thenReturn(
                        Mono.just(success(content("{\"type\":\"text\",\"text\":\"hello\"}"))),
                        Mono.just(success(content("{\"type\":\"image\",\"data\":\"aGk=\"}"))),
                        Mono.just(success("{\"result\":{\"content\":["
                                + "{\"uri\":\"file:///home/user/d.txt\",\"type\":\"text\",\"text\":\"hello\"},"
                                + "{\"uri\":\"file:///home/user/img.png\",\"type\":\"image\",\"data\":\"aGk=\"},"
                                + "{\"uri\":\"file:///ignored.txt\",\"type\":\"resource\","
                                + "\"resource\":{\"uri\":\"file:///home/user/a.txt\",\"type\":\"text\",\"text\":\"a\"}},"
                                + "{\"uri\":\"file:///ignored.txt\",\"type\":\"resource\","
                                + "\"resource\":{\"uri\":\"file:///home/user/b.txt\",\"type\":\"blob\",\"blob\":\"Yg==\"}},"
                                + "{\"uri\":\"file:///ignored.bin\",\"type\":\"resource\","
                                + "\"resource\":{\"uri\":\"file:///home/user/c.bin\",\"type\":\"blob\",\"blob\":\"/w==\"}}]}}")));

        assertEquals("hello", client.downloadFile("/home/user/a.txt"));
        assertArrayEquals("hi".getBytes(), (byte[]) client.downloadFile("/home/user/image.png"));
        Map<String, Object> files = client.downloadFiles(
                List.of("/home/user/d.txt", "/home/user/img.png",
                        "/home/user/a.txt", "/home/user/b.txt", "/home/user/c.bin"));
        assertEquals("hello", files.get("/home/user/d.txt"));
        assertArrayEquals("hi".getBytes(), (byte[]) files.get("/home/user/img.png"));
        assertEquals("a", files.get("/home/user/a.txt"));
        // Blob decodes to a UTF-8 String when valid, otherwise raw bytes.
        assertEquals("b", files.get("/home/user/b.txt"));
        assertArrayEquals(new byte[]{(byte) 0xFF}, (byte[]) files.get("/home/user/c.bin"));
    }

    @Test
    void failsClosedForUnsuccessfulOrMissingResponses() {
        when(controlClient.get("/code-interpreters/missing"))
                .thenReturn(Mono.just(RequestResult.builder()
                        .success(false).statusCode(403).error("denied").build()));
        when(controlClient.delete("/code-interpreters/missing"))
                .thenReturn(Mono.empty());

        APIException getError = assertThrows(APIException.class,
                () -> client.getCodeInterpreter("missing"));
        assertEquals(403, getError.getStatusCode());
        APIException deleteError = assertThrows(APIException.class,
                () -> client.deleteCodeInterpreter("missing"));
        assertEquals(0, deleteError.getStatusCode());
    }

    @Test
    void closesInjectedTransportsIdempotently() {
        client.close();
        client.close();

        verify(controlClient).close();
        verify(dataClient).close();
    }

    private void activateSession() {
        client.setCodeInterpreterName("interpreter");
        client.setSessionId("session");
    }

    @SuppressWarnings("unchecked")
    private static String firstWritePath(CodeInterpreterInvokeRequest request) {
        var contents = (List<Map<String, Object>>) request.getArguments().get("write_contents");
        return (String) contents.get(0).get("path");
    }

    private static String content(String item) {
        return "{\"result\":{\"content\":[" + item + "]}}";
    }

    private static RequestResult success(String json) throws Exception {
        return RequestResult.builder()
                .success(true)
                .statusCode(200)
                .data(JsonUtils.MAPPER.readTree(json))
                .build();
    }
}
