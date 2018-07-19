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

package io.micronaut.configuration.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.Server;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link com.netflix.loadbalancer.ServerList} implementation that uses the {@link DiscoveryClient}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DiscoveryClientServerList extends AbstractServerList<Server> {
    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryClientServerList.class);
    private final DiscoveryClient discoveryClient;
    private final String serviceID;

    /**
     * Constructor.
     * @param discoveryClient discoveryClient
     * @param serviceID serviceID
     */
    public DiscoveryClientServerList(DiscoveryClient discoveryClient, String serviceID) {
        this.discoveryClient = discoveryClient;
        this.serviceID = serviceID;
    }

    @Override
    public List<Server> getInitialListOfServers() {
        List<Server> serverList = resolveServerList();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolved initial list of servers from DiscoveryClient [{}]: {}", discoveryClient.getDescription(), serverList);
        }
        return serverList;
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        List<Server> serverList = resolveServerList();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolved updated list of servers from DiscoveryClient [{}]: {}", discoveryClient.getDescription(), serverList);
        }
        return serverList;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        // ignore
    }

    /**
     * Return ribbon servers discovered.
     *
     * @return list of servers
     */
    protected List<Server> resolveServerList() {
        List<ServiceInstance> serviceInstances = Flowable.fromPublisher(discoveryClient.getInstances(serviceID)).blockingFirst();
        List<Server> servers = new ArrayList<>(serviceInstances.size());
        for (ServiceInstance serviceInstance : serviceInstances) {
            RibbonServer server = new RibbonServer(serviceInstance);
            servers.add(server);
        }
        return servers;
    }
}
