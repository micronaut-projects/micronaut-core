/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.discovery.aws.route53.client;

import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync;
import com.amazonaws.services.servicediscovery.model.*;
import io.micronaut.configurations.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.aws.route53.AWSServiceDiscoveryResolver;
import io.micronaut.discovery.aws.route53.Route53ClientDiscoveryConfiguration;
import io.micronaut.discovery.aws.route53.Route53DiscoveryConfiguration;
import io.micronaut.discovery.aws.route53.registration.EC2ServiceInstance;
import io.micronaut.http.client.Client;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Rvanderwerf
 * @since 1.0
 */
@Singleton
@Client(id = Route53ClientDiscoveryConfiguration.SERVICE_ID, path = "/", configuration = Route53ClientDiscoveryConfiguration.class)
@Requires(env = Environment.AMAZON_EC2)
@Requires(beans = Route53DiscoveryConfiguration.class)
@Requires(beans = AWSClientConfiguration.class)
@Requires(property = "aws.route53.discovery.enabled", value = "true", defaultValue = "false")
public class Route53AutoNamingClient implements DiscoveryClient {

    AWSClientConfiguration awsClientConfiguration;

    Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration;

    AWSServiceDiscoveryResolver awsServiceDiscoveryResolver;

    Environment environment;

    AWSServiceDiscoveryAsync discoveryClient;

    public Route53AutoNamingClient(AWSClientConfiguration awsClientConfiguration,
                                   Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration,
                                   AWSServiceDiscoveryResolver awsServiceDiscoveryResolver,
                                   Environment environment) {
        this.awsClientConfiguration = awsClientConfiguration;
        this.route53ClientDiscoveryConfiguration = route53ClientDiscoveryConfiguration;
        this.awsServiceDiscoveryResolver = awsServiceDiscoveryResolver;
        this.environment = environment;

    }

    /**
     * Used to help with testing.
     * @return Route53ClientDiscoveryConfiguration
     */
    public Route53ClientDiscoveryConfiguration getRoute53ClientDiscoveryConfiguration() {
        return route53ClientDiscoveryConfiguration;
    }

    /**
     * Used to help with testing.
     * @param route53ClientDiscoveryConfiguration config class
     */
    public void setRoute53ClientDiscoveryConfiguration(Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration) {
        this.route53ClientDiscoveryConfiguration = route53ClientDiscoveryConfiguration;
    }

    /**
     * Used to help with testing.
     * @param discoveryClient discovery client class
     */
    public void setDiscoveryClient(AWSServiceDiscoveryAsync discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /**
     * This is to make it easier to replace the client with a mock.
     * @return AWSServiceDiscoveryAsync to communicate with AWS
     */
    private AWSServiceDiscoveryAsync getDiscoveryClient() {
        return awsServiceDiscoveryResolver.resolve(environment);
    }


    /**
     * Unused.
     */
    @Override
    public String getDescription() {
        return "Route 53 Auto Naming Client";
    }

    /**
     * transforms an aws result into a list of service instances.
     * @param instancesResult instance result list of a service from aws route 53
     * @return serviceInstance list that micronaut wants
     */
    private Flowable<List<ServiceInstance>> convertInstancesResulttoServiceInstances(ListInstancesResult instancesResult) {
        List<ServiceInstance> serviceInstances = new ArrayList<ServiceInstance>();
        for (InstanceSummary instanceSummary : instancesResult.getInstances()) {
            try {
                String uri = "http://" + instanceSummary.getAttributes().get("URI");
                ServiceInstance serviceInstance = new EC2ServiceInstance(instanceSummary.getId(), new URI(uri)).metadata(instanceSummary.getAttributes()).build();
                serviceInstances.add(serviceInstance);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return Flowable.just(serviceInstances);
    }

    /**
     * Gets a list of instances registered with Route53 given a service ID.
     * @param serviceId The service id
     * @return list of serviceInstances usable by MN.
     */
    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        if (serviceId == null) {
            serviceId = getRoute53ClientDiscoveryConfiguration().getAwsServiceId();  // we can default to the config file
        }
        ListInstancesRequest instancesRequest = new ListInstancesRequest().withServiceId(serviceId);
        Future<ListInstancesResult> instanceResult = getDiscoveryClient().listInstancesAsync(instancesRequest);
        Flowable<ListInstancesResult> observableInstanceResult = Flowable.fromFuture(instanceResult);
        Flowable<List<ServiceInstance>> observableInstances = observableInstanceResult.flatMap(result -> convertInstancesResulttoServiceInstances(result));
        return observableInstances;
    }

    /**
     * Gets a list of service IDs from AWS for a given namespace.
     * @return rx java publisher list of the service IDs in string format
     */
    @Override
    public Publisher<List<String>> getServiceIds() {
        ServiceFilter serviceFilter = new ServiceFilter().withName("NAMESPACE_ID").withValues(getRoute53ClientDiscoveryConfiguration().getNamespaceId());
        ListServicesRequest listServicesRequest = new ListServicesRequest().withFilters(serviceFilter);
        Future<ListServicesResult> response = getDiscoveryClient().listServicesAsync(listServicesRequest);
        Flowable<ListServicesResult> flowableList = Flowable.fromFuture(response);
        Flowable<List<String>> flowableInstanceIds = flowableList.flatMap(result -> convertServiceIds(result));
        return flowableInstanceIds;
    }

    /**
     * Converts the services IDs returned for usage in MN.
     * @param listServicesResult service result returned from AWS
     * @return RXJava publisher of the service list
     */
    private Publisher<List<String>> convertServiceIds(ListServicesResult listServicesResult) {
        List<ServiceSummary> services = listServicesResult.getServices();
        List<String> serviceIds = new ArrayList<String>();

        for (ServiceSummary service : services) {
            serviceIds.add(service.getId());
        }
        return Publishers.just(
                serviceIds
        );

    }

    /**
     * Close down AWS Client on shutdown.
     */
    @Override
    public void close() {
        getDiscoveryClient().shutdown();
    }
}
