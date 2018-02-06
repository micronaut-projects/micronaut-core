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
package org.particleframework.configurations.ribbon

import com.netflix.loadbalancer.DummyPing
import com.netflix.loadbalancer.IPing
import com.netflix.loadbalancer.IRule
import com.netflix.loadbalancer.ZoneAvoidanceRule
import org.particleframework.context.ApplicationContext
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class RibbonConfigSpec extends Specification {

    void "test that a custom default ping is possible"() {
        expect:
        ApplicationContext.run().getBean(IPing) instanceof MyPing
        ApplicationContext.run().getBean(IRule) instanceof MyZoneAvoidanceRule
    }

    @Singleton
    static class MyPing extends DummyPing {

    }

    @Singleton
    static class MyZoneAvoidanceRule extends ZoneAvoidanceRule {

    }
}
