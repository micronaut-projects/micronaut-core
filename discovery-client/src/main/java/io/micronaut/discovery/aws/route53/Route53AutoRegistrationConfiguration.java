package io.micronaut.discovery.aws.route53;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

@ConfigurationProperties("aws.route53.registration")
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
public class Route53AutoRegistrationConfiguration extends RegistrationConfiguration {
    String route53Alias;
    String dnsNamespaceType; // can be either Public or Private
    String namespaceId; //ID of the namespace if it already exists
    String awsServiceId; //ID of the service if it already exists
    String serviceName;  // name of the service that is created
    String serviceDescription; // desecrption of the service that is created
    Long dnsRecordTTL; // TTL refresh interval for the dns records



    public String getRoute53Alias() {
        return route53Alias;
    }

    public void setRoute53Alias(String route53Alias) {
        this.route53Alias = route53Alias;
    }

    public String getDnsNamespaceType() {
        return dnsNamespaceType;
    }

    public void setDnsNamespaceType(String dnsNamespaceType) {
        this.dnsNamespaceType = dnsNamespaceType;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    public String getAwsServiceId() {
        return awsServiceId;
    }

    public void setAwsServiceId(String awsServiceId) {
        this.awsServiceId = awsServiceId;
    }

    public Long getDnsRecordTTL() {
        return dnsRecordTTL;
    }

    public void setDnsRecordTTL(Long dnsRecordTTL) {
        this.dnsRecordTTL = dnsRecordTTL;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }
}
