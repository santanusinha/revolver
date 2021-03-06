/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.dropwizard.revolver.resource;

import com.codahale.metrics.annotation.Metered;
import com.flipkart.ranger.healthcheck.HealthcheckStatus;
import com.flipkart.ranger.model.ServiceNode;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.model.RevolverApiMetadata;
import io.dropwizard.revolver.core.model.RevolverMetadataResponse;
import io.dropwizard.revolver.core.model.RevolverServiceMetadata;
import io.dropwizard.revolver.discovery.RevolverServiceResolver;
import io.dropwizard.revolver.discovery.model.RangerEndpointSpec;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.swagger.annotations.ApiOperation;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author phaneesh
 */
@Path("/revolver")
@Slf4j
@Data
@Singleton
public class RevolverMetadataResource {

    public static final String UNKNOWN = "UNKNOWN";
    private RevolverConfig config;

    @Builder
    public RevolverMetadataResource(final RevolverConfig config) {
        this.config = config;
    }

    @Path("/v1/metadata/status")
    @GET
    @Metered
    @ApiOperation(value = "Get the status & metadata of revolver api gateway")
    @Produces(MediaType.APPLICATION_JSON)
    public RevolverMetadataResponse status() {
        final RevolverMetadataResponse.RevolverMetadataResponseBuilder metadataResponse = RevolverMetadataResponse.builder();
        metadataResponse.clientId(config.getClientConfig().getClientName());
        final List<RevolverHttpServiceConfig> services = config.getServices().stream()
                .filter(service -> service instanceof RevolverHttpServiceConfig)
                .map(service -> ((RevolverHttpServiceConfig)service))
                .sorted(Comparator.comparing(RevolverServiceConfig::getService)).collect(Collectors.toList());
        services.forEach( s -> {
            RevolverServiceMetadata.RevolverServiceMetadataBuilder serviceMetadataBuilder = RevolverServiceMetadata.builder();
            serviceMetadataBuilder.name(s.getService())
                    .type(s.getType())
                    .apis(apiMetadataList(s));
            if (s.getEndpoint() instanceof RangerEndpointSpec) {
                instanceStats((RangerEndpointSpec) s.getEndpoint(), serviceMetadataBuilder);
            } else {
                serviceMetadataBuilder.status(UNKNOWN);
            }
            metadataResponse.service(serviceMetadataBuilder.build());
        });
        return metadataResponse.build();
    }

    @Path("/v1/metadata/service/ranger/{service}")
    @GET
    @Metered
    @ApiOperation(value = "Get the status & metadata of a service registered in api")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ServiceNode<RevolverServiceResolver.ShardInfo>> serviceStatus(@PathParam("service") String service) {
        RevolverServiceResolver serviceResolver = RevolverBundle.getServiceNameResolver();
        RevolverServiceResolver.ShardedServiceDiscoveryInfo serviceInfo = serviceResolver.getServiceFinders().getOrDefault(service, null);
        if(serviceInfo == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return serviceInfo.getShardFinder()
                .getAll(new RevolverServiceResolver.ShardInfo(serviceInfo.getEnvironment()));
    }


    @Path("/v1/metadata/config")
    @GET
    @Metered
    @ApiOperation(value = "Get configuration of revolver api gateway")
    @Produces(MediaType.APPLICATION_JSON)
    public RevolverConfig config() {
        return config;
    }

    private List<RevolverApiMetadata> apiMetadataList(RevolverHttpServiceConfig httpServiceConfig) {
        return httpServiceConfig.getApis().parallelStream().map( a -> RevolverApiMetadata.builder()
                .async(a.isAsync())
                .name(a.getApi())
                .path(a.getPath())
                .methods(a.getMethods())
                .secured(!a.isWhitelist())
                .build()).sorted(Comparator.comparing(RevolverApiMetadata::getPath)).collect(Collectors.toList());
    }

    private void instanceStats(RangerEndpointSpec endpoint, RevolverServiceMetadata.RevolverServiceMetadataBuilder serviceMetadataBuilder) {
        RevolverServiceResolver serviceResolver = RevolverBundle.getServiceNameResolver();
        if(serviceResolver == null) {
            serviceMetadataBuilder.status(UNKNOWN);
        } else {
            if(serviceResolver.getServiceFinders().containsKey(endpoint.getService())) {
                List<ServiceNode<RevolverServiceResolver.ShardInfo>> serviceNodes = serviceResolver.getServiceFinders()
                        .get(endpoint.getService()).getShardFinder()
                        .getAll(new RevolverServiceResolver.ShardInfo(endpoint.getEnvironment()));
                long healthy =  serviceNodes.parallelStream().filter( n -> n.getHealthcheckStatus() == HealthcheckStatus.healthy).count();
                serviceMetadataBuilder.instances(serviceNodes.size())
                        .healthy(healthy)
                        .unhealthy(serviceNodes.size() - healthy);
                serviceMetadataBuilder.status(healthy > 0 ?  "HEALTHY" : UNKNOWN);
            } else {
                serviceMetadataBuilder.status(UNKNOWN);
            }
        }
    }

}
