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
package io.micronaut.inject.provider

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import org.atinject.tck.auto.DriversSeat
import org.atinject.tck.auto.accessories.SpareTire
import spock.lang.Specification

class ProviderNamedInjectionSpec extends Specification {

    void "test qualified provider injection"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        Seats seats = ctx.getBean(Seats)

        expect:
        seats.driversSeatProvider.get() instanceof DriversSeat
        seats.spareTireProvider.get() instanceof SpareTire

        cleanup:
        ctx.close()
    }

    void "test each bean with a nullable provider parameter"() {
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBeansOfType(EachBeanProvider).size() == 2

        when:
        ctx.getBeansOfType(ErrorEachBeanProvider)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.message.contains("Missing bean argument value: notABeanProvider")

        cleanup:
        ctx.close()
    }
}
