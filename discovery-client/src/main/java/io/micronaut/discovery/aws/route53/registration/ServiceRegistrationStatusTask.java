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

import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync;
import com.amazonaws.services.servicediscovery.model.GetOperationRequest;
import com.amazonaws.services.servicediscovery.model.GetOperationResult;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.aws.route53.Route53AutoRegistrationConfiguration;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This monitors and retries a given operationID when a service is registered. We have to do this in another thread because
 * amazon's async API still requires blocking polling to get the output of a service registration.
 *
 * @author Ryan
 * @author graemerocher
 */
@Internal
class ServiceRegistrationStatusTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceRegistrationStatusTask.class);

    private final String operationId;
    private final Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration;
    private final ServiceInstance embeddedServerInstance;
    private final AWSServiceDiscoveryAsync discoveryClient;
    private boolean registered = false;

    /**
     * Constructor for the task.
     * @param discoveryClient aws connection imp for service discovery
     * @param route53AutoRegistrationConfiguration configuration for auto registartion
     * @param embeddedServerInstance server instance running to register
     * @param operationId operation after first register call to monitor
     */
    ServiceRegistrationStatusTask(AWSServiceDiscoveryAsync discoveryClient,
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
