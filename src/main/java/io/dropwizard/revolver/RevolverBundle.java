/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dropwizard.revolver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.google.common.collect.ImmutableMap;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet;
import com.netflix.hystrix.strategy.HystrixPlugins;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.msgpack.MsgPackBundle;
import io.dropwizard.revolver.core.RevolverExecutionException;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.discovery.RevolverServiceResolver;
import io.dropwizard.revolver.discovery.model.RangerEndpointSpec;
import io.dropwizard.revolver.discovery.model.SimpleEndpointSpec;
import io.dropwizard.revolver.handler.RevolverCallbackRequestFilter;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.auth.BasicAuthConfig;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.model.ApiPathMap;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.resource.RevolverCallbackResource;
import io.dropwizard.revolver.resource.RevolverRequestResource;
import io.dropwizard.revolver.resource.RevolverRequestStatusResource;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.xml.XmlBundle;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author phaneesh
 */
@Slf4j
public abstract class RevolverBundle<T extends Configuration> implements ConfiguredBundle<T> {

    private static Map<String, RevolverHttpCommand> httpCommands = new HashMap<>();

    private static MultivaluedMap<String, ApiPathMap> serviceToPathMap = new MultivaluedHashMap<>();

    private static final ObjectMapper msgPackObjectMapper = new ObjectMapper(new MessagePackFactory());

    private static final XmlMapper xmlObjectMapper = new XmlMapper();


    @Override
    public void initialize(final Bootstrap<?> bootstrap) {
        registerTypes(bootstrap);
        configureXmlMapper();
        bootstrap.addBundle(new XmlBundle());
        bootstrap.addBundle(new MsgPackBundle());
        if(HystrixPlugins.getInstance().getMetricsPublisher() == null) {
            val publisher = new HystrixCodaHaleMetricsPublisher(bootstrap.getMetricRegistry());
            HystrixPlugins.getInstance().registerMetricsPublisher(publisher);
        }
    }

    @Override
    public void run(final T configuration, final Environment environment) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        initializeRevolver(configuration, environment);
        environment.getApplicationContext().addServlet(HystrixMetricsStreamServlet.class, getRevolverConfig(configuration).getHystrixStreamPath());
        environment.jersey().register(new RevolverCallbackRequestFilter());
        environment.jersey().register(new RevolverRequestResource(environment.getObjectMapper(), msgPackObjectMapper, xmlObjectMapper, getPersistenceProvider()));
        environment.jersey().register(new RevolverCallbackResource(getPersistenceProvider()));
        environment.jersey().register(new RevolverRequestStatusResource(getPersistenceProvider()));
    }


    private void registerTypes(final Bootstrap<?> bootstrap) {
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(RevolverHttpServiceConfig.class, "http"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(RevolverHttpRequest.class, "http"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(BasicAuthConfig.class, "basic"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(SimpleEndpointSpec.class, "simple"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(RangerEndpointSpec.class, "ranger_sharded"));
    }

    private void configureXmlMapper() {
        xmlObjectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        xmlObjectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        xmlObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        xmlObjectMapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, true);
        xmlObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        xmlObjectMapper.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, true);
    }

    private Map<String, RevolverHttpApiConfig> generateApiConfigMap(final RevolverHttpServiceConfig serviceConfiguration) {
        serviceConfiguration.getApis().forEach( apiConfig -> serviceToPathMap.add(serviceConfiguration.getService(),
                ApiPathMap.builder()
                        .api(apiConfig.getApi())
                        .path(generatePathExpression(apiConfig.getPath())).build()));
        final ImmutableMap.Builder<String, RevolverHttpApiConfig> configMapBuilder = ImmutableMap.builder();
        serviceConfiguration.getApis().forEach(apiConfig -> configMapBuilder.put(apiConfig.getApi(), apiConfig));
        return configMapBuilder.build();
    }

    private String generatePathExpression(final String path) {
        return path.replaceAll("\\{(([^/])+\\})", "(([^/])+)");
    }

    public static ApiPathMap matchPath(final String service, final String path) {
        if(serviceToPathMap.containsKey(service)) {
           final val apiMap = serviceToPathMap.get(service).stream().filter(api -> path.matches(api.getPath())).findFirst();
           return apiMap.orElse(null);
        } else {
            return null;
        }
    }

    public static RevolverHttpCommand getHttpCommand(final String service) {
        val command = httpCommands.get(service);
        if (null == command) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST, "No service spec defined for service: " + service);
        }
        return command;
    }

    public abstract RevolverConfig getRevolverConfig(final T configuration);

    public abstract PersistenceProvider getPersistenceProvider();

    private void initializeRevolver(final T configuration, final Environment environment) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        val revolverConfig = getRevolverConfig(configuration);
        final RevolverServiceResolver serviceNameResolver = new RevolverServiceResolver(revolverConfig.getServiceResolverConfig(), environment.getObjectMapper());
        for (final RevolverServiceConfig config : revolverConfig.getServices()) {
            final String type = config.getType();
            switch (type) {
                case "http":
                case "https":
                    final RevolverHttpServiceConfig httpConfig = (RevolverHttpServiceConfig)config;
                    httpCommands.put(config.getService(), RevolverHttpCommand.builder()
                            .clientConfiguration(revolverConfig.getClientConfig())
                            .runtimeConfig(revolverConfig.getGlobal())
                            .serviceConfiguration(httpConfig).apiConfigurations(generateApiConfigMap(httpConfig))
                            .serviceResolver(serviceNameResolver)
                            .traceCollector(trace -> {
                                //TODO: Put in a publisher if required
                            }).build());
                    break;
                default:
                    log.warn("Unsupported Service type: " +type);

            }
        }
    }

}