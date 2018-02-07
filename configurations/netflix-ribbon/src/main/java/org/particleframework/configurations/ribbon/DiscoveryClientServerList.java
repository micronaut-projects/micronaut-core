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
package org.particleframework.configurations.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import io.reactivex.Flowable;
import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.discovery.ServiceInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ServerList} implementation that uses the {@link DiscoveryClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DiscoveryClientServerList extends AbstractServerList<Server> {
    private final DiscoveryClient discoveryClient;
    private final String serviceID;

    public DiscoveryClientServerList(DiscoveryClient discoveryClient, String serviceID) {
        this.discoveryClient = discoveryClient;
        this.serviceID = serviceID;
    }

    @Override
    public List<Server> getInitialListOfServers() {
        List<ServiceInstance> serviceInstances = Flowable.fromPublisher(discoveryClient.getInstances(serviceID)).blockingFirst();
        List<Server> servers = new ArrayList<>(serviceInstances.size());
        for (ServiceInstance serviceInstance : serviceInstances) {
            RibbonServer server = new RibbonServer(serviceInstance);
            servers.add(server);
        }
        return servers;
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        return getInitialListOfServers();
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        // ignore
    }
}
