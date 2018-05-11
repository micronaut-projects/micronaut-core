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
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.http.HttpMethod
import spock.lang.Specification

class SecurityConfigurationPropertiesSpec extends Specification {

    void "test configuring security with missing access key"() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.interceptUrlMap': [
                        [pattern: '/health']
        ]], 'test')

        when:
        ctx.getBean(SecurityConfigurationProperties)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.cause instanceof ConfigurationException

        cleanup:
        ctx.stop()
    }

    void "test configuring security with invalid method"() {
        given:
        def ctx = ApplicationContext.run([
                "micronaut.security.enabled": true,
                "micronaut.security.interceptUrlMap": [
                        [httpMethod: 'FOO', pattern: '/health', access: ['isAnonymous()']]
                ]], "test")

        when:
        ctx.getBean(SecurityConfigurationProperties)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.cause instanceof ConfigurationException

        cleanup:
        ctx.stop()
    }

    void "test configuring security with missing pattern"() {
        given:
        def ctx = ApplicationContext.run([
                "micronaut.security.enabled": true,
                "micronaut.security.interceptUrlMap": [
                        [httpMethod: 'POST', access: ['isAnonymous()']]
                ]], "test")

        when:
        ctx.getBean(SecurityConfigurationProperties)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.cause instanceof ConfigurationException

        cleanup:
        ctx.stop()
    }

    void "test configuring valid security"() {
        given:
        def ctx = ApplicationContext.run([
                "micronaut.security.enabled": true,
                "micronaut.security.interceptUrlMap": [
                        [pattern: '/health', access: 'foo'],
                        [pattern: '/health', access: 'isAnonymous()'],
                        [httpMethod: 'POST', pattern: '/health', access: ['isAnonymous()']],
                        [httpMethod: 'post', pattern: '/health', access: ['isAnonymous()']]
                ]], "test")

        when:
        SecurityConfigurationProperties config = ctx.getBean(SecurityConfigurationProperties)

        then:
        config.interceptUrlMap.size() == 4
        config.interceptUrlMap[0].pattern == '/health'
        config.interceptUrlMap[0].access == ['foo']
        !config.interceptUrlMap[0].httpMethod.isPresent()
        config.interceptUrlMap[1].pattern == '/health'
        config.interceptUrlMap[1].access == ['isAnonymous()']
        !config.interceptUrlMap[1].httpMethod.isPresent()
        config.interceptUrlMap[2].pattern == '/health'
        config.interceptUrlMap[2].access == ['isAnonymous()']
        config.interceptUrlMap[2].httpMethod.get() == HttpMethod.POST
        config.interceptUrlMap[3].pattern == '/health'
        config.interceptUrlMap[3].access == ['isAnonymous()']
        config.interceptUrlMap[3].httpMethod.get() == HttpMethod.POST

        cleanup:
        ctx.stop()
    }
}
