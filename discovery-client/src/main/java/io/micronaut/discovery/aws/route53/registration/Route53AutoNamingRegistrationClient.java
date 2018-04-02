package io.micronaut.discovery.aws.route53.registration;

import com.amazonaws.services.route53.model.GetHealthCheckStatusRequest;
import com.amazonaws.services.route53.model.GetHealthCheckStatusResult;
import com.amazonaws.services.servicediscovery.*;
import com.amazonaws.services.servicediscovery.model.*;
import io.micronaut.configurations.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceIdGenerator;
import io.micronaut.discovery.aws.route53.Route53AutoRegistrationConfiguration;
import io.micronaut.discovery.aws.route53.client.Route53AutoNamingClient;
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.aws.AmazonComputeInstanceMetadataResolver;
import io.micronaut.discovery.cloud.aws.AmazonEC2InstanceMetadata;
import io.micronaut.health.HealthStatus;
import io.micronaut.health.HeartbeatConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@Singleton
@Requires(env= Environment.AMAZON_EC2)
@Requires(beans = {Route53AutoRegistrationConfiguration.class})
@Requires(property = "aws.route53.registration.enabled", value = "true", defaultValue = "false")
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
public class Route53AutoNamingRegistrationClient extends DiscoveryServiceAutoRegistration {

    public static final String AWS_INSTANCE_PORT = "AWS_INSTANCE_PORT";
    public static final String AWS_INSTANCE_IPV4 = "AWS_INSTANCE_IPV4";
    public static final String AWS_INSTANCE_CNAME = "AWS_INSTANCE_CNAME";
    public static final String AWS_INSTANCE_IPV6 = "AWS_INSTANCE_IPV6";
    public static final String AWS_ALIAS_DNS_NAME = "AWS_ALIAS_DNS_NAME";


    private final Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration;
    private final Environment environment;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ServiceInstanceIdGenerator idGenerator;
    private final AWSClientConfiguration clientConfiguration;
    private final AWSServiceDiscovery discoveryClient;
    private final AmazonComputeInstanceMetadataResolver amazonComputeInstanceMetadataResolver;
    private Service discoveryService;

