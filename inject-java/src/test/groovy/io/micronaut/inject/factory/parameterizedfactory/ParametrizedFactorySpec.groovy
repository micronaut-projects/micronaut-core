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
package io.micronaut.inject.factory.parameterizedfactory

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ParametrizedFactorySpec extends Specification  {
    void "test parametrized factory definition"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        C c = context.createBean(C, Collections.singletonMap("count", 10))

        expect:
        c != null
        c.count == 10
        c.b != null

        cleanup:
        context.close()
    }

    void "test parametrized factory definition missing parameter"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        context.createBean(C)

        then:
        BeanInstantiationException e = thrown()
        e.message.contains('Missing bean argument [int count] for type: io.micronaut.inject.factory.parameterizedfactory.C. Required arguments: int count')

        cleanup:
        context.close()

    }

    void "test parametrized factory definition invalid parameter"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        context.createBean(C, Collections.singletonMap("count", "test"))

        then:
        BeanInstantiationException e = thrown()
        e.message.contains('Invalid bean argument [int count]. Cannot convert object [test] to required type: int')

        cleanup:
        context.close()
    }

    def "verify that parametrized bean with nullable arguments can be initialized"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        D parametrizedBean = context.createBean(D)

        then:
        parametrizedBean != null

        cleanup:
        context.close()
    }
}
