package com.huaweicloud.agentarts.sdk.service.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.APIException;
import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.SignMode;
import com.huaweicloud.agentarts.sdk.core.signer.V11Signer;
import com.huaweicloud.agentarts.sdk.service.auth.CredentialProviders;
import com.huaweicloud.sdk.core.auth.AKSKSigner;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.ICredentialProvider;
import com.huaweicloud.sdk.core.http.HttpMethod;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base HTTP client for AgentArts services with signing support.
 * Supports two signing modes:
 * <ul>
 *   <li>{@link SignMode#V11_HMAC_SHA256} — AgentArts V11 signer with HKDF key derivation</li>
 *   <li>{@link SignMode#SDK_HMAC_SHA256} — Standard Huawei Cloud SDK-HMAC-SHA256</li>
 * </ul>
 *
 * <p>All responses are opened in streaming mode. If the Content-Type indicates
 * a streaming response ({@code text/event-stream} or {@code application/x-ndjson}),
 * the body is left unconsumed and available via {@link RequestResult#iterLines()}.</p>
 */
public class BaseHttpClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BaseHttpClient.class);
    private static final ObjectMapper OBJECT_MAPPER = com.huaweicloud.agentarts.sdk.core.util.JsonUtils.MAPPER;
    private static final String STREAM_SSE = "text/event-stream";
    private static final String STREAM_NDJSON = "application/x-ndjson";
    private static final String STREAM_OCTET = "application/octet-stream";
    private static final String STREAM_TAR = "application/x-tar";

    private final RequestConfig config;
    private final boolean openAkSk;
    private final SignMode signMode;
    private final String regionId;
    private final ICredentialProvider credentialProvider;
    private final HttpClient httpClient;
    private final Vertx vertx;
    private final boolean ownVertx;

    // Mutable state (thread-safe)
    private final Map<String, String> defaultHeaders = new ConcurrentHashMap<>();
    private volatile String authHeader;

    /**
     * Create a BaseHttpClient with a shared Vert.x instance.
     */
    public BaseHttpClient(RequestConfig config, boolean openAkSk, SignMode signMode,
                          String regionId, Vertx vertx) {
        this(config, openAkSk, signMode, regionId, vertx, CredentialProviders.defaultBasicProvider());
    }

    /**
     * Create a BaseHttpClient with a shared Vert.x instance and credential provider.
     */
    public BaseHttpClient(RequestConfig config, boolean openAkSk, SignMode signMode,
                          String regionId, Vertx vertx, ICredentialProvider credentialProvider) {
        this.config = config != null ? config : new RequestConfig();
        this.openAkSk = openAkSk;
        this.signMode = signMode != null ? signMode : SignMode.SDK_HMAC_SHA256;
        this.regionId = regionId != null ? regionId : Constants.getRegion();
        this.credentialProvider = Objects.requireNonNull(credentialProvider, "credentialProvider must not be null");
        this.vertx = vertx;
        this.ownVertx = false;
        this.httpClient = createHttpClient(vertx);
    }

    /**
     * Create a BaseHttpClient with its own Vert.x instance.
     *
     * <p>The Vert.x instance is configured with a hardened DNS address resolver:
     * longer per-query timeout (10s vs the 5s default), more query attempts
     * (8 vs the 2–4 default), and negative-result caching disabled. The default
     * Netty async DNS resolver is unreliable on some networks (UDP queries to a
     * single server can time out), which previously caused intermittent
     * {@code UnknownHostException} on cloud endpoints that the system resolver
     * could resolve fine.</p>
     */
    public BaseHttpClient(RequestConfig config, boolean openAkSk, SignMode signMode, String regionId) {
        this(config, openAkSk, signMode, regionId, CredentialProviders.defaultBasicProvider());
    }

    /**
     * Create a BaseHttpClient with its own Vert.x instance and credential provider.
     */
    public BaseHttpClient(RequestConfig config, boolean openAkSk, SignMode signMode, String regionId,
                          ICredentialProvider credentialProvider) {
        this.config = config != null ? config : new RequestConfig();
        this.openAkSk = openAkSk;
        this.signMode = signMode != null ? signMode : SignMode.SDK_HMAC_SHA256;
        this.regionId = regionId != null ? regionId : Constants.getRegion();
        this.credentialProvider = Objects.requireNonNull(credentialProvider, "credentialProvider must not be null");
        io.vertx.core.VertxOptions vertxOptions = new io.vertx.core.VertxOptions()
                .setAddressResolverOptions(new io.vertx.core.dns.AddressResolverOptions()
                        .setQueryTimeout(10_000L)
                        .setMaxQueries(8)
                        .setCacheNegativeTimeToLive(0)
                        .setRdFlag(true));
        this.vertx = Vertx.vertx(vertxOptions);
        this.ownVertx = true;
        this.httpClient = createHttpClient(vertx);
    }

    /**
     * Convenience constructor with default SignMode.SDK_HMAC_SHA256.
     */
    public BaseHttpClient(RequestConfig config) {
        this(config, false, SignMode.SDK_HMAC_SHA256, null);
    }

    private HttpClient createHttpClient(Vertx vertx) {
        HttpClientOptions options = new HttpClientOptions()
                .setConnectTimeout((int) (config.getTimeoutSeconds() * 1000))
                .setIdleTimeout((int) config.getTimeoutSeconds())
                .setTrustAll(!config.isVerifySsl())
                .setVerifyHost(config.isVerifySsl())
                .setDecompressionSupported(true);
        return vertx.createHttpClient(options);
    }

    // ========================
    // Public HTTP methods
    // ========================

    public Mono<RequestResult> get(String url, Map<String, String> headers) {
        return request("GET", url, headers, null, null);
    }

    public Mono<RequestResult> get(String url) {
        return get(url, null);
    }

    public Mono<RequestResult> post(String url, Map<String, String> headers, Object body) {
        return request("POST", url, headers, body, null);
    }

    public Mono<RequestResult> post(String url, Object body) {
        return post(url, null, body);
    }

    public Mono<RequestResult> put(String url, Map<String, String> headers, Object body) {
        return request("PUT", url, headers, body, null);
    }

    public Mono<RequestResult> put(String url, Object body) {
        return put(url, null, body);
    }

    public Mono<RequestResult> patch(String url, Map<String, String> headers, Object body) {
        return request("PATCH", url, headers, body, null);
    }

    public Mono<RequestResult> delete(String url, Map<String, String> headers) {
        return request("DELETE", url, headers, null, null);
    }

    public Mono<RequestResult> delete(String url) {
        return delete(url, null);
    }

    // ========================
    // Auth management
    // ========================

    /**
     * Set the Authorization header.
     *
     * @param scheme auth scheme (e.g., "Bearer")
     * @param token  the token value
     */
    public void setAuthToken(String scheme, String token) {
        if (scheme == null || scheme.isBlank()) {
            throw new IllegalArgumentException("Authentication scheme must not be blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token must not be blank");
        }
        this.authHeader = scheme.trim() + " " + token.trim();
    }

    public void setAuthToken(String token) {
        setAuthToken("Bearer", token);
    }

    /** Clear the Authorization header. */
    public void clearAuth() {
        this.authHeader = null;
    }

    /** Set a default header that will be sent with every request. */
    public void setHeader(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Header name must not be blank");
        }
        defaultHeaders.put(name, Objects.requireNonNull(value, "Header value must not be null"));
    }

    public RequestConfig getConfig() {
        return config;
    }

    public SignMode getSignMode() {
        return signMode;
    }

    // ========================
    // Core request method
    // ========================

    /**
     * Execute an HTTP request with optional signing.
     *
     * @param method      HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param url         relative URL (will be prepended with base URL)
     * @param headers     additional request headers (nullable)
     * @param body        request body (will be serialized to JSON if not null)
     * @param queryParams query parameters (nullable)
     * @return Mono emitting the RequestResult
     */
    public Mono<RequestResult> request(String method, String url,
                                        Map<String, String> headers,
                                        Object body,
                                        Map<String, List<String>> queryParams) {
        return request(method, url, headers, body, queryParams, null);
    }

    /** Execute a request with an optional per-request timeout override. */
    public Mono<RequestResult> request(String method, String url,
                                        Map<String, String> headers,
                                        Object body,
                                        Map<String, List<String>> queryParams,
                                        Double timeoutSeconds) {
        return requestInternal(method, url, headers, body, queryParams, timeoutSeconds, false);
    }

    /**
     * Execute a request that leaves successful octet-stream or tar responses unbuffered.
     * JSON/text error responses remain aggregated so callers can inspect service errors.
     */
    public Mono<RequestResult> requestBinaryStream(String method, String url,
                                                    Map<String, String> headers,
                                                    Object body,
                                                    Map<String, List<String>> queryParams,
                                                    Double timeoutSeconds) {
        return requestInternal(method, url, headers, body, queryParams, timeoutSeconds, true);
    }

    private Mono<RequestResult> requestInternal(String method, String url,
                                                 Map<String, String> headers,
                                                 Object body,
                                                 Map<String, List<String>> queryParams,
                                                 Double timeoutSeconds,
                                                 boolean streamBinaryResponse) {
        if (timeoutSeconds != null && (!Double.isFinite(timeoutSeconds) || timeoutSeconds <= 0
                || timeoutSeconds > RequestConfig.MAX_TIMEOUT_SECONDS)) {
            return Mono.error(new IllegalArgumentException(
                    "timeoutSeconds must be greater than zero and at most "
                            + (long) RequestConfig.MAX_TIMEOUT_SECONDS));
        }
        return Mono.fromFuture(() -> executeRequest(
                        method, url, headers, body, queryParams, timeoutSeconds,
                        streamBinaryResponse))
                .subscribeOn(Schedulers.boundedElastic())
                // Retry on transient DNS resolution failures — the Netty async
                // resolver can time out on networks where the system resolver
                // would succeed. Each retry re-signs (fresh timestamp) via
                // executeRequest.
                .<RequestResult>flatMap(result -> {
                    if (result != null && !result.isSuccess() && result.getError() != null
                            && result.getError().contains("Failed to resolve")) {
                        return Mono.error(new java.io.IOException("DNS resolution failed: "
                                + result.getError()));
                    }
                    return Mono.just(result);
                })
                .retryWhen(reactor.util.retry.Retry
                        .max(3)
                        .filter(e -> e instanceof java.io.IOException
                                && e.getMessage() != null
                                && e.getMessage().contains("DNS resolution failed"))
                        .doBeforeRetry(rc -> LOG.warn("Retrying after DNS failure (attempt {}): {}",
                                rc.totalRetries() + 1, rc.failure().getMessage())))
                .onErrorResume(e -> Mono.just(RequestResult.builder()
                        .success(false)
                        .error("Request error: " + e.getMessage())
                        .build()));
    }

    private CompletableFuture<RequestResult> executeRequest(String method, String url,
                                                             Map<String, String> headers,
                                                             Object body,
                                                             Map<String, List<String>> queryParams,
                                                             Double timeoutSeconds,
                                                             boolean streamBinaryResponse) {
        try {
            // Build full URL
            String fullUrl = joinUrl(config.getBaseUrl(), url);
            if (queryParams != null && !queryParams.isEmpty()) {
                fullUrl = appendQueryParams(fullUrl, queryParams);
            }

            // Merge headers
            Map<String, String> mergedHeaders = new HashMap<>();
            defaultHeaders.forEach((name, value) ->
                    setHeaderReplacingCaseInsensitive(mergedHeaders, name, value));
            if (authHeader != null) {
                setHeaderReplacingCaseInsensitive(mergedHeaders, "Authorization", authHeader);
            }
            if (headers != null) {
                headers.forEach((name, value) ->
                        setHeaderReplacingCaseInsensitive(mergedHeaders, name, value));
            }

            // Content-Type must not participate in request signing: save it,
            // remove it from the headers passed to signRequest, then restore it
            // after signing so the signed request never includes it.
            String contentType = getHeader(mergedHeaders, "Content-Type", null);
            if (contentType == null && body != null) {
                contentType = "application/json";
            }
            mergedHeaders.keySet().removeIf(key -> key.equalsIgnoreCase("Content-Type"));

            // Sign the request if AK/SK signing is enabled
            if (openAkSk) {
                signRequest(method, fullUrl, mergedHeaders, body, queryParams);
            }

            // Restore Content-Type after signing
            if (contentType != null) {
                setHeaderReplacingCaseInsensitive(mergedHeaders, "Content-Type", contentType);
            }

            // Serialize body. byte[] is sent as raw bytes (for streaming uploads
            // with a caller-supplied Content-Type, e.g. application/octet-stream);
            // other objects are JSON-serialized.
            byte[] bodyBytes = null;
            if (body != null) {
                if (body instanceof byte[]) {
                    bodyBytes = (byte[]) body;
                } else if (body instanceof String) {
                    bodyBytes = ((String) body).getBytes(StandardCharsets.UTF_8);
                } else {
                    bodyBytes = OBJECT_MAPPER.writeValueAsBytes(body);
                }
                if (bodyBytes.length > config.getMaxRequestBodyBytes()) {
                    throw new IllegalArgumentException(
                            "Request body exceeds configured limit of "
                                    + config.getMaxRequestBodyBytes() + " bytes");
                }
            }

            // Build Vert.x request
            URI uri = URI.create(fullUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equals(uri.getScheme()) ? 443 : 80;
            }
            String path = uri.getRawPath();
            if (uri.getRawQuery() != null) {
                path = path + "?" + uri.getRawQuery();
            }

            double effectiveTimeout = timeoutSeconds != null
                    ? timeoutSeconds : config.getTimeoutSeconds();
            CompletableFuture<RequestResult> future = new CompletableFuture<>();
            RequestOptions options = new RequestOptions()
                    .setMethod(io.vertx.core.http.HttpMethod.valueOf(method))
                    .setHost(host)
                    .setPort(port)
                    .setSsl("https".equals(uri.getScheme()))
                    .setURI(path)
                    .setFollowRedirects(config.isFollowRedirects())
                    .setTimeout((long) (effectiveTimeout * 1000));
            mergedHeaders.forEach(options::putHeader);

            Buffer requestBody = bodyBytes != null ? Buffer.buffer(bodyBytes) : null;
            httpClient.request(options).onComplete(requestAr -> {
                if (requestAr.failed()) {
                    completeRequestFailure(future, requestAr.cause());
                    return;
                }
                HttpClientRequest request = requestAr.result();
                if (requestBody != null) {
                    request.send(requestBody).onComplete(
                            ar -> handleResponse(ar, future, streamBinaryResponse));
                } else {
                    request.send().onComplete(
                            ar -> handleResponse(ar, future, streamBinaryResponse));
                }
            });

            return future;
        } catch (Exception e) {
            LOG.error("Request error: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(
                    RequestResult.builder()
                            .success(false)
                            .statusCode(0)
                            .error("Request error: " + e.getMessage())
                            .build()
            );
        }
    }

    private void handleResponse(io.vertx.core.AsyncResult<HttpClientResponse> ar,
                                 CompletableFuture<RequestResult> future,
                                 boolean streamBinaryResponse) {
        if (ar.failed()) {
            completeRequestFailure(future, ar.cause());
            return;
        }

        try {
            HttpClientResponse response = ar.result();
            int statusCode = response.statusCode();
            boolean success = statusCode >= 200 && statusCode < 300;

            // Extract response headers
            Map<String, String> responseHeaders = new HashMap<>();
            response.headers().forEach(entry ->
                    responseHeaders.put(entry.getKey(), entry.getValue()));

            // Check for streaming content type
            String responseContentType = response.getHeader("Content-Type");
            String contentType = responseContentType != null ? responseContentType.toLowerCase() : "";
            boolean isStreaming = contentType.contains(STREAM_SSE)
                    || contentType.contains(STREAM_NDJSON)
                    || (streamBinaryResponse && success
                    && (contentType.contains(STREAM_OCTET) || contentType.contains(STREAM_TAR)));

            if (isStreaming) {
                // Stop the Vert.x stream before exposing the result. Demand from
                // the selected Reactor stream drives response.fetch(n), so the
                // entire body is never materialized in memory.
                response.pause();
                Context responseContext = Vertx.currentContext();
                AtomicBoolean subscribed = new AtomicBoolean();
                AtomicBoolean terminated = new AtomicBoolean();
                Runnable closeAction = () -> abortStreamingResponse(response, responseContext, terminated);
                Flux<byte[]> byteStream = createByteStream(
                        response, responseContext, subscribed, terminated, closeAction);
                Flux<String> lineStream = decodeLines(byteStream);

                future.complete(RequestResult.builder()
                        .success(success)
                        .statusCode(statusCode)
                        .headers(responseHeaders)
                        .streaming(true)
                        .lineStream(lineStream)
                        .byteStream(byteStream)
                        .closeAction(closeAction)
                        .build());
            } else {
                aggregateResponse(response, statusCode, success, contentType,
                        responseHeaders, future);
            }
        } catch (Exception e) {
            completeResponseFailure(future, e);
        }
    }

    private void aggregateResponse(HttpClientResponse response, int statusCode, boolean success,
                                   String contentType, Map<String, String> responseHeaders,
                                   CompletableFuture<RequestResult> future) {
        long maximumBytes = config.getMaxResponseBodyBytes();
        String contentLength = response.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > maximumBytes) {
                    response.request().reset();
                    completeOversizedResponse(future, statusCode, responseHeaders, maximumBytes);
                    return;
                }
            } catch (NumberFormatException ignored) {
                // Invalid server metadata is ignored; the streaming counter remains authoritative.
            }
        }

        Buffer aggregate = Buffer.buffer();
        AtomicBoolean terminated = new AtomicBoolean();
        response.exceptionHandler(error -> {
            if (terminated.compareAndSet(false, true)) {
                completeRequestFailure(future, error);
            }
        });
        response.handler(chunk -> {
            if (terminated.get()) return;
            long nextSize = (long) aggregate.length() + chunk.length();
            if (nextSize > maximumBytes) {
                if (terminated.compareAndSet(false, true)) {
                    response.request().reset();
                    completeOversizedResponse(future, statusCode, responseHeaders, maximumBytes);
                }
                return;
            }
            aggregate.appendBuffer(chunk);
        });
        response.endHandler(ignored -> {
            if (!terminated.compareAndSet(false, true)) return;
            try {
                byte[] bodyBytes = aggregate.getBytes();
                String bodyString = new String(bodyBytes, StandardCharsets.UTF_8);
                Object data = null;
                String error = null;
                if (bodyBytes.length > 0) {
                    if (isTextResponse(contentType)) {
                        try {
                            data = OBJECT_MAPPER.readTree(bodyString);
                        } catch (Exception parseError) {
                            data = bodyString;
                        }
                    } else {
                        data = bodyBytes;
                    }
                    if (!success) {
                        error = extractErrorFromBody(data);
                        if (error == null) {
                            error = "HTTP " + statusCode + " returned a binary response";
                        }
                    }
                }
                future.complete(RequestResult.builder()
                        .success(success)
                        .statusCode(statusCode)
                        .data(data)
                        .error(error)
                        .headers(responseHeaders)
                        .streaming(false)
                        .build());
            } catch (Exception error) {
                completeResponseFailure(future, error);
            }
        });
    }

    private void completeOversizedResponse(CompletableFuture<RequestResult> future,
                                           int statusCode,
                                           Map<String, String> responseHeaders,
                                           long maximumBytes) {
        future.complete(RequestResult.builder()
                .success(false)
                .statusCode(statusCode)
                .headers(responseHeaders)
                .error("Response body exceeds configured limit of " + maximumBytes + " bytes")
                .build());
    }

    private static boolean isTextResponse(String contentType) {
        if (contentType == null || contentType.isBlank()) return true;
        return contentType.startsWith("text/")
                || contentType.contains("json")
                || contentType.contains("xml")
                || contentType.contains("yaml")
                || contentType.contains("javascript")
                || contentType.contains("x-www-form-urlencoded");
    }

    private Flux<byte[]> createByteStream(HttpClientResponse response, Context responseContext,
                                          AtomicBoolean subscribed, AtomicBoolean terminated,
                                          Runnable closeAction) {
        return Flux.create(sink -> {
            if (!subscribed.compareAndSet(false, true)) {
                sink.error(new IllegalStateException(
                        "A streaming response body can only be consumed once; choose iterLines() or iterBytes()"));
                return;
            }
            if (terminated.get()) {
                sink.error(new IllegalStateException("Streaming response has already been closed"));
                return;
            }

            if (responseContext == null) {
                terminated.set(true);
                sink.error(new IllegalStateException("Streaming response is not associated with a Vert.x context"));
                return;
            }

            responseContext.runOnContext(ignored -> {
                response.exceptionHandler(error -> {
                    if (terminated.compareAndSet(false, true)) {
                        sink.error(error);
                    }
                });
                response.handler(buffer -> {
                    if (!terminated.get()) {
                        sink.next(buffer.getBytes());
                    }
                });
                response.endHandler(ignoredEnd -> {
                    if (terminated.compareAndSet(false, true)) {
                        sink.complete();
                    }
                });
            });

            sink.onRequest(demand -> responseContext.runOnContext(ignored -> {
                if (!terminated.get()) {
                    if (demand == Long.MAX_VALUE) {
                        response.resume();
                    } else if (demand > 0) {
                        response.fetch(demand);
                    }
                }
            }));
            sink.onCancel(closeAction::run);
        }, FluxSink.OverflowStrategy.ERROR);
    }

    private void abortStreamingResponse(HttpClientResponse response, Context responseContext,
                                        AtomicBoolean terminated) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        Runnable reset = () -> response.request().reset();
        if (responseContext != null) {
            responseContext.runOnContext(ignored -> reset.run());
        } else {
            reset.run();
        }
    }

    private Flux<String> decodeLines(Flux<byte[]> byteStream) {
        return Flux.defer(() -> {
            Utf8LineDecoder decoder = new Utf8LineDecoder();
            return byteStream.concatMapIterable(decoder::accept)
                    .concatWith(Flux.defer(() -> Flux.fromIterable(decoder.finish())));
        });
    }

    private void completeRequestFailure(CompletableFuture<RequestResult> future, Throwable error) {
        LOG.error("Request failed: {}", error.getMessage(), error);
        future.complete(RequestResult.builder()
                .success(false)
                .statusCode(0)
                .error("Request error: " + error.getMessage())
                .build());
    }

    private void completeResponseFailure(CompletableFuture<RequestResult> future, Throwable error) {
        LOG.error("Response handling error: {}", error.getMessage(), error);
        future.complete(RequestResult.builder()
                .success(false)
                .statusCode(0)
                .error("Unexpected error: " + error.getMessage())
                .build());
    }

    private static final class Utf8LineDecoder {
        private final ByteArrayOutputStream currentLine = new ByteArrayOutputStream();

        List<String> accept(byte[] bytes) {
            List<String> lines = new ArrayList<>();
            for (byte value : bytes) {
                if (value == '\n') {
                    lines.add(takeLine());
                } else {
                    currentLine.write(value);
                }
            }
            return lines;
        }

        List<String> finish() {
            return currentLine.size() == 0 ? List.of() : List.of(takeLine());
        }

        private String takeLine() {
            byte[] bytes = currentLine.toByteArray();
            currentLine.reset();
            int length = bytes.length;
            if (length > 0 && bytes[length - 1] == '\r') {
                length--;
            }
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        }
    }

    // ========================
    // Signing
    // ========================

    private void signRequest(String method, String fullUrl, Map<String, String> headers,
                              Object body, Map<String, List<String>> queryParams) {
        try {
            BasicCredentials credentials = loadCredentials();

            Map<String, List<String>> effectiveQueryParams = parseQueryParams(URI.create(fullUrl));
            if (signMode == SignMode.V11_HMAC_SHA256) {
                signRequestV11(method, fullUrl, headers, body, effectiveQueryParams, credentials);
            } else {
                signRequestSdk(method, fullUrl, headers, body, effectiveQueryParams, credentials);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign request; request was not sent", e);
        }
    }

    /**
     * Sign using Huawei Cloud SDK standard SDK-HMAC-SHA256 (via built-in AKSKSigner).
     */
    private void signRequestSdk(String method, String fullUrl, Map<String, String> headers,
                                 Object body, Map<String, List<String>> queryParams,
                                 BasicCredentials credentials) throws Exception {
        URI uri = URI.create(fullUrl);
        String endpoint = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() > 0 && uri.getPort() != 443 && uri.getPort() != 80) {
            endpoint += ":" + uri.getPort();
        }
        String path = uri.getRawPath();

        // BaseHttpClient invokes AKSKSigner directly instead of the generated
        // SDK client's auth pipeline. AKSKSigner does not inject the STS token,
        // so it must be present before signing and before the request is sent.
        String securityToken = credentials.getSecurityToken();
        if (securityToken != null && !securityToken.isBlank()) {
            setHeaderReplacingCaseInsensitive(headers, "X-Security-Token", securityToken);
        }

        com.huaweicloud.sdk.core.http.HttpRequest.HttpRequestBuilder builder = com.huaweicloud.sdk.core.http.HttpRequest
                .newBuilder()
                .withEndpoint(endpoint)
                .withPath(path)
                .withMethod(HttpMethod.valueOf(method));

        // Query parameters are parsed once from the final URL by signRequest.
        // Adding both the URL query and the original argument signs duplicates.
        if (queryParams != null) {
            for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
                builder.addQueryParam(entry.getKey(), entry.getValue());
            }
        }

        String bodyStr = "";
        // byte[] bodies are streamed raw (non-JSON Content-Type); the SDK-HMAC
        // signer hashes a body *string*, so leave the signed body empty for raw
        // bytes — V11 (UNSIGNED-PAYLOAD) is unaffected, and bearer-token agents
        // do not verify the body signature.
        boolean binaryBody = body instanceof byte[];
        if (binaryBody) {
            setHeaderReplacingCaseInsensitive(headers, "x-sdk-content-sha256", "UNSIGNED-PAYLOAD");
        }
        if (body != null && !binaryBody) {
            if (body instanceof String) {
                bodyStr = (String) body;
            } else {
                bodyStr = OBJECT_MAPPER.writeValueAsString(body);
            }
        }
        if (!"GET".equals(method) && !"HEAD".equals(method) && !bodyStr.isEmpty()) {
            builder.withBodyAsString(bodyStr);
            builder.withContentType(getHeader(headers, "Content-Type", "application/json"));
        }

        builder.addHeaders(headers);
        com.huaweicloud.sdk.core.http.HttpRequest sdkRequest = builder.build();
        Map<String, String> signedHeaders = AKSKSigner.getInstance().sign(sdkRequest, credentials);
        headers.putAll(signedHeaders);
    }

    /**
     * Sign using V11-HMAC-SHA256 with HKDF key derivation (AgentArts custom signer).
     */
    private void signRequestV11(String method, String fullUrl, Map<String, String> headers,
                                 Object body, Map<String, List<String>> queryParams,
                                 BasicCredentials credentials) throws Exception {
        URI uri = URI.create(fullUrl);
        String host = uri.getHost();
        if (uri.getPort() != -1) {
            host += ":" + uri.getPort();
        }
        String path = uri.getRawPath();

        setHeaderReplacingCaseInsensitive(headers, "Host", host);
        setHeaderReplacingCaseInsensitive(headers, "x-sdk-content-sha256", "UNSIGNED-PAYLOAD");

        String securityToken = credentials.getSecurityToken();
        if (securityToken != null && !securityToken.isBlank()) {
            setHeaderReplacingCaseInsensitive(headers, "X-Security-Token", securityToken);
        }

        V11Signer signer = new V11Signer(credentials.getAk(), credentials.getSk(), regionId);
        signer.sign(method, path, queryParams, headers);
    }

    // ========================
    // Helpers
    // ========================

    private String appendQueryParams(String url, Map<String, List<String>> queryParams) {
        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        TreeMap<String, List<String>> sorted = new TreeMap<>(queryParams);
        for (Map.Entry<String, List<String>> entry : sorted.entrySet()) {
            String key = encodeQueryComponent(entry.getKey());
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                values = List.of("");
            }
            for (String value : values) {
                if (!first) {
                    sb.append('&');
                }
                sb.append(key).append('=').append(encodeQueryComponent(value));
                first = false;
            }
        }
        return sb.toString();
    }

    private static String joinUrl(String baseUrl, String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Request URL must not be blank");
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("RequestConfig.baseUrl must be configured for relative URLs");
        }
        if (baseUrl.endsWith("/") && url.startsWith("/")) {
            return baseUrl + url.substring(1);
        }
        if (!baseUrl.endsWith("/") && !url.startsWith("/")) {
            return baseUrl + "/" + url;
        }
        return baseUrl + url;
    }

    static Map<String, List<String>> parseQueryParams(URI uri) {
        Map<String, List<String>> result = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&", -1)) {
            // A few legacy clients build URLs ending in '?'/ '&'. HTTP servers
            // ignore the empty segment, so the signer must ignore it as well;
            // signing it as an empty-name parameter produces a different
            // canonical query string from the service.
            if (pair.isEmpty()) {
                continue;
            }
            String[] keyValue = pair.split("=", 2);
            String key = java.net.URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
            String value = keyValue.length == 2
                    ? java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8)
                    : "";
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private static String encodeQueryComponent(String value) {
        String encoded = URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
        return encoded.replace("+", "%20").replace("%7E", "~").replace("*", "%2A");
    }

    private static String getHeader(Map<String, String> headers, String name, String defaultValue) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(defaultValue);
    }

    private static void setHeaderReplacingCaseInsensitive(
            Map<String, String> headers, String name, String value) {
        headers.keySet().removeIf(key -> key.equalsIgnoreCase(name));
        headers.put(name, value);
    }

    // Package-visible seam keeps credential failure tests deterministic without
    // mutating process environment or contacting metadata services.
    BasicCredentials loadCredentials() {
        return CredentialProviders.resolveBasic(credentialProvider);
    }

    private String extractErrorFromBody(Object data) {
        if (data instanceof JsonNode) {
            JsonNode json = (JsonNode) data;
            if (json.has("error")) {
                JsonNode errNode = json.get("error");
                if (errNode.isTextual()) return errNode.asText();
                if (errNode.isObject() && errNode.has("message")) return errNode.get("message").asText();
            }
            if (json.has("message")) return json.get("message").asText();
            if (json.has("error_msg")) return json.get("error_msg").asText();
        }
        if (data instanceof String) return (String) data;
        return null;
    }

    @Override
    public void close() {
        httpClient.close();
        if (ownVertx) {
            vertx.close();
        }
    }
}
