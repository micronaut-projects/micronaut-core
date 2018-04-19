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
