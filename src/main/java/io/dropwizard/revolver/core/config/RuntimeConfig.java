package io.dropwizard.revolver.core.config;

import io.dropwizard.revolver.core.config.hystrix.CircuitBreakerConfig;
import io.dropwizard.revolver.core.config.hystrix.MetricsConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author phaneesh
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RuntimeConfig {

    private ThreadPoolConfig threadPool = new ThreadPoolConfig();

    public MetricsConfig getMetrics()
    {
        return this.metrics;
    }

    private MetricsConfig metrics = new MetricsConfig();

    public CircuitBreakerConfig getCircuitBreaker()
    {
        return this.circuitBreaker;
    }

    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
}