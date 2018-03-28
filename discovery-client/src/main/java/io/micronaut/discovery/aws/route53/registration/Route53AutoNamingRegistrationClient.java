package io.micronaut.discovery.aws.route53.registration;

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


@Singleton
@Requires(beans = {Route53AutoNamingClient.class, Route53AutoRegistrationConfiguration.class})
@Requires(property = "aws.route53.registration.enabled", value = "true", defaultValue = "false")
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
public class Route53AutoNamingRegistrationClient extends DiscoveryServiceAutoRegistration {

    private final Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration;
    private final Route53AutoNamingClient route53AutoNamingClient;
    private final Environment environment;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ServiceInstanceIdGenerator idGenerator;
    private final AWSClientConfiguration clientConfiguration;
    private final AWSServiceDiscovery discoveryClient;

    protected static final Logger LOG = LoggerFactory.getLogger(Route53AutoNamingRegistrationClient.class);


    protected Route53AutoNamingRegistrationClient(
            Environment environment,
            Route53AutoNamingClient route53AutoNamingClient,
            HeartbeatConfiguration heartbeatConfiguration,
            Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration,
            ServiceInstanceIdGenerator idGenerator,
            AWSClientConfiguration clientConfiguration) {
        super(route53AutoRegistrationConfiguration);
        this.environment = environment;
        this.route53AutoNamingClient = route53AutoNamingClient;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.route53AutoRegistrationConfiguration = route53AutoRegistrationConfiguration;
        this.idGenerator = idGenerator;
        this.clientConfiguration = clientConfiguration;
        this.discoveryClient = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.clientConfiguration).build();
    }

    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {
        // this only work if you create a health status check when you register it
        System.out.println("pulsate health status="+status.toString());
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
        String requestId = ApplicationConfiguration.APPLICATION_NAME+":"+Long.toString(System.nanoTime());

        if (route53AutoRegistrationConfiguration.getNamespaceId()==null) { // try to create these if they don't supply it with sensible defaults
            if (route53AutoRegistrationConfiguration.getDnsNamespaceType()!=null &&
                    route53AutoRegistrationConfiguration.getDnsNamespaceType().equalsIgnoreCase("public")) {
                CreatePublicDnsNamespaceRequest publicDnsNamespaceRequest =
                        new CreatePublicDnsNamespaceRequest().withCreatorRequestId(requestId)
                                .withName(route53AutoRegistrationConfiguration.getRoute53Alias())
                                .withDescription("test");
                //TODO switch to async version
                CreatePublicDnsNamespaceResult clientResult = discoveryClient.createPublicDnsNamespace(publicDnsNamespaceRequest);
                String operationId = clientResult.getOperationId();


                GetOperationResult opResult = checkOperation(operationId);
                route53AutoRegistrationConfiguration.setNamespaceId(opResult.getOperation().getTargets().get("NAMESPACE"));

            } else {
                if (route53AutoRegistrationConfiguration.getDnsNamespaceType().equalsIgnoreCase("private")) {
                    CreatePrivateDnsNamespaceRequest privateDnsNamespaceRequest =
                            new CreatePrivateDnsNamespaceRequest().withCreatorRequestId(requestId)
                                    .withName(route53AutoRegistrationConfiguration.getRoute53Alias())
                                    .withDescription("test");
                    //TODO switch to async version since this can take some time to complete

                    CreatePrivateDnsNamespaceResult clientResult = discoveryClient.createPrivateDnsNamespace(privateDnsNamespaceRequest);
                    String operationId = clientResult.getOperationId();
                    GetOperationResult opResult = checkOperation(operationId);
                    route53AutoRegistrationConfiguration.setNamespaceId(opResult.getOperation().getTargets().get("NAMESPACE"));

                }
            }
        }

        if (route53AutoRegistrationConfiguration.getAwsServiceId()==null) {
            DnsRecord dnsRecord = new DnsRecord().withType(RecordType.CNAME).withTTL(route53AutoRegistrationConfiguration.getDnsRecordTTL());
            DnsConfig dnsConfig = new DnsConfig().withDnsRecords(dnsRecord).withNamespaceId(route53AutoRegistrationConfiguration.getNamespaceId()).withRoutingPolicy(RoutingPolicy.WEIGHTED);
            CreateServiceRequest createServiceRequest = new CreateServiceRequest().withDnsConfig(dnsConfig)
                    .withDescription(route53AutoRegistrationConfiguration.getServiceDescription())
                    .withName(route53AutoRegistrationConfiguration.getServiceName());
            CreateServiceResult servicerResult = discoveryClient.createService(createServiceRequest);
            Service createdService = servicerResult.getService();
            route53AutoRegistrationConfiguration.setAwsServiceId(createdService.getId());
        }

        Map<String,String> instanceAttributes = new HashMap<String,String>();
        // we need to build a url to register for apps to callback
        instanceAttributes.put("URI",instance.getURI().toString());
        //TODO config sharing will go in map above ?
        RegisterInstanceRequest instanceRequest = new RegisterInstanceRequest().withServiceId(route53AutoRegistrationConfiguration.getAwsServiceId())
                .withInstanceId(instance.getInstanceId().get()).withAttributes(instanceAttributes);

        RegisterInstanceResult instanceResult = discoveryClient.registerInstance(instanceRequest);
        GetOperationResult opResult = checkOperation(instanceResult.getOperationId());

        assert opResult.getOperation().getStatus().equals("SUCCESS");

    }


    GetOperationResult checkOperation(String operationId) {

        String result = "";
        GetOperationResult opResult = null;
        try {
            while (!result.equals("SUCCESS") && !result.equals("FAIL")) {
                opResult = discoveryClient.getOperation(new GetOperationRequest().withOperationId(operationId));
                result = opResult.getOperation().getStatus();
                if (opResult.getOperation().getStatus().equals("SUCCESS")) {
                    route53AutoRegistrationConfiguration.setNamespaceId(opResult.getOperation().getTargets().get("NAMESPACE"));
                    LOG.info("Successfully created namespace id "+opResult.getOperation().getTargets().get("NAMESPACE")+" please add this to your configs for future restarts.");
                    return opResult;
                } else {
                    if (opResult.getOperation().getStatus().equals("FAIL")){
                        LOG.error("Error calling aws service for operationId:"+operationId+" error code:"+ opResult.getOperation().getErrorCode()+" error message:"+opResult.getOperation().getErrorMessage());
                        return opResult;
                    }
                }
                Thread.currentThread().sleep(1000);
            }
        } catch (InterruptedException e) {
            LOG.error("Error polling for aws response operation:",e);
        }
        return opResult;

    }

    /**
     * these are convenience methods to help cleanup things like integration test data
     * @param serviceDiscovery
     * @param serviceId
     */
    public void deleteService(AWSServiceDiscovery serviceDiscovery, String serviceId) {
        if (serviceDiscovery==null) {
            serviceDiscovery = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.clientConfiguration).build();
        }

        DeleteServiceRequest deleteServiceRequest = new DeleteServiceRequest().withId(serviceId);
        serviceDiscovery.deleteService(deleteServiceRequest);

    }

    /**
     * these are convenience methods to help cleanup things like integration test data
     * @param serviceDiscovery
     * @param namespaceId
     */
    public void deleteNamespace(AWSServiceDiscovery serviceDiscovery, String namespaceId) {
        if (serviceDiscovery==null) {
            serviceDiscovery = AWSServiceDiscoveryClient.builder().withClientConfiguration(clientConfiguration.clientConfiguration).build();
        }

        DeleteNamespaceRequest deleteNamespaceRequest = new DeleteNamespaceRequest().withId(namespaceId);
        serviceDiscovery.deleteNamespace(deleteNamespaceRequest);

    }

}
