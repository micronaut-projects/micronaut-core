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

import com.netflix.client.ClientException;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.*;
import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.Prototype;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.http.client.LoadBalancer;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.http.client.loadbalance.DiscoveryClientRoundRobinLoadBalancer;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import java.net.URL;
import java.util.List;

/**
 * A {@link LoadBalancer} that is also a Ribbon {@link ILoadBalancer}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Replaces(DiscoveryClientRoundRobinLoadBalancer.class)
@Prototype
public class RibbonLoadBalancer implements LoadBalancer, ILoadBalancer {

    private final ILoadBalancer loadBalancer;
    private final LoadBalancerContext loadBalancerContext;
    private final IClientConfig clientConfig;

    @Inject
    @SuppressWarnings("unchecked")
    public RibbonLoadBalancer(
            @Argument String serviceID,
            DiscoveryClient discoveryClient,
            BeanContext beanContext,
            IClientConfig defaultClientConfig,
            ServerListFilter defaultFilter,
            IRule defaultRule,
            IPing defaultPing) {

        IClientConfig niwsClientConfig = beanContext.findBean(IClientConfig.class, Qualifiers.byName(serviceID)).orElse(defaultClientConfig);
        IRule rule = beanContext.findBean(IRule.class, Qualifiers.byName(serviceID)).orElse(defaultRule);
        IPing ping = beanContext.findBean(IPing.class, Qualifiers.byName(serviceID)).orElse(defaultPing);
        ServerListFilter serverListFilter = beanContext.findBean(ServerListFilter.class, Qualifiers.byName(serviceID)).orElse(defaultFilter);
        ServerList<Server> serverList = beanContext.findBean(ServerList.class, Qualifiers.byName(serviceID)).orElse(new DiscoveryClientServerList(discoveryClient, serviceID));
        this.loadBalancer = new ZoneAwareLoadBalancer(
                niwsClientConfig,
                rule,
                ping,
                serverList,
                serverListFilter,
                new PollingServerListUpdater(niwsClientConfig)

        );
        this.loadBalancerContext = new LoadBalancerContext(loadBalancer, defaultClientConfig);
        this.loadBalancerContext.initWithNiwsConfig(niwsClientConfig);
        this.clientConfig = defaultClientConfig;
    }


    /**
     * @return The {@link IClientConfig} used to construct this load balancer
     */
    public IClientConfig getClientConfig() {
        return clientConfig;
    }

    /**
     * @return The underlying load balancer
     */
    public ILoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * @return A {@link LoadBalancerContext} for this load balancer
     */
    public LoadBalancerContext getLoadBalancerContext() {
        return loadBalancerContext;
    }

    @Override
    public Publisher<ServiceInstance> select(Object discriminator) {
        try {
            Server server = this.loadBalancerContext.getServerFromLoadBalancer(null, discriminator);
            RibbonServiceInstance ribbonServiceInstance = new RibbonServiceInstance(server, loadBalancerContext);
            return Publishers.just(ribbonServiceInstance);

        } catch (ClientException e) {
            return Publishers.just(new HttpClientException("Error retrieving Ribbon server: " + e.getMessage(), e));
        }
    }

    @Override
    public void addServers(List<Server> newServers) {
        loadBalancer.addServers(newServers);
    }

    @Override
    public Server chooseServer(Object key) {
        return loadBalancer.chooseServer(key);
    }

    @Override
    public void markServerDown(Server server) {
        loadBalancer.markServerDown(server);
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<Server> getServerList(boolean availableOnly) {
        return loadBalancer.getServerList(availableOnly);
    }

    @Override
    public List<Server> getReachableServers() {
        return loadBalancer.getReachableServers();
    }

    @Override
    public List<Server> getAllServers() {
        return loadBalancer.getAllServers();
    }

}
