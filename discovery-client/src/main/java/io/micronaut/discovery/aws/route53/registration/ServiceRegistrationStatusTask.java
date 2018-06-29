package io.micronaut.discovery.aws.route53.registration;

import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync;
import com.amazonaws.services.servicediscovery.model.GetOperationRequest;
import com.amazonaws.services.servicediscovery.model.GetOperationResult;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.aws.route53.Route53AutoRegistrationConfiguration;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Executor;


/**
 * This monitors and retries a given operationID when a service is registered. We have to do this in another thread because
 * amazon's async API still requires blocking polling to get the output of a service registration.
 */
public class ServiceRegistrationStatusTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceRegistrationStatusTask.class);


    String operationId;
    Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration;
    ServiceInstance embeddedServerInstance;
    AWSServiceDiscoveryAsync discoveryClient;
    private boolean registered = false;

    /**
     * Constructor for the task.
     * @param discoveryClient
     * @param route53AutoRegistrationConfiguration
     * @param embeddedServerInstance
     * @param operationId
     */
    public ServiceRegistrationStatusTask(AWSServiceDiscoveryAsync discoveryClient,
                                         Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration,
                                         ServiceInstance embeddedServerInstance,
                                         String operationId) {
        this.discoveryClient = discoveryClient;
        this.route53AutoRegistrationConfiguration = route53AutoRegistrationConfiguration;
        this.embeddedServerInstance = embeddedServerInstance;
        this.operationId = operationId;

    }

    /**
     * Runs the polling process to AWS checks every 5 seconds.
     */
    @Override
    public void run() {

        while (!registered) {
                GetOperationRequest operationRequest = new GetOperationRequest().withOperationId(operationId);
                GetOperationResult result = discoveryClient.getOperation(operationRequest);
                if (LOG.isInfoEnabled()) {
                    LOG.info("Service registration for operation " + operationId + " resulted in " + result.getOperation().getStatus());
                }
                if (result.getOperation().getStatus().equalsIgnoreCase("failure") || result.getOperation().getStatus().equalsIgnoreCase("success")) {
                    registered = true; // either way we are done
                    if (result.getOperation().getStatus().equalsIgnoreCase("failure")) {
                        if (route53AutoRegistrationConfiguration.isFailFast() && embeddedServerInstance instanceof EmbeddedServerInstance) {
                            if (LOG.isErrorEnabled()) {
                                LOG.error("Error registering instance shutting down instance because failfast is set.");
                            }
                            ((EmbeddedServerInstance) embeddedServerInstance).getEmbeddedServer().stop();
                        }
                    }
                }
            }
            try {
                Thread.currentThread().sleep(5000);
            } catch (InterruptedException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Registration monitor service has been aborted, unable to verify proper service registration on Route 53.", e);
                }
            }
        }

}
