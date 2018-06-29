package io.micronaut.discovery.route53

import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsyncClientBuilder
import io.micronaut.context.annotation.Replaces
import io.micronaut.discovery.aws.route53.Route53ClientDiscoveryConfiguration
import io.micronaut.discovery.aws.route53.client.Route53AutoNamingClient

import javax.annotation.PostConstruct

@Replaces(Route53AutoNamingClient)
class Route53AutoNamingClientMock extends Route53AutoNamingClient {


    String namespaceId = "asdb123"
    String serviceId = "123abcdf"

    Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration = new Route53ClientDiscoveryConfiguration()


    Route53ClientDiscoveryConfiguration getRoute53ClientDiscoveryConfiguration() {
        route53ClientDiscoveryConfiguration = new Route53ClientDiscoveryConfiguration()
        route53ClientDiscoveryConfiguration.awsServiceId = serviceId
        route53ClientDiscoveryConfiguration.namespaceId = namespaceId
        return this.route53ClientDiscoveryConfiguration
    }

    @PostConstruct
    private void init() {
        if (discoveryClient == null) {
            discoveryClient = new AWSServiceDiscoveryAsyncMock()
        }
    }





    AWSServiceDiscoveryAsync getDiscoveryClient() {
        return new AWSServiceDiscoveryAsyncMock()
    }

}