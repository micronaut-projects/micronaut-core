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

package io.micronaut.security.config

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import spock.lang.Specification

class SecurityConfigurationSpec extends Specification {

    void "test configuring security"() {
        given:
        def ctx = ApplicationContext.run([
                "micronaut.security.enabled": true,
                "micronaut.security.interceptUrlMap": [
                        [pattern: '/health'],
                        [pattern: '/health', access: 'foo'],
                        [httpMethod: 'FOO', pattern: '/health', access: ['IS_AUTHENTICATED_ANONYMOUSLY']],
                        [pattern: '/health', access: ['IS_AUTHENTICATED_ANONYMOUSLY']],
                        [httpMethod: 'POST', pattern: '/health', access: ['IS_AUTHENTICATED_ANONYMOUSLY']],
                        [httpMethod: 'post', pattern: '/health', access: ['IS_AUTHENTICATED_ANONYMOUSLY']]
        ]], "test")

        when:
        SecurityConfiguration config = ctx.getBean(SecurityConfiguration)

        then:
        config.interceptUrlMap.size() == 3
        config.interceptUrlMap[0].pattern == '/health'
        config.interceptUrlMap[0].access == ['IS_AUTHENTICATED_ANONYMOUSLY']
        config.interceptUrlMap[0].httpMethod == HttpMethod.GET
        config.interceptUrlMap[1].pattern == '/health'
        config.interceptUrlMap[1].access == ['IS_AUTHENTICATED_ANONYMOUSLY']
        config.interceptUrlMap[1].httpMethod == HttpMethod.POST
        config.interceptUrlMap[2].pattern == '/health'
        config.interceptUrlMap[2].access == ['IS_AUTHENTICATED_ANONYMOUSLY']
        config.interceptUrlMap[2].httpMethod == HttpMethod.POST
    }
}