    protected static final Logger LOG = LoggerFactory.getLogger(Route53AutoNamingRegistrationClient.class);


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
        this.discoveryClient = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.clientConfiguration).build();
        this.amazonComputeInstanceMetadataResolver = amazonComputeInstanceMetadataResolver;

    }

    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {
        // this only work if you create a health status check when you register it
        // we can't really pulsate anywhere because amazon health checks work inverse from this UNLESS you have a custom health check
        if (discoveryService.getHealthCheckCustomConfig()!=null) {
            CustomHealthStatus customHealthStatus = CustomHealthStatus.UNHEALTHY;
            if (status.getOperational().isPresent()) {
                customHealthStatus = CustomHealthStatus.HEALTHY;
            }
            discoveryClient.updateInstanceCustomHealthStatus(new UpdateInstanceCustomHealthStatusRequest().withInstanceId(instance.getInstanceId().get()).withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId()).withStatus(customHealthStatus));
        }

        if (status.getOperational().isPresent() && !status.getOperational().get()) {
            discoveryClient.deregisterInstance(new DeregisterInstanceRequest().withInstanceId(instance.getInstanceId().get()).withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId()));
            LOG.info("Health status is non operational, instance id "+instance.getInstanceId().get()+" was de-registered from the discovery service.");
        }
    }

    @Override
    protected void deregister(ServiceInstance instance) {

        DeregisterInstanceRequest deregisterInstanceRequest = new DeregisterInstanceRequest().withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId())
                .withInstanceId(instance.getInstanceId().get());
        discoveryClient.deregisterInstance(deregisterInstanceRequest);
    }

    @Override
    protected void register(ServiceInstance instance) {
        // step 1 get domain from config
        // set service from config
        // check if service exists
        // register service if not
        // register instance to service

        Map<String,String> instanceAttributes = new HashMap<String,String>();

        // you can't just put anything in there like a custom config. Only certain things are allowed or you get weird errors
        // see https://docs.aws.amazon.com/Route53/latest/APIReference/API_autonaming_RegisterInstance.html
        //if the service uses A records use these
        if (instance.getPort()>0) {
            instanceAttributes.put("AWS_INSTANCE_PORT", Integer.toString(instance.getPort()));
        }
        if (amazonComputeInstanceMetadataResolver!=null) {
            Optional<ComputeInstanceMetadata> instanceMetadata = amazonComputeInstanceMetadataResolver.resolve(environment);
            if (instanceMetadata.isPresent()) {
                ComputeInstanceMetadata computeInstanceMetadata = instanceMetadata.get();
                if (computeInstanceMetadata.getPublicIpV4()!=null) {
                    instanceAttributes.put(AWS_INSTANCE_IPV4, computeInstanceMetadata.getPublicIpV4());
                } else { if (computeInstanceMetadata.getPrivateIpV4()!=null) {
                    instanceAttributes.put(AWS_INSTANCE_IPV4, computeInstanceMetadata.getPrivateIpV4());
                }}

                if (!instanceAttributes.containsKey(AWS_INSTANCE_IPV4)) {
                    // try ip v6
                    if (computeInstanceMetadata.getPublicIpV4()!=null) {
                        instanceAttributes.put(AWS_INSTANCE_IPV6, computeInstanceMetadata.getPublicIpV6());
                    } else { if (computeInstanceMetadata.getPrivateIpV6()!=null) {
                        instanceAttributes.put(AWS_INSTANCE_IPV6, computeInstanceMetadata.getPrivateIpV6());
                    }}
                }
            }
        } else {
            //TODO we can call ec2 service and find the info we need but this may be overkill
        }


        //TODO config sharing will go in map above or in ConfigurationManagement Service?
        RegisterInstanceRequest instanceRequest = new RegisterInstanceRequest().withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId())
                .withInstanceId(instance.getInstanceId().get()).withCreatorRequestId(Long.toString(System.nanoTime())).withAttributes(instanceAttributes);

        RegisterInstanceResult instanceResult = discoveryClient.registerInstance(instanceRequest);
        GetOperationResult opResult = checkOperation(instanceResult.getOperationId());

        assert opResult.getOperation().getStatus().equals("SUCCESS");

    }


    private GetOperationResult checkOperation(String operationId) {

        String result = "";
        GetOperationResult opResult = null;
        try {
            while (!result.equals("SUCCESS") && !result.equals("FAIL")) {
                opResult = discoveryClient.getOperation(new GetOperationRequest().withOperationId(operationId));
                result = opResult.getOperation().getStatus();
                if (opResult.getOperation().getStatus().equals("SUCCESS")) {
                    LOG.info("Successfully created namespace id "+opResult.getOperation().getTargets().get("NAMESPACE")+" please add this to your configs for future restarts.");
                    return opResult;
                } else {
                    if (opResult.getOperation().getStatus().equals("FAIL")){
                        LOG.error("Error calling aws service for operationId:"+operationId+" error code:"+ opResult.getOperation().getErrorCode()+" error message:"+opResult.getOperation().getErrorMessage());
                        return opResult;
                    }
                }
                Thread.currentThread().sleep(1000); // if you call this to much amazon will rate limit you
            }
        } catch (InterruptedException e) {
            LOG.error("Error polling for aws response operation:",e);
        }
        return opResult;

    }

    /**
     * these are convenience methods to help cleanup things like integration test data
     * @param serviceId
     */
    public void deleteService(String serviceId) {

        DeleteServiceRequest deleteServiceRequest = new DeleteServiceRequest().withId(serviceId);
        discoveryClient.deleteService(deleteServiceRequest);

    }

    /**
     * these are convenience methods to help cleanup things like integration test data
     *
     * @param namespaceId
     */
    public void deleteNamespace(String namespaceId) {

        DeleteNamespaceRequest deleteNamespaceRequest = new DeleteNamespaceRequest().withId(namespaceId);
        discoveryClient.deleteNamespace(deleteNamespaceRequest);

    }


    public String createNamespace(AWSServiceDiscovery serviceDiscovery, String name) {
        if (serviceDiscovery==null) {
            serviceDiscovery = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.clientConfiguration).build();
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


    public String createService(AWSServiceDiscovery serviceDiscovery, String name, String description, String namespaceId, Long ttl) {
        if (serviceDiscovery==null) {
            serviceDiscovery = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.clientConfiguration).build();
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



}
