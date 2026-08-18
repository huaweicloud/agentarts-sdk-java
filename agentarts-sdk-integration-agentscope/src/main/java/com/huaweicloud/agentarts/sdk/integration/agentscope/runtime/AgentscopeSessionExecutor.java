package com.huaweicloud.agentarts.sdk.integration.agentscope.runtime;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentStateStore;
import io.vertx.ext.web.Session;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Executes AgentScope calls with bounded latency and session-safe persistence.
 *
 * <p>AgentScope agents contain mutable conversation state. This executor uses a
 * fixed set of fair locks to serialize calls that map to the same session while
 * keeping memory usage bounded for long-running services. Applications should
 * create a fresh {@link ReActAgent} for each call and may safely share the model
 * and toolkit dependencies used to build it.</p>
 */
public final class AgentscopeSessionExecutor {

    private static final int LOCK_STRIPES = 256;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private final Duration timeout;
    private final AgentStateStore stateStore;
    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    public AgentscopeSessionExecutor(AgentStateStore stateStore) {
        this(stateStore, DEFAULT_TIMEOUT);
    }

    public AgentscopeSessionExecutor(AgentStateStore stateStore, Duration timeout) {
        this.timeout = requirePositive(timeout, "timeout");
        this.stateStore = Objects.requireNonNull(stateStore);
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock(true);
        }
    }

    /** Execute one user message and persist the successful resulting state. */
    public Msg call(ReActAgent agent, String message, AgentscopeRequestContext context) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(context, "context");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        Msg input = Msg.builder().role(MsgRole.USER).textContent(message).build();
        RuntimeContext ctx = context.runtimeContext();
        ReentrantLock lock = lockFor(ctx.getSessionId());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException("Timed out waiting for the AgentScope session lock");
            }
            Msg result = agent.call(List.of(input), ctx).block(timeout);
            if (result == null) {
                throw new IllegalStateException("AgentScope returned no result");
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the AgentScope session lock", e);
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    public Duration getTimeout() {
        return timeout;
    }

    private ReentrantLock lockFor(String sessionId) {
        int index = (sessionId.hashCode() & Integer.MAX_VALUE) % locks.length;
        return locks[index];
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
