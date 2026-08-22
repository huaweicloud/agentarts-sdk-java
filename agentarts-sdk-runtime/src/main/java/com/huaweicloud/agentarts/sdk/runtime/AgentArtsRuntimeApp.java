package com.huaweicloud.agentarts.sdk.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.sdk.core.PingStatus;
import com.huaweicloud.agentarts.sdk.runtime.context.AgentArtsRuntimeContext;
import com.huaweicloud.agentarts.sdk.runtime.context.RequestContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketHandshake;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * AgentArts Runtime Application — Vert.x-based HTTP server.
 *
 * <p>Vert.x-based HTTP server for hosting agent endpoints.
 * Exposes three endpoints:</p>
 * <ul>
 *   <li>{@code POST /invocations} — main agent invocation (sync JSON / SSE streaming)</li>
 *   <li>{@code GET /ping} — health check returning {@link PingStatus}</li>
 *   <li>{@code WS /ws} — WebSocket endpoint for bidirectional streaming</li>
 * </ul>
 *
 * <h2>Concurrency control:</h2>
 * <p>A {@link Semaphore} limits concurrent invocations. When all permits are taken,
 * new requests receive HTTP 503 with {@code {"error": "Service busy - maximum concurrency reached"}}.</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * AgentArtsRuntimeApp app = new AgentArtsRuntimeApp();
 *
 * app.setEntrypoint((payload, ctx) -> {
 *     return Map.of("result", "Hello " + payload.get("name"));
 * });
 *
 * app.setPingHandler(() -> PingStatus.HEALTHY);
 *
 * app.run(8080);
 * }</pre>
 */
public class AgentArtsRuntimeApp {

    private static final Logger LOG = LoggerFactory.getLogger(AgentArtsRuntimeApp.class);
    public static final long DEFAULT_MAX_REQUEST_BODY_BYTES = 10L * 1024 * 1024;
    private static final Object EMPTY_MONO_RESPONSE = new Object();
    private static final ObjectMapper OBJECT_MAPPER = com.huaweicloud.agentarts.sdk.core.util.JsonUtils.MAPPER;

    private final int maxConcurrency;
    private final Semaphore invocationSemaphore;
    private final Vertx vertx;
    private final boolean ownVertx;
    private volatile long maxRequestBodyBytes = DEFAULT_MAX_REQUEST_BODY_BYTES;

    // Handlers (volatile: set on user thread, read on Vert.x event-loop thread)
    private volatile BiFunction<Map<String, Object>, RequestContext, Object> entrypointHandler;
    private volatile Function<RequestContext, Object> entrypointWithPayloadOnly;
    private volatile Supplier<PingStatus> pingHandler;
    private volatile Function<RequestContext, PingStatus> pingHandlerWithCtx;
    private volatile BiFunction<ServerWebSocket, RequestContext, Void> wsHandler;

    // State
    private final AtomicReference<PingStatus> forcedPingStatus = new AtomicReference<>();
    private final AtomicLong lastStatusUpdateTime = new AtomicLong(System.currentTimeMillis());
    private final java.util.concurrent.ConcurrentHashMap<Long, String> activeTasks =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong taskCounter = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger activeTaskCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private final Object lifecycleLock = new Object();
    private final List<AutoCloseable> managedResources = new ArrayList<>();
    private volatile HttpServer httpServer;
    private volatile boolean closed;

    /**
     * Create an AgentArtsRuntimeApp.
     *
     * @param maxConcurrency maximum concurrent invocations (default 15)
     * @param vertx          shared Vert.x instance (nullable, creates own if null)
     */
    public AgentArtsRuntimeApp(int maxConcurrency, Vertx vertx) {
        AgentArtsRuntimeContext.enableReactorContextPropagation();
        this.maxConcurrency = maxConcurrency > 0 ? maxConcurrency : Constants.DEFAULT_MAX_CONCURRENCY;
        this.invocationSemaphore = new Semaphore(this.maxConcurrency);
        if (vertx != null) {
            this.vertx = vertx;
            this.ownVertx = false;
        } else {
            this.vertx = Vertx.vertx();
            this.ownVertx = true;
        }
    }

    public AgentArtsRuntimeApp(int maxConcurrency) {
        this(maxConcurrency, null);
    }

    public AgentArtsRuntimeApp() {
        this(Constants.DEFAULT_MAX_CONCURRENCY, null);
    }

