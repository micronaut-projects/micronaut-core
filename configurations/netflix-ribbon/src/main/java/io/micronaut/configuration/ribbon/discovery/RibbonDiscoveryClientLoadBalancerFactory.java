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
import com.netflix.loadbalancer.*;
import io.micronaut.configuration.ribbon.DiscoveryClientServerList;
import io.micronaut.configuration.ribbon.RibbonLoadBalancer;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.loadbalance.DiscoveryClientLoadBalancerFactory;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;
import java.net.URI;
import java.util.List;
import java.util.Optional;


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
    private final Environment environment;

    /**
     * Constructor.
     * @param discoveryClient discoveryClient
     * @param beanContext beanContext
     * @param defaultClientConfig defaultClientConfig
     * @param environment The environment
     */
    public RibbonDiscoveryClientLoadBalancerFactory(DiscoveryClient discoveryClient,
                                                    BeanContext beanContext,
                                                    IClientConfig defaultClientConfig,
                                                    Environment environment) {
        super(discoveryClient);

        this.beanContext = beanContext;
        this.defaultClientConfig = defaultClientConfig;
        this.environment = environment;
    }

    @Override
    public LoadBalancer create(String serviceID) {
        serviceID = NameUtils.hyphenate(serviceID);
        IClientConfig niwsClientConfig = beanContext.findBean(IClientConfig.class, Qualifiers.byName(serviceID)).orElse(new StandardNameClientConfig(environment, serviceID, defaultClientConfig));
        IRule rule = beanContext.findBean(IRule.class, Qualifiers.byName(serviceID)).orElseGet(() -> beanContext.createBean(IRule.class));
        IPing ping = beanContext.findBean(IPing.class, Qualifiers.byName(serviceID)).orElseGet(() -> beanContext.createBean(IPing.class));
        ServerListFilter serverListFilter = beanContext.findBean(ServerListFilter.class, Qualifiers.byName(serviceID)).orElseGet(() -> beanContext.createBean(ServerListFilter.class));

        ServerList<Server> serverList = buildServerList(beanContext, serviceID, niwsClientConfig, getDiscoveryClient());

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

    private static ServerList<Server> buildServerList(BeanContext beanContext, String serviceID, IClientConfig niwsClientConfig, DiscoveryClient discoveryClient) {
        final Optional<List> result = ConversionService.SHARED.convert(
                niwsClientConfig.get(CommonClientConfigKey.ListOfServers, null),
                Argument.of(List.class, URI.class)
        );

        final List staticServerList = result.orElse(null);
        ServerList<Server> serverList;
        if (CollectionUtils.isNotEmpty(staticServerList)) {
            final ConfigurationBasedServerList configList = new ConfigurationBasedServerList();
            configList.initWithNiwsConfig(niwsClientConfig);
            serverList = configList;
        } else {
            serverList = beanContext.findBean(ServerList.class, Qualifiers.byName(serviceID))
                                    .orElseGet(() -> new DiscoveryClientServerList(discoveryClient, serviceID));
        }
        return serverList;
    }
}
