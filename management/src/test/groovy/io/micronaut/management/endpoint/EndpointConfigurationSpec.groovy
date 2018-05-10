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
package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class EndpointConfigurationSpec extends Specification {

    void "test sensitive inheritance"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.foo.enabled': true, 'endpoints.all.sensitive': true])

        when:
        EndpointConfiguration foo = context.getBean(EndpointConfiguration, Qualifiers.byName("foo"))

        then: "Foo to be sensitive because it was inherited from all"
        foo.isSensitive().get()
        foo.isEnabled().get()

        cleanup:
        context.close()
    }

    void "test enabled inheritance"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.foo.sensitive': true, 'endpoints.all.enabled': false])

        when:
        EndpointConfiguration foo = context.getBean(EndpointConfiguration, Qualifiers.byName("foo"))

        then: "Foo to not be enabled because it was inherited from all"
        foo.isSensitive().get()
        !foo.isEnabled().get()

        cleanup:
        context.close()
    }

    void "test sensitive is not present"() {
        given:
        ApplicationContext context = ApplicationContext.run( ['endpoints.foo.enabled': true])

        when:
        EndpointConfiguration foo = context.getBean(EndpointConfiguration, Qualifiers.byName("foo"))

        then:
        !foo.isSensitive().isPresent()

        cleanup:
        context.close()
    }

    void "test enabled is not present"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.foo.sensitive': true])

        when:
        EndpointConfiguration foo = context.getBean(EndpointConfiguration, Qualifiers.byName("foo"))

        then:
        !foo.isEnabled().isPresent()

        cleanup:
        context.close()
    }
}