    /**
     * Register an application resource that is closed exactly once when this runtime stops.
     * Resources are closed in reverse registration order after the HTTP listener has stopped.
     *
     * @param resource resource owned by this application
     * @return the same resource for fluent construction
     * @param <T> resource type
     */
    public <T extends AutoCloseable> T registerManagedResource(T resource) {
        java.util.Objects.requireNonNull(resource, "resource");
        synchronized (lifecycleLock) {
            if (closed) {
                throw new IllegalStateException("AgentArts Runtime is closed");
            }
            managedResources.add(resource);
        }
        return resource;
    }

    /** Configure the maximum aggregated HTTP invocation body before the server is started. */
    public void setMaxRequestBodyBytes(long maximumBytes) {
        if (maximumBytes < 1) {
            throw new IllegalArgumentException("maximumBytes must be greater than zero");
        }
        if (httpServer != null) {
            throw new IllegalStateException(
                    "Maximum request body size cannot be changed after the server is started");
        }
        this.maxRequestBodyBytes = maximumBytes;
    }

    public long getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    // ========================
    // Handler registration
    // ========================

    /**
     * Register the main invocation handler.
     *
     * @param handler receives (payload, RequestContext) and returns a result.
     *                Return a {@link Flux} for SSE streaming, or any object for JSON response.
     */
    public void setEntrypoint(BiFunction<Map<String, Object>, RequestContext, Object> handler) {
        this.entrypointHandler = handler;
    }

    /**
     * Register a simple entrypoint that only receives payload (no context).
     */
    public void setEntrypoint(Function<Map<String, Object>, Object> handler) {
        this.entrypointWithPayloadOnly = (ctx) -> null; // unused
        this.entrypointHandler = (payload, ctx) -> handler.apply(payload);
    }

    /**
     * Register the health-check ping handler.
     */
    public void setPingHandler(Supplier<PingStatus> handler) {
        this.pingHandler = handler;
    }

    public void setPingHandler(Function<RequestContext, PingStatus> handler) {
        this.pingHandlerWithCtx = handler;
    }

    /**
     * Register the WebSocket handler.
     */
    public void setWebSocketHandler(BiFunction<ServerWebSocket, RequestContext, Void> handler) {
        this.wsHandler = handler;
    }

    /**
     * Force a specific ping status (e.g., for graceful shutdown).
     */
    public void forcePingStatus(PingStatus status) {
        forcedPingStatus.set(status);
        lastStatusUpdateTime.set(System.currentTimeMillis());
    }

    // ========================
    // Async task tracking
    // ========================

    /**
     * Register an active background task.
     *
     * @return task ID for later completion
     */
    public long addTask(String name) {
        long taskId = taskCounter.incrementAndGet();
        activeTasks.put(taskId, name);
        activeTaskCount.incrementAndGet();
        return taskId;
    }

    /**
     * Mark a background task as complete.
     */
    public void completeTask(long taskId) {
        if (activeTasks.remove(taskId) != null) {
            activeTaskCount.decrementAndGet();
        }
    }

    /**
     * Check if any background tasks are running.
     */
    public boolean hasRunningTasks() {
        return activeTaskCount.get() > 0;
    }

    // ========================
    // Server lifecycle
    // ========================

    /**
     * Start the HTTP server and block until it's listening.
     *
     * @param port the port to listen on (default 8080)
     */
    public void run(int port) {
        run(port, "0.0.0.0");
    }

