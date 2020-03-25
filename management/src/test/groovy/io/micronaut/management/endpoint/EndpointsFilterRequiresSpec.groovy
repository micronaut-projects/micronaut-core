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
package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.Specification

class EndpointsFilterRequiresSpec extends Specification {

    def "EndpointsFilter is loaded if micronaut.security.enabled=false"() {
        given:
        ApplicationContext context = ApplicationContext.run(['micronaut.security.enabled': false])

        when:
        context.getBean(EndpointsFilter.class)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    def "EndpointsFilter is loaded if micronaut.security.enabled does not exists"() {
        given:
        ApplicationContext context = ApplicationContext.run(['micronaut.security.enabled': false])

        when:
        context.getBean(EndpointsFilter.class)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    def "EndpointsFilter is loaded by default"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        context.getBean(EndpointsFilter.class)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }
}
