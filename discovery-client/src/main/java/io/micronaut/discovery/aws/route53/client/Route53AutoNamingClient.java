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

import com.amazonaws.SdkClientException;
import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsyncClientBuilder;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryClient;
import com.amazonaws.services.servicediscovery.model.*;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import io.micronaut.configurations.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.aws.route53.Route53ClientDiscoveryConfiguration;
import io.micronaut.discovery.aws.route53.Route53DiscoveryConfiguration;
import io.micronaut.discovery.aws.route53.registration.EC2ServiceInstance;
import io.micronaut.http.client.Client;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import javafx.collections.ObservableList;
import org.reactivestreams.Publisher;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Inject
    AWSClientConfiguration awsClientConfiguration;

    @Inject
    Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration;

    @Inject
    Route53DiscoveryConfiguration route53DiscoveryConfiguration;

    AWSServiceDiscoveryAsync discoveryClient;

    public Route53ClientDiscoveryConfiguration getRoute53ClientDiscoveryConfiguration() {
        return route53ClientDiscoveryConfiguration;
    }

    public void setRoute53ClientDiscoveryConfiguration(Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration) {
        this.route53ClientDiscoveryConfiguration = route53ClientDiscoveryConfiguration;
    }

    public void setDiscoveryClient(AWSServiceDiscoveryAsync discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    public AWSServiceDiscoveryAsync getDiscoveryClient() {
        return discoveryClient;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @PostConstruct
    private void init() {
        discoveryClient = AWSServiceDiscoveryAsyncClientBuilder.standard().withClientConfiguration(awsClientConfiguration.getClientConfiguration()).build();
    }

    /*@Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        Publisher<ListInstancesResult> instancesResultPublisher = getAwsInstances(serviceId);
        instancesResultPublisher.subscribe();

    }*/


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

    @Override
    public Publisher<List<String>> getServiceIds() {
        ServiceFilter serviceFilter = new ServiceFilter().withName("NAMESPACE_ID").withValues(getRoute53ClientDiscoveryConfiguration().getNamespaceId());
        ListServicesRequest listServicesRequest = new ListServicesRequest().withFilters(serviceFilter);
        Future<ListServicesResult> response = getDiscoveryClient().listServicesAsync(listServicesRequest);
        Flowable<ListServicesResult> flowableList = Flowable.fromFuture(response);
        Flowable<List<String>> flowableInstanceIds = flowableList.flatMap(result -> convertServiceIds(result));
        return flowableInstanceIds;
    }

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

    @Override
    public void close() throws IOException {
        getDiscoveryClient().shutdown();
    }
}
