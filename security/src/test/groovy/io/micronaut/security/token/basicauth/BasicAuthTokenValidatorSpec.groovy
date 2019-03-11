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
package io.micronaut.security.token.basicauth

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class BasicAuthTokenValidatorSpec extends Specification {

    def "BasicAuthTokenValidator not loaded unless security is turn on"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        expect:
        !applicationContext.containsBean(BasicAuthTokenValidator)

        cleanup:
        applicationContext.close()
    }

    def "BasicAuthTokenValidator not loaded if micronaut.security.enabled=false"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['micronaut.security.enabled': false])

        expect:
        !applicationContext.containsBean(BasicAuthTokenValidator)

        cleanup:
        applicationContext.close()
    }

    def "BasicAuthTokenValidator is loaded if micronaut.security.enabled=true"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['micronaut.security.enabled': true])

        expect:
        applicationContext.containsBean(BasicAuthTokenValidator)

        cleanup:
        applicationContext.close()
    }

    def "BasicAuthTokenValidator is loaded if micronaut.security.token.basic-auth.enabled=false"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.basic-auth.enabled': false
        ])

        expect:
        !applicationContext.containsBean(BasicAuthTokenValidator)

        cleanup:
        applicationContext.close()
    }
}
