/*
 * Copyright 2017-2020 original authors
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

import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryClient;
import com.amazonaws.services.servicediscovery.model.*;
import io.micronaut.configuration.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.aws.route53.AWSServiceDiscoveryResolver;
import io.micronaut.discovery.aws.route53.Route53AutoRegistrationConfiguration;
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.aws.AmazonComputeInstanceMetadataResolver;
import io.micronaut.health.HealthStatus;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.Flowable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;


/**
 * An implementation of {@link DiscoveryServiceAutoRegistration} for Route 53.
 *
 * @author Rvanderwerf
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = com.amazonaws.services.servicediscovery.AWSServiceDiscovery.class)
@Requires(env = Environment.AMAZON_EC2)
@Requires(beans = {Route53AutoRegistrationConfiguration.class})
@Requires(property = Route53AutoNamingRegistrationClient.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
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
    /**
     * Constant for whether route 53 registration is enabled.
     */
    public static final String ENABLED = "aws.route53.registration.enabled";

    private static final Logger LOG = LoggerFactory.getLogger(Route53AutoNamingRegistrationClient.class);
    private final Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration;
    private final Environment environment;
    private final AWSClientConfiguration clientConfiguration;
    private AmazonComputeInstanceMetadataResolver amazonComputeInstanceMetadataResolver;
    private Service discoveryService;
    private Executor executorService;
    private AWSServiceDiscoveryResolver awsServiceDiscoveryResolver;




    /**
     * Constructor for setup.
     * @param environment current environemnts
     * @param route53AutoRegistrationConfiguration  config for auto registration
     * @param clientConfiguration general client configuraiton
     * @param amazonComputeInstanceMetadataResolver resolver for aws compute metdata
     * @param executorService this is for executing the thread to monitor the register operation for completion
     * @param awsServiceDiscoveryResolver this allows is to swap out the bean for a mock version for unit testing
     */
    protected Route53AutoNamingRegistrationClient(
            Environment environment,
            Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration,
            AWSClientConfiguration clientConfiguration,
            AmazonComputeInstanceMetadataResolver amazonComputeInstanceMetadataResolver,
            @Named(TaskExecutors.IO) Executor executorService,
            AWSServiceDiscoveryResolver awsServiceDiscoveryResolver) {
        super(route53AutoRegistrationConfiguration);
        this.environment = environment;
        this.route53AutoRegistrationConfiguration = route53AutoRegistrationConfiguration;
        this.clientConfiguration = clientConfiguration;
        this.awsServiceDiscoveryResolver = awsServiceDiscoveryResolver;
        this.amazonComputeInstanceMetadataResolver = amazonComputeInstanceMetadataResolver;
        this.executorService = executorService;
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
        Optional<String> opt = instance.getInstanceId();
        if (!opt.isPresent()) {
            // try the metadata
            if (instance.getMetadata().contains("instanceId")) {
                opt = Optional.of(instance.getMetadata().asMap().get("instanceId"));
            } else {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Cannot determine the instance ID. Are you sure you are running on AWS EC2?");
                }
            }
        }

        opt.ifPresent(instanceId -> {
            if (discoveryService != null && discoveryService.getHealthCheckCustomConfig() != null) {
                CustomHealthStatus customHealthStatus = CustomHealthStatus.UNHEALTHY;

                if (status.getOperational().isPresent()) {
                    customHealthStatus = CustomHealthStatus.HEALTHY;
                }

                UpdateInstanceCustomHealthStatusRequest updateInstanceCustomHealthStatusRequest = new UpdateInstanceCustomHealthStatusRequest()
                                                                                                            .withInstanceId(instanceId)
                                                                                                            .withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId())
                                                                                                            .withStatus(customHealthStatus);
                getDiscoveryClient().updateInstanceCustomHealthStatus(
                        updateInstanceCustomHealthStatusRequest);
            }

            if (status.getOperational().isPresent() && !status.getOperational().get()) {
                getDiscoveryClient().deregisterInstance(new DeregisterInstanceRequest().withInstanceId(instanceId).withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId()));
                LOG.info("Health status is non operational, instance id " + instanceId + " was de-registered from the discovery service.");
            }

        });

    }


    /**
     * shutdown instance if it fails health check can gracefully stop.
     */
    @Override
    protected void deregister(ServiceInstance instance) {

        if (instance.getInstanceId().isPresent()) {
            DeregisterInstanceRequest deregisterInstanceRequest = new DeregisterInstanceRequest().withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId())
                    .withInstanceId(instance.getInstanceId().get());
            getDiscoveryClient().deregisterInstance(deregisterInstanceRequest);
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

        Map<String, String> instanceAttributes = new HashMap<>();

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


        ConvertibleValues<String> metadata = instance.getMetadata();

        String instanceId = null;
        if (instance.getInstanceId().isPresent()) {
            instanceId = instance.getInstanceId().get();
        } else {
            // try the metadata
            if (metadata.contains("instanceId")) {
                instanceId = metadata.asMap().get("instanceId");
            } else {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Cannot determine the instance ID. Are you sure you are running on AWS EC2?");
                }
            }
        }
        RegisterInstanceRequest instanceRequest = new RegisterInstanceRequest().withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId())
                .withInstanceId(instanceId).withCreatorRequestId(Long.toString(System.nanoTime())).withAttributes(instanceAttributes);

        Future<RegisterInstanceResult> instanceResult = getDiscoveryClient().registerInstanceAsync(instanceRequest);
        Flowable<RegisterInstanceResult> flowableResult = Flowable.fromFuture(instanceResult);


        //noinspection SubscriberImplementation
        flowableResult.subscribe(new Subscriber<RegisterInstanceResult>() {

            @Override
            public void onNext(RegisterInstanceResult registerInstanceResult) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Called AWS to register service [{}] with {}", instance.getId(), route53AutoRegistrationConfiguration.getAwsServiceId());
                }
                if (registerInstanceResult.getOperationId() != null) {
                    ServiceRegistrationStatusTask serviceRegistrationStatusTask = new ServiceRegistrationStatusTask(getDiscoveryClient(),
                            route53AutoRegistrationConfiguration,
                            instance,
                            registerInstanceResult.getOperationId());
                    executorService.execute(serviceRegistrationStatusTask);
                }

            }

            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onError(Throwable t) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error registering instance with AWS:" + t.getMessage(), t);
                }
                if (route53AutoRegistrationConfiguration.isFailFast() && instance instanceof EmbeddedServerInstance) {
                    LOG.error("Error registering instance with AWS and Failfast is set: stopping instance");
                    ((EmbeddedServerInstance) instance).getEmbeddedServer().stop();
                }
            }

            @Override
            public void onComplete() {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Success calling register service request [{}] with {} is complete.", instance.getId(),
                            route53AutoRegistrationConfiguration.getAwsServiceId());
                }
            }
        });

    }

    /**
     * These are convenience methods to help cleanup things like integration test data.
     *
     * @param serviceId service id from AWS to delete
     */
    public void deleteService(String serviceId) {

        DeleteServiceRequest deleteServiceRequest = new DeleteServiceRequest().withId(serviceId);
        getDiscoveryClient().deleteService(deleteServiceRequest);

    }

    /**
     * these are convenience methods to help cleanup things like integration test data.
     *
     * @param namespaceId namespace ID from AWS to delete
     */
    public void deleteNamespace(String namespaceId) {

        DeleteNamespaceRequest deleteNamespaceRequest = new DeleteNamespaceRequest().withId(namespaceId);
        getDiscoveryClient().deleteNamespace(deleteNamespaceRequest);

    }

    /**
     * This is a helper method for integration tests to create a new namespace.
     * Normally you would do this yourself with your own domain/subdomain on route53.
     *
     * @param name name of the namespace in your route53
     * @return id of the namespace
     */
    public String createNamespace(String name) {
        String requestId = Long.toString(System.nanoTime());

        CreatePublicDnsNamespaceRequest publicDnsNamespaceRequest =
                new CreatePublicDnsNamespaceRequest().withCreatorRequestId(requestId)
                        .withName(name)
                        .withDescription("test");
        CreatePublicDnsNamespaceResult clientResult = getDiscoveryClient().createPublicDnsNamespace(publicDnsNamespaceRequest);
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
     * Loop for checking of the call to aws is complete or not. This is the non-async version used for testing
     * @param operationId operation ID we are polling for
     * @return result of the operation, can be success or failure or ongoing
     */
    private GetOperationResult checkOperation(String operationId) {
        String result = "";
        GetOperationResult opResult = null;
        try {
            while (!result.equals("SUCCESS") && !result.equals("FAIL")) {
                opResult = getDiscoveryClient().getOperation(new GetOperationRequest().withOperationId(operationId));
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
                //TODO make this configurable
                Thread.currentThread().sleep(5000); // if you call this to much amazon will rate limit you
            }
        } catch (InterruptedException e) {
            LOG.error("Error polling for aws response operation:", e);
        }
        return opResult;
    }

    /**
     * Gets the discovery client Impl for easier testing.
     * @return interface to communicate with AWS (or fake it)
     */
    public AWSServiceDiscoveryAsync getDiscoveryClient() {
        return awsServiceDiscoveryResolver.resolve(environment);
    }

    /**
     * Gets the discovery service used on route53. This to check to see if it has custom health checks.
     * @return service used to register instances to or get instances from
     */
    public Service getDiscoveryService() {

        return discoveryService;
    }

    /**
     * Used for testing.
     * @param discoveryService service reference on route53 on AWS
     */
    public void setDiscoveryService(Service discoveryService) {
        this.discoveryService = discoveryService;
    }

}
