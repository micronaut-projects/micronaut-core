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

package io.micronaut.configuration.ribbon

import com.netflix.client.config.CommonClientConfigKey
import com.netflix.client.config.IClientConfig
import com.netflix.loadbalancer.DummyPing
import com.netflix.loadbalancer.IPing
import com.netflix.loadbalancer.IRule
import com.netflix.loadbalancer.ServerListFilter
import com.netflix.loadbalancer.ZoneAffinityServerListFilter
import com.netflix.loadbalancer.ZoneAvoidanceRule
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class RibbonConfigSpec extends Specification {

    void "test that a custom default ping is possible"() {
        expect:
        ApplicationContext.run().getBean(IPing) instanceof MyPing
        ApplicationContext.run().getBean(IRule) instanceof MyZoneAvoidanceRule
        ApplicationContext.run().getBean(ServerListFilter) instanceof MyZoneAffinityFilter
    }

    void "test named IClientConfig configuration"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run('ribbon.clients.foo.VipAddress':'test')

        expect:
        applicationContext.getBean(IClientConfig, Qualifiers.byName("foo")).get(CommonClientConfigKey.VipAddress) == 'test'
    }

    @Prototype
    static class MyPing extends DummyPing {

    }

    @Prototype
    static class MyZoneAvoidanceRule extends ZoneAvoidanceRule {

    }

    @Prototype
    static class MyZoneAffinityFilter extends ZoneAffinityServerListFilter {
        MyZoneAffinityFilter(IClientConfig niwsClientConfig) {
            super(niwsClientConfig)
        }
    }
}
