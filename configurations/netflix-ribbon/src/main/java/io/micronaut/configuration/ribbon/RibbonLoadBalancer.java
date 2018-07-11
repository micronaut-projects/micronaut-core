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

import com.netflix.client.ClientException;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.PollingServerListUpdater;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ServerListFilter;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import org.reactivestreams.Publisher;

import java.util.List;

/**
 * A {@link LoadBalancer} that is also a Ribbon {@link ILoadBalancer}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class RibbonLoadBalancer implements LoadBalancer, ILoadBalancer {

    private final ILoadBalancer loadBalancer;
    private final LoadBalancerContext loadBalancerContext;
    private final IClientConfig clientConfig;

    /**
     * Constructor.
     * @param niwsClientConfig niwsClientConfig
     * @param serverList serverList
     * @param serverListFilter serverListFilter
     * @param rule rule
     * @param ping ping
     */
    @SuppressWarnings("unchecked")
    public RibbonLoadBalancer(
        IClientConfig niwsClientConfig,
        ServerList serverList,
        ServerListFilter serverListFilter,
        IRule rule,
        IPing ping) {

        this.loadBalancer = createLoadBalancer(niwsClientConfig, rule, ping, serverListFilter, serverList);
        this.loadBalancerContext = new LoadBalancerContext(loadBalancer, niwsClientConfig);
        this.loadBalancerContext.initWithNiwsConfig(niwsClientConfig);
        this.clientConfig = niwsClientConfig;
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

    /**
     * Creates the {@link ILoadBalancer} to use. Defaults to {@link ZoneAwareLoadBalancer}. Subclasses can override to provide custom behaviour
     *
     * @param clientConfig     The client config
     * @param rule             The {@link IRule}
     * @param ping             THe {@link IPing}
     * @param serverListFilter The {@link ServerListFilter}
     * @param serverList       The {@link ServerList}
     * @return The {@link ILoadBalancer} instance. Never null
     */
    @SuppressWarnings("unchecked")
    protected ILoadBalancer createLoadBalancer(IClientConfig clientConfig, IRule rule, IPing ping, ServerListFilter serverListFilter, ServerList<Server> serverList) {
        return new ZoneAwareLoadBalancer(
            clientConfig,
            rule,
            ping,
            serverList,
            serverListFilter,
            new PollingServerListUpdater(clientConfig)
        );
    }
}
