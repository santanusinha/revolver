package io.dropwizard.revolver;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.Configuration;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.jetty.MutableServletContextHandler;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.revolver.core.config.ClientConfig;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RuntimeConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.discovery.model.SimpleEndpointSpec;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.Before;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author phaneesh
 */
@Slf4j
public class BaseRevolverTest {

    protected final HealthCheckRegistry healthChecks = mock(HealthCheckRegistry.class);
    protected final JerseyEnvironment jerseyEnvironment = mock(JerseyEnvironment.class);
    protected final LifecycleEnvironment lifecycleEnvironment = new LifecycleEnvironment();
    protected final Environment environment = mock(Environment.class);
    protected final Bootstrap<?> bootstrap = mock(Bootstrap.class);
    protected final Configuration configuration = mock(Configuration.class);

    protected final InMemoryPersistenceProvider inMemoryPersistenceProvider = new InMemoryPersistenceProvider();

    protected final RevolverBundle<Configuration> bundle = new RevolverBundle<Configuration>() {

        @Override
        public RevolverConfig getRevolverConfig(final Configuration configuration) {
            return revolverConfig;
        }

        @Override
        public PersistenceProvider getPersistenceProvider() {
            return inMemoryPersistenceProvider;
        }
    };

    protected RevolverConfig revolverConfig;

    @Before
    public void setup() throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException,
            KeyStoreException, KeyManagementException, IOException, InterruptedException {
        when(jerseyEnvironment.getResourceConfig()).thenReturn(new DropwizardResourceConfig());
        when(environment.jersey()).thenReturn(jerseyEnvironment);
        when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
        when(environment.healthChecks()).thenReturn(healthChecks);
        when(environment.getObjectMapper()).thenReturn(new ObjectMapper());
        when(bootstrap.getObjectMapper()).thenReturn(new ObjectMapper());
        when(environment.getApplicationContext()).thenReturn(new MutableServletContextHandler());

        val simpleEndpoint = new SimpleEndpointSpec();
        simpleEndpoint.setHost("localhost");
        simpleEndpoint.setPort(9999);

        revolverConfig = RevolverConfig.builder()
                .clientConfig(ClientConfig.builder()
                        .clientName("test-client")
                        .build()
                )
                .global(new RuntimeConfig())
                .service(RevolverHttpServiceConfig.builder()
                        .authEnabled(false)
                        .connectionPoolSize(1)
                        .secured(false)
                        .enpoint(simpleEndpoint)
                        .service("test")
                        .type("http")
                        .api(RevolverHttpApiConfig.configBuilder()
                                .api("test")
                                .method(RevolverHttpApiConfig.RequestMethod.GET)
                                .method(RevolverHttpApiConfig.RequestMethod.POST)
                                .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                .path("{version}/test")
                                .runtime(HystrixCommandConfig.builder()
                                        .threadPool(ThreadPoolConfig.builder()
                                                .concurrency(1).timeout(2000)
                                                .build())
                                        .build()).build())
                        .api(RevolverHttpApiConfig.configBuilder()
                                .api("test_multi")
                                .method(RevolverHttpApiConfig.RequestMethod.GET)
                                .method(RevolverHttpApiConfig.RequestMethod.POST)
                                .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                .path("{version}/test/{operation")
                                .runtime(HystrixCommandConfig.builder()
                                        .threadPool(ThreadPoolConfig.builder()
                                                .concurrency(1).timeout(2000)
                                                .build())
                                        .build()).build())
                        .build())
                .build();

        bundle.initialize(bootstrap);

        bundle.run(configuration, environment);

        lifecycleEnvironment.getManagedObjects().forEach(object -> {
            try {
                object.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}