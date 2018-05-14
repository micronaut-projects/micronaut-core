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

package io.micronaut.discovery.aws.route53.registration;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryClient;
import com.amazonaws.services.servicediscovery.model.CreatePublicDnsNamespaceRequest;
import com.amazonaws.services.servicediscovery.model.CreatePublicDnsNamespaceResult;
import com.amazonaws.services.servicediscovery.model.CreateServiceRequest;
import com.amazonaws.services.servicediscovery.model.CreateServiceResult;
import com.amazonaws.services.servicediscovery.model.CustomHealthStatus;
import com.amazonaws.services.servicediscovery.model.DeleteNamespaceRequest;
import com.amazonaws.services.servicediscovery.model.DeleteServiceRequest;
import com.amazonaws.services.servicediscovery.model.DeregisterInstanceRequest;
import com.amazonaws.services.servicediscovery.model.DnsConfig;
import com.amazonaws.services.servicediscovery.model.DnsRecord;
import com.amazonaws.services.servicediscovery.model.GetOperationRequest;
import com.amazonaws.services.servicediscovery.model.GetOperationResult;
import com.amazonaws.services.servicediscovery.model.RecordType;
import com.amazonaws.services.servicediscovery.model.RegisterInstanceRequest;
import com.amazonaws.services.servicediscovery.model.RegisterInstanceResult;
import com.amazonaws.services.servicediscovery.model.RoutingPolicy;
import com.amazonaws.services.servicediscovery.model.Service;
import com.amazonaws.services.servicediscovery.model.UpdateInstanceCustomHealthStatusRequest;
import io.micronaut.configurations.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceIdGenerator;
import io.micronaut.discovery.aws.route53.Route53AutoRegistrationConfiguration;
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.aws.AmazonComputeInstanceMetadataResolver;
import io.micronaut.health.HealthStatus;
import io.micronaut.health.HeartbeatConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Rvanderwerf
 * @since 1.0
 */
@Singleton
@Requires(env = Environment.AMAZON_EC2)
@Requires(beans = {Route53AutoRegistrationConfiguration.class})
@Requires(property = "aws.route53.registration.enabled", value = "true", defaultValue = "false")
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
public class Route53AutoNamingRegistrationClient extends DiscoveryServiceAutoRegistration {

    /**
     * Constant for AWS instance port.
     */
    public static final String AWS_INSTANCE_PORT = "AWS_INSTANCE_PORT";

    /**
     * Constant for AWS intance IPv4.
     */
    public static final String AWS_INSTANCE_IPV4 = "AWS_INSTANCE_IPV4";

    /**
     * Constant for AWS instance cname.
     */
    public static final String AWS_INSTANCE_CNAME = "AWS_INSTANCE_CNAME";

    /**
     * Constant for AWS instance IPv6.
     */
    public static final String AWS_INSTANCE_IPV6 = "AWS_INSTANCE_IPV6";

    /**
     * Constant for AWS alias dns name.
     */
    public static final String AWS_ALIAS_DNS_NAME = "AWS_ALIAS_DNS_NAME";
    private static final Logger LOG = LoggerFactory.getLogger(Route53AutoNamingRegistrationClient.class);

    private final Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration;
    private final Environment environment;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ServiceInstanceIdGenerator idGenerator;
    private final AWSClientConfiguration clientConfiguration;
    private AWSServiceDiscovery discoveryClient;
    private final AmazonComputeInstanceMetadataResolver amazonComputeInstanceMetadataResolver;
    private Service discoveryService;



    /**
     * Constructor for setup.
     * @param environment current environemnts
     * @param heartbeatConfiguration heartbeat config
     * @param route53AutoRegistrationConfiguration  config for auto registration
     * @param idGenerator optional id generator (not used here)
     * @param clientConfiguration general client configuraiton
     * @param amazonComputeInstanceMetadataResolver resolver for aws compute metdata
     */
    protected Route53AutoNamingRegistrationClient(
            Environment environment,
            HeartbeatConfiguration heartbeatConfiguration,
            Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration,
            ServiceInstanceIdGenerator idGenerator,
            AWSClientConfiguration clientConfiguration,
            AmazonComputeInstanceMetadataResolver amazonComputeInstanceMetadataResolver) {
        super(route53AutoRegistrationConfiguration);
        this.environment = environment;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.route53AutoRegistrationConfiguration = route53AutoRegistrationConfiguration;
        this.idGenerator = idGenerator;
        this.clientConfiguration = clientConfiguration;
        try {
            this.discoveryClient = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.getClientConfiguration()).build();
        } catch (SdkClientException ske) {
            LOG.warn("Warning: cannot find any AWS credentials. Please verify your configuration.", ske);
        }
        this.amazonComputeInstanceMetadataResolver = amazonComputeInstanceMetadataResolver;
    }

    /**
     * If custom health check is enabled, this sends a heartbeat to it.
     * In most cases aws monitoring works off polling an application's endpoint
     * @param instance The instance of the service
     * @param status   The {@link HealthStatus}
     */
    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {
        // this only work if you create a health status check when you register it
        // we can't really pulsate anywhere because amazon health checks work inverse from this UNLESS you have a custom health check
        if (discoveryService.getHealthCheckCustomConfig() != null) {
            CustomHealthStatus customHealthStatus = CustomHealthStatus.UNHEALTHY;
            if (status.getOperational().isPresent()) {
                customHealthStatus = CustomHealthStatus.HEALTHY;
            }
            discoveryClient.updateInstanceCustomHealthStatus(new UpdateInstanceCustomHealthStatusRequest().withInstanceId(instance.getInstanceId().get()).withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId()).withStatus(customHealthStatus));
        }

        if (status.getOperational().isPresent() && !status.getOperational().get()) {
            discoveryClient.deregisterInstance(new DeregisterInstanceRequest().withInstanceId(instance.getInstanceId().get()).withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId()));
            LOG.info("Health status is non operational, instance id " + instance.getInstanceId().get() + " was de-registered from the discovery service.");
        }
    }

    @Override
    /**
     * shutdown instance if it fails health check can gracefully stop.
     */
    protected void deregister(ServiceInstance instance) {

        if (instance.getInstanceId().isPresent()) {
            DeregisterInstanceRequest deregisterInstanceRequest = new DeregisterInstanceRequest().withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId())
                    .withInstanceId(instance.getInstanceId().get());
            discoveryClient.deregisterInstance(deregisterInstanceRequest);
        }
    }

    /**
     * register new instance to the service registry.
     * @param instance The {@link ServiceInstance}
     */
    @Override
    protected void register(ServiceInstance instance) {
        // step 1 get domain from config
        // set service from config
        // check if service exists
        // register service if not
        // register instance to service

        Map<String, String> instanceAttributes = new HashMap<String, String>();

        // you can't just put anything in there like a custom config. Only certain things are allowed or you get weird errors
        // see https://docs.aws.amazon.com/Route53/latest/APIReference/API_autonaming_RegisterInstance.html
        //if the service uses A records use these
        if (instance.getPort() > 0) {
            instanceAttributes.put("AWS_INSTANCE_PORT", Integer.toString(instance.getPort()));
        }
        if (amazonComputeInstanceMetadataResolver != null) {
            Optional<ComputeInstanceMetadata> instanceMetadata = amazonComputeInstanceMetadataResolver.resolve(environment);
            if (instanceMetadata.isPresent()) {
                ComputeInstanceMetadata computeInstanceMetadata = instanceMetadata.get();
                if (computeInstanceMetadata.getPublicIpV4() != null) {
                    instanceAttributes.put(AWS_INSTANCE_IPV4, computeInstanceMetadata.getPublicIpV4());
                } else {
                    if (computeInstanceMetadata.getPrivateIpV4() != null) {
                        instanceAttributes.put(AWS_INSTANCE_IPV4, computeInstanceMetadata.getPrivateIpV4());
                    }
                }

                if (!instanceAttributes.containsKey(AWS_INSTANCE_IPV4)) {
                    // try ip v6
                    if (computeInstanceMetadata.getPublicIpV4() != null) {
                        instanceAttributes.put(AWS_INSTANCE_IPV6, computeInstanceMetadata.getPublicIpV6());
                    } else {
                        if (computeInstanceMetadata.getPrivateIpV6() != null) {
                            instanceAttributes.put(AWS_INSTANCE_IPV6, computeInstanceMetadata.getPrivateIpV6());
                        }
                    }
                }
            }
        }

        assert instance.getInstanceId().isPresent();
        RegisterInstanceRequest instanceRequest = new RegisterInstanceRequest().withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId())
                .withInstanceId(instance.getInstanceId().get()).withCreatorRequestId(Long.toString(System.nanoTime())).withAttributes(instanceAttributes);

        RegisterInstanceResult instanceResult = discoveryClient.registerInstance(instanceRequest);
        GetOperationResult opResult = checkOperation(instanceResult.getOperationId());

        assert opResult.getOperation().getStatus().equals("SUCCESS");
    }

    /**
     * These are convenience methods to help cleanup things like integration test data.
     *
     * @param serviceId service id from AWS to delete
     */
    public void deleteService(String serviceId) {

        DeleteServiceRequest deleteServiceRequest = new DeleteServiceRequest().withId(serviceId);
        discoveryClient.deleteService(deleteServiceRequest);

    }

    /**
     * these are convenience methods to help cleanup things like integration test data.
     *
     * @param namespaceId namespace ID from AWS to delete
     */
    public void deleteNamespace(String namespaceId) {

        DeleteNamespaceRequest deleteNamespaceRequest = new DeleteNamespaceRequest().withId(namespaceId);
        discoveryClient.deleteNamespace(deleteNamespaceRequest);

    }

    /**
     * This is a helper method for integration tests to create a new namespace.
     * Normally you would do this yourself with your own domain/subdomain on route53.
     *
     * @param serviceDiscovery service discovery object
     * @param name name of the namespace in your route53
     * @return id of the namespace
     */
    public String createNamespace(AWSServiceDiscovery serviceDiscovery, String name) {
        if (serviceDiscovery == null) {
            serviceDiscovery = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.getClientConfiguration()).build();
        }
        String requestId = Long.toString(System.nanoTime());

        CreatePublicDnsNamespaceRequest publicDnsNamespaceRequest =
                new CreatePublicDnsNamespaceRequest().withCreatorRequestId(requestId)
                        .withName(name)
                        .withDescription("test");
        //TODO switch to async version
        CreatePublicDnsNamespaceResult clientResult = discoveryClient.createPublicDnsNamespace(publicDnsNamespaceRequest);
        String operationId = clientResult.getOperationId();


        GetOperationResult opResult = checkOperation(operationId);
        return opResult.getOperation().getTargets().get("NAMESPACE");
    }

    /**
     * Create service, helper for integration tests.
     * @param serviceDiscovery service discovery instance
     * @param name name of the service
     * @param description description of the service
     * @param namespaceId namespaceId to attach it to (get via cli or api call)
     * @param ttl time to live for checking pulse
     * @return serviceId
     */
    public String createService(AWSServiceDiscovery serviceDiscovery, String name, String description, String namespaceId, Long ttl) {
        if (serviceDiscovery == null) {
            serviceDiscovery = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.getClientConfiguration()).build();
        }
        DnsRecord dnsRecord = new DnsRecord().withType(RecordType.A).withTTL(ttl);
        DnsConfig dnsConfig = new DnsConfig().withDnsRecords(dnsRecord).withNamespaceId(namespaceId).withRoutingPolicy(RoutingPolicy.WEIGHTED);
        CreateServiceRequest createServiceRequest = new CreateServiceRequest().withDnsConfig(dnsConfig)
                .withDescription(description)
                .withName(name);
        CreateServiceResult servicerResult = serviceDiscovery.createService(createServiceRequest);
        Service createdService = servicerResult.getService();
        return createdService.getId();
    }

    /**
     * loop for checking of the call to aws is complete or not. Need to replace with RxJava/future call.
     * @param operationId operation ID we are polling for
     * @return result of the operation, can be success or failure or ongoing
     */
    private GetOperationResult checkOperation(String operationId) {
        String result = "";
        GetOperationResult opResult = null;
        try {
            while (!result.equals("SUCCESS") && !result.equals("FAIL")) {
                opResult = discoveryClient.getOperation(new GetOperationRequest().withOperationId(operationId));
                result = opResult.getOperation().getStatus();
                if (opResult.getOperation().getStatus().equals("SUCCESS")) {
                    LOG.info("Successfully get operation id " + operationId);
                    return opResult;
                } else {
                    if (opResult.getOperation().getStatus().equals("FAIL")) {
                        LOG.error("Error calling aws service for operationId:" + operationId + " error code:" + opResult.getOperation().getErrorCode() + " error message:" + opResult.getOperation().getErrorMessage());
                        return opResult;
                    }
                }
                Thread.currentThread().sleep(1000); // if you call this to much amazon will rate limit you
            }
        } catch (InterruptedException e) {
            LOG.error("Error polling for aws response operation:", e);
        }
        return opResult;
    }
}