    /**
     * Start the HTTP server on a specific interface and block until it is listening.
     *
     * @param port the port to listen on; {@code 0} selects an ephemeral port
     * @param host bind address or host name
     */
    public void run(int port, String host) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }

        synchronized (lifecycleLock) {
            if (closed) {
                throw new IllegalStateException("AgentArts Runtime is closed");
            }
            if (httpServer != null) {
                throw new IllegalStateException("AgentArts Runtime is already started");
            }
        }

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create().setBodyLimit(maxRequestBodyBytes));

        // Routes
        router.post("/invocations").handler(this::handleInvocation);
        router.get("/ping").handler(this::handlePing);

        // WebSocket
        HttpServer server = vertx.createHttpServer(new HttpServerOptions());
        server.webSocketHandshakeHandler(this::handleWebSocketHandshake);
        server.webSocketHandler(this::handleWebSocket);
        server.requestHandler(router);

        synchronized (lifecycleLock) {
            httpServer = server;
        }
        try {
            server.listen(port, host).onSuccess(listening ->
                    LOG.info("AgentArts Runtime started on {}:{}", host, listening.actualPort()))
                    .toCompletionStage().toCompletableFuture().join();
        } catch (RuntimeException e) {
            synchronized (lifecycleLock) {
                if (httpServer == server) {
                    httpServer = null;
                }
            }
            try {
                server.close().toCompletionStage().toCompletableFuture().join();
            } catch (RuntimeException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    public void run() {
        run(Constants.DEFAULT_PORT);
    }

    /**
     * Start the server and keep the calling thread alive until interruption or JVM shutdown.
     * The shutdown path always invokes {@link #stop()}, including managed-resource cleanup.
     */
    public void runUntilShutdown(int port, String host) {
        CountDownLatch stopped = new CountDownLatch(1);
        Thread shutdownHook = new Thread(() -> {
            try {
                stop();
            } finally {
                stopped.countDown();
            }
        }, "agentarts-runtime-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            run(port, host);
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is already in progress.
            }
            stop();
        }
    }

    public void runUntilShutdown(int port) {
        runUntilShutdown(port, "0.0.0.0");
    }

    public void runUntilShutdown() {
        runUntilShutdown(Constants.DEFAULT_PORT);
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        HttpServer server;
        boolean closeVertx;
        List<AutoCloseable> resources;
        synchronized (lifecycleLock) {
            server = httpServer;
            httpServer = null;
            closeVertx = !closed && ownVertx;
            closed = true;
            resources = new ArrayList<>(managedResources);
            managedResources.clear();
        }
        RuntimeException failure = null;
        if (server != null) {
            try {
                server.close().toCompletionStage().toCompletableFuture().join();
            } catch (RuntimeException e) {
                failure = e;
            }
        }
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).close();
            } catch (Exception e) {
                failure = appendFailure(failure,
                        new IllegalStateException("Failed to close a managed runtime resource", e));
            }
        }
        if (closeVertx) {
            try {
                vertx.close().toCompletionStage().toCompletableFuture().join();
            } catch (RuntimeException e) {
                failure = appendFailure(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException appendFailure(
            RuntimeException current, RuntimeException additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
    }

    public int getPort() {
        return httpServer != null ? httpServer.actualPort() : 0;
    }

    // ========================
    // POST /invocations
    // ========================

    @SuppressWarnings("unchecked")
    private void handleInvocation(RoutingContext rc) {
        if (entrypointHandler == null) {
            sendJsonError(rc, 404, "NotFound", "No entrypoint handler registered");
            return;
        }

        // Parse JSON payload
        Map<String, Object> payload;
        try {
            String body = rc.body().asString();
            if (body == null || body.isEmpty()) {
                // Match Python: empty body → 400 JSONDecodeError
                sendJsonError(rc, 400, "Invalid JSON payload", "Empty request body");
                return;
            } else {
                payload = OBJECT_MAPPER.readValue(body, Map.class);
            }
        } catch (JsonProcessingException e) {
            sendJsonError(rc, 400, "Invalid JSON payload", e.getMessage());
            return;
        }

        // Build request context from headers
        RequestContext ctx = RequestContext.fromHeaders(name ->
                rc.request().getHeader(name));

        // Concurrency check: if all permits are taken, return immediately.
        if (!invocationSemaphore.tryAcquire()) {
            sendJsonError(rc, 503, "Service busy - maximum concurrency reached", null);
            return;
        }

        // User handlers are allowed to block. Run handler construction on a worker
        // thread so a slow SDK/model call cannot stall the Vert.x event loop.
        vertx.executeBlocking(() -> {
            AgentArtsRuntimeContext.fromRequestContext(ctx);
            try {
                Object result = entrypointHandler.apply(payload, ctx);
                if (result instanceof Flux<?> flux) {
                    return AgentArtsRuntimeContext.propagate(flux);
                }
                if (result instanceof Mono<?> mono) {
                    return AgentArtsRuntimeContext.propagate(mono);
                }
                return result;
            } finally {
                AgentArtsRuntimeContext.clear();
            }
        }, false).onComplete(ar -> {
            if (ar.failed()) {
                invocationSemaphore.release();
                Throwable error = ar.cause();
                LOG.error("Invocation error: {}", error.getMessage(), error);
                sendJsonError(rc, 500, error.getClass().getSimpleName(), error.getMessage());
                return;
            }

            Object result = ar.result();
            if (result instanceof Flux<?> flux) {
                sendSseStream(rc, flux.doFinally(signal -> invocationSemaphore.release()),
                        ctx.getSessionId());
            } else if (result instanceof Mono<?> mono) {
                sendMonoResponse(rc, mono.doFinally(signal -> invocationSemaphore.release()),
                        ctx.getSessionId());
            } else {
                try {
                    sendJsonResponse(rc, 200, result, ctx.getSessionId());
                } finally {
                    invocationSemaphore.release();
                }
            }
        });
    }

    // ========================
    // GET /ping
    // ========================

    private void handlePing(RoutingContext rc) {
        try {
            RequestContext ctx = RequestContext.fromHeaders(name ->
                    rc.request().getHeader(name));

            PingStatus status = getCurrentPingStatus(ctx);

            Map<String, Object> response = Map.of(
                    "status", status.getValue(),
                    "time_of_last_update", lastStatusUpdateTime.get() / 1000
            );
            // Match Python: always include session header (even if empty string)
            String sessionId = ctx.getSessionId();
            sendJsonResponse(rc, 200, response, sessionId != null ? sessionId : "");
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "status", PingStatus.UNHEALTHY.getValue(),
                    "time_of_last_update", lastStatusUpdateTime.get() / 1000
            );
            sendJsonResponse(rc, 200, response, "");
        }
    }

    private PingStatus getCurrentPingStatus(RequestContext ctx) {
        // Priority 1: forced status
        PingStatus forced = forcedPingStatus.get();
        if (forced != null) {
            return forced;
        }

        // Priority 2: custom handler
        if (pingHandlerWithCtx != null) {
            PingStatus result = pingHandlerWithCtx.apply(ctx);
            if (result != null) {
                lastStatusUpdateTime.set(System.currentTimeMillis());
                return result;
            }
        }
        if (pingHandler != null) {
            PingStatus result = pingHandler.get();
            if (result != null) {
                lastStatusUpdateTime.set(System.currentTimeMillis());
                return result;
            }
        }

        // Priority 3: default — check if any tasks running
        if (activeTaskCount.get() > 0) {
            return PingStatus.HEALTHY_BUSY;
        }
        return PingStatus.HEALTHY;
    }

    // ========================
    // WS /ws
    // ========================

    private void handleWebSocketHandshake(ServerWebSocketHandshake handshake) {
        if (!"/ws".equals(handshake.path())) {
            handshake.reject(404).onFailure(error ->
                    LOG.debug("Failed to reject WebSocket handshake: {}", error.getMessage()));
            return;
        }
        handshake.accept().onFailure(error ->
                LOG.debug("WebSocket handshake failed: {}", error.getMessage()));
    }

    private void handleWebSocket(ServerWebSocket ws) {
        if (wsHandler == null) {
            ws.close((short) 1011, "No WebSocket handler registered");
            return;
        }

        RequestContext ctx = RequestContext.fromHeaders(name ->
                ws.headers().get(name));
        AgentArtsRuntimeContext.fromRequestContext(ctx);

        try {
            wsHandler.apply(ws, ctx);
        } catch (Exception e) {
            LOG.error("WebSocket error: {}", e.getMessage(), e);
            ws.close((short) 1011, e.getMessage());
        } finally {
            AgentArtsRuntimeContext.clear();
        }
    }

    // ========================
    // Response helpers
    // ========================

    private void sendJsonResponse(RoutingContext rc, int statusCode, Object data, String sessionId) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(data);
            rc.response()
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json");

            // Match Python: always include session header (even if empty string)
            rc.response().putHeader(Constants.SESSION_HEADER, sessionId != null ? sessionId : "");

            rc.response().end(json);
        } catch (JsonProcessingException e) {
            sendJsonError(rc, 500, "SerializationError", e.getMessage());
        }
    }

    private void sendJsonError(RoutingContext rc, int statusCode, String error, String message) {
        try {
            Map<String, String> body = Map.of(
                    "error", error != null ? error : "Unknown",
                    "message", message != null ? message : ""
            );
            String json = OBJECT_MAPPER.writeValueAsString(body);
            rc.response()
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(json);
        } catch (JsonProcessingException e) {
            rc.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"InternalError\",\"message\":\"Failed to serialize error\"}");
        }
    }

    private void sendSseStream(RoutingContext rc, Flux<?> flux, String sessionId) {
        rc.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "text/event-stream")
                .putHeader("Cache-Control", "no-cache")
                .putHeader("Connection", "keep-alive")
                .setChunked(true);

        if (sessionId != null) {
            rc.response().putHeader(Constants.SESSION_HEADER, sessionId);
        }

        Context context = Vertx.currentContext();
        if (context == null) {
            context = vertx.getOrCreateContext();
        }
        SseSubscriber subscriber = new SseSubscriber(rc, context);
        flux.subscribe(subscriber);
        rc.response().exceptionHandler(error -> subscriber.cancel());
        rc.request().connection().closeHandler(ignored -> subscriber.cancel());
    }

    private void sendMonoResponse(RoutingContext rc, Mono<?> mono, String sessionId) {
        Context context = Vertx.currentContext();
        if (context == null) {
            context = vertx.getOrCreateContext();
        }
        Context responseContext = context;
        Disposable subscription = mono
                .map(value -> (Object) value)
                .defaultIfEmpty(EMPTY_MONO_RESPONSE)
                .subscribe(item -> responseContext.runOnContext(ignored -> sendJsonResponse(
                                rc, 200, item == EMPTY_MONO_RESPONSE ? null : item, sessionId)),
                error -> {
                    LOG.error("Async invocation error: {}", error.getMessage(), error);
                    responseContext.runOnContext(ignored -> sendJsonError(
                            rc, 500, error.getClass().getSimpleName(), error.getMessage()));
                });
        rc.request().connection().closeHandler(ignored -> subscription.dispose());
    }

    private final class SseSubscriber extends BaseSubscriber<Object> {
        private final RoutingContext routingContext;
        private final Context context;

        private SseSubscriber(RoutingContext routingContext, Context context) {
            this.routingContext = routingContext;
            this.context = context;
        }

        @Override
        protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
            context.runOnContext(ignored -> request(1));
        }

        @Override
        protected void hookOnNext(Object value) {
            String frame;
            if (value instanceof String strValue) {
                frame = "data: " + strValue + "\n\n";
            } else {
                try {
                    frame = "data: " + OBJECT_MAPPER.writeValueAsString(value) + "\n\n";
                } catch (JsonProcessingException e) {
                    frame = "data: {\"error\":\"SerializationError\",\"message\":\""
                            + escapeJson(e.getMessage()) + "\"}\n\n";
                }
            }

            writeFrameAndRequestNext(frame);
        }

        private void writeFrameAndRequestNext(String frame) {
            context.runOnContext(ignored -> {
                HttpServerResponse response = routingContext.response();
                if (response.ended() || response.closed()) {
                    cancel();
                    return;
                }
                response.write(frame).onComplete(write -> {
                    if (write.failed()) {
                        LOG.debug("SSE client write failed: {}", write.cause().getMessage());
                        cancel();
                    } else {
                        requestWhenWritable(response);
                    }
                });
            });
        }

        private void requestWhenWritable(HttpServerResponse response) {
            if (isDisposed()) {
                return;
            }
            if (response.writeQueueFull()) {
                response.drainHandler(ignored -> {
                    response.drainHandler(null);
                    if (!isDisposed()) {
                        request(1);
                    }
                });
            } else {
                request(1);
            }
        }

        @Override
        protected void hookOnError(Throwable error) {
            context.runOnContext(ignored -> {
                HttpServerResponse response = routingContext.response();
                if (response.ended() || response.closed()) {
                    return;
                }
                try {
                    Map<String, String> errorBody = Map.of(
                            "error", error.getClass().getSimpleName(),
                            "error_type", error.getClass().getName(),
                            "message", error.getMessage() != null ? error.getMessage() : "");
                    response.write("data: " + OBJECT_MAPPER.writeValueAsString(errorBody) + "\n\n")
                            .onComplete(done -> response.end());
                } catch (JsonProcessingException serializationError) {
                    response.end();
                }
            });
        }

        @Override
        protected void hookOnComplete() {
            context.runOnContext(ignored -> {
                HttpServerResponse response = routingContext.response();
                if (!response.ended() && !response.closed()) {
                    response.end();
                }
            });
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
