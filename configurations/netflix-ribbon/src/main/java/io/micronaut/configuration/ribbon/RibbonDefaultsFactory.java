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
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.ServerListFilter;
import com.netflix.loadbalancer.ZoneAffinityServerListFilter;
import com.netflix.loadbalancer.ZoneAvoidanceRule;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(classes = IClientConfig.class)
public class RibbonDefaultsFactory {

    /**
     * The default {@link ServerListFilter} to use.
     *
     * @param defaultConfig The default {@link IClientConfig}
     * @return The default {@link ServerListFilter} to use
     */
    @Prototype
    @Primary
    @Requires(missingBeans = ServerListFilter.class)
    ServerListFilter defaultServerListFilter(IClientConfig defaultConfig) {
        return new ZoneAffinityServerListFilter(defaultConfig);
    }

    /**
     * @return The default {@link IPing} to use.
     */
    @Prototype
    @Primary
    @Requires(missingBeans = IPing.class)
    IPing defaultPing() {
        return new DummyPing();
    }

    /**
     * @param clientConfig clientConfig
     * @return The default {@link IRule} to use
     */
    @Prototype
    @Primary
    @Requires(missingBeans = IRule.class)
    IRule defaultRule(IClientConfig clientConfig) {
        ZoneAvoidanceRule zoneAvoidanceRule = new ZoneAvoidanceRule();
        zoneAvoidanceRule.initWithNiwsConfig(clientConfig);
        return zoneAvoidanceRule;
    }
}
