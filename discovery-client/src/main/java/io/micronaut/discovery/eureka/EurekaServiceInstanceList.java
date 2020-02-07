/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.discovery.eureka;

import io.micronaut.discovery.client.DiscoveryServerInstanceList;
import io.micronaut.discovery.eureka.client.v2.EurekaClient;
import io.micronaut.discovery.eureka.condition.RequiresEureka;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>A {@link io.micronaut.discovery.ServiceInstanceList} for Consul which reads from the {@link EurekaConfiguration}.</p>
 * <p>
 * <p>The reason this is useful is if a {@link io.micronaut.runtime.context.scope.refresh.RefreshEvent} occurs then the
 * {@link EurekaConfiguration} will be updated and the backing list of {@link io.micronaut.discovery.ServiceInstance}
 * changed at runtime.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@RequiresEureka
public class EurekaServiceInstanceList extends DiscoveryServerInstanceList {

    /**
     * Creates a new eureka service instance list.
     * @param configuration The discovery config
     * @param instanceConfiguration The instance config
     */
    @Inject
    public EurekaServiceInstanceList(EurekaConfiguration configuration, ApplicationConfiguration.InstanceConfiguration instanceConfiguration) {
        super(configuration, instanceConfiguration);
    }

    @Override
    public String getID() {
        return EurekaClient.SERVICE_ID;
    }
}
