/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.runtime.event.annotation.itfce

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class EventListenerDefinedOnInterfaceSpec extends Specification {

    void "test @EventListener defined on interface"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        DefaultThingService thingService = ctx.getBean(DefaultThingService)

        when:
        thingService.create("stuff")

        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        conditions.eventually {
            thingService.getThings().size() == 1
            thingService.getThings().contains("stuff")
        }


        cleanup:
        ctx.close()
    }
}
