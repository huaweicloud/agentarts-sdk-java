package com.huaweicloud.agentarts.spring;

import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot Actuator HealthIndicator for AgentArts Runtime.
 *
 * <p>Reports the runtime server health via {@code /actuator/health}.
 * Only activated when Spring Boot Actuator is on the classpath.</p>
 *
 * <p>Health mapping (based on the bound runtime server port):</p>
 * <ul>
 *   <li>Port bound, no running tasks → {@code UP} with detail "healthy"</li>
 *   <li>Port bound, tasks running → {@code UP} with detail "busy"</li>
 *   <li>Port not bound (server not started) → {@code UNKNOWN} with detail "not_started"</li>
 * </ul>
 */
@AutoConfiguration(after = AgentArtsAutoConfiguration.class)
@ConditionalOnClass(AbstractHealthIndicator.class)
@ConditionalOnBean(AgentArtsRuntimeApp.class)
public class AgentArtsHealthIndicatorAutoConfiguration {

    @Bean
    public AgentArtsHealthIndicator agentArtsHealthIndicator(AgentArtsRuntimeApp app) {
        return new AgentArtsHealthIndicator(app);
    }

    public static class AgentArtsHealthIndicator extends AbstractHealthIndicator {

        private final AgentArtsRuntimeApp app;

        public AgentArtsHealthIndicator(AgentArtsRuntimeApp app) {
            this.app = app;
        }

        @Override
        protected void doHealthCheck(Health.Builder builder) {
            int port = app.getPort();

            if (port > 0) {
                builder.up()
                        .withDetail("port", port)
                        .withDetail("running", true);

                if (app.hasRunningTasks()) {
                    builder.withDetail("status", "busy");
                } else {
                    builder.withDetail("status", "healthy");
                }
            } else {
                builder.unknown()
                        .withDetail("running", false)
                        .withDetail("status", "not_started");
            }
        }
    }
}
