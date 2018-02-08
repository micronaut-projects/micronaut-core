/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.consul;

import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.ServiceInstanceList;
import org.particleframework.discovery.client.DiscoveryServerInstanceList;
import org.particleframework.discovery.consul.client.v1.ConsulClient;
import org.particleframework.discovery.consul.condition.RequiresConsul;

import javax.inject.Singleton;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * <p>A {@link ServiceInstanceList} for Consul which reads from the {@link ConsulConfiguration}</p>
 *
 * <p>The reason this is useful is if a {@link org.particleframework.runtime.context.scope.refresh.RefreshEvent} occurs then the
 * {@link ConsulConfiguration} will be updated and the backing list of {@link ServiceInstance} changed at runtime.</p>
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@RequiresConsul
public class ConsulServiceInstanceList extends DiscoveryServerInstanceList {

    public ConsulServiceInstanceList(ConsulConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String getID() {
        return ConsulClient.SERVICE_ID;
    }
}
