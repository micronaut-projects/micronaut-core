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
package io.micronaut.http.missing

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class MissingBeanSpec extends Specification {

    void "test a controller with a missing bean 1"() {
        when:
            ApplicationContext.run(EmbeddedServer, ['spec.name': "MissingConstructorBeanController"])
        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean: io/micronaut/inject/test/external/ExternalBean"
    }

    void "test a controller with a missing bean 2"() {
        when:
            ApplicationContext.run(EmbeddedServer, ['spec.name': "MissingFieldBeanController"])
        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean: io/micronaut/inject/test/external/ExternalBean"
    }

    void "test a controller with a missing bean 3"() {
        when:
            ApplicationContext.run(EmbeddedServer, ['spec.name': "MissingMethodInjectBeanController"])
        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean: io/micronaut/inject/test/external/ExternalBean"
    }

    void "test a controller with a missing bean 4"() {
        when:
            ApplicationContext.run(EmbeddedServer, ['spec.name': "MissingExecutableMethodBeanController"])
        then:
            def e = thrown(BeanInstantiationException)
            e.message == "Failed to initialize the bean: io/micronaut/inject/test/external/ExternalBean"
    }

}
