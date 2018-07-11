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

package io.micronaut.configuration.ribbon.discovery;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ServerListFilter;
import io.micronaut.configuration.ribbon.DiscoveryClientServerList;
import io.micronaut.configuration.ribbon.RibbonLoadBalancer;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.loadbalance.DiscoveryClientLoadBalancerFactory;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;

/**
 * Replaces the default {@link DiscoveryClientLoadBalancerFactory} with one that returns {@link RibbonLoadBalancer} instances.
 *
 * @author graemerocher
 * @since 1.0
 */
@Replaces(DiscoveryClientLoadBalancerFactory.class)
@Singleton
public class RibbonDiscoveryClientLoadBalancerFactory extends DiscoveryClientLoadBalancerFactory {
    private final BeanContext beanContext;
    private final IClientConfig defaultClientConfig;

    /**
     * Constructor.
     * @param discoveryClient discoveryClient
     * @param beanContext beanContext
     * @param defaultClientConfig defaultClientConfig
     */
    public RibbonDiscoveryClientLoadBalancerFactory(DiscoveryClient discoveryClient,
                                                    BeanContext beanContext,
                                                    IClientConfig defaultClientConfig) {
        super(discoveryClient);

        this.beanContext = beanContext;
        this.defaultClientConfig = defaultClientConfig;
    }

    @Override
    public LoadBalancer create(String serviceID) {
        serviceID = NameUtils.hyphenate(serviceID);
        IClientConfig niwsClientConfig = beanContext.findBean(IClientConfig.class, Qualifiers.byName(serviceID)).orElse(defaultClientConfig);
        IRule rule = beanContext.findBean(IRule.class, Qualifiers.byName(serviceID)).orElseGet(() -> beanContext.createBean(IRule.class));
        IPing ping = beanContext.findBean(IPing.class, Qualifiers.byName(serviceID)).orElseGet(() -> beanContext.createBean(IPing.class));
        ServerListFilter serverListFilter = beanContext.findBean(ServerListFilter.class, Qualifiers.byName(serviceID)).orElseGet(() -> beanContext.createBean(ServerListFilter.class));

        String finalServiceID = serviceID;
        ServerList<Server> serverList = beanContext.findBean(ServerList.class, Qualifiers.byName(serviceID)).orElseGet(() -> new DiscoveryClientServerList(getDiscoveryClient(), finalServiceID));

        if (niwsClientConfig.getPropertyAsBoolean(CommonClientConfigKey.InitializeNFLoadBalancer, true)) {
            return createRibbonLoadBalancer(niwsClientConfig, rule, ping, serverListFilter, serverList);
        } else {
            return super.create(serviceID);
        }
    }

    /**
     * Create the load balancer based on the parameters.
     * @param niwsClientConfig niwsClientConfig
     * @param rule rule
     * @param ping ping
     * @param serverListFilter serverListFilter
     * @param serverList serverList
     * @return balancer
     */
    protected RibbonLoadBalancer createRibbonLoadBalancer(IClientConfig niwsClientConfig, IRule rule, IPing ping, ServerListFilter serverListFilter, ServerList<Server> serverList) {
        return new RibbonLoadBalancer(
            niwsClientConfig,
            serverList,
            serverListFilter,
            rule,
            ping
        );
    }
}
