package io.micronaut.security.config

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.http.HttpMethod
import spock.lang.Specification

class SecurityConfigurationSpec extends Specification {

    void "test configuring security with missing access key"() {
        given:
        def ctx = ApplicationContext.run([
                "micronaut.security.enabled": true,
                "micronaut.security.interceptUrlMap": [
                        [pattern: '/health']
        ]], "test")

        when:
        ctx.getBean(SecurityConfiguration)

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
        ctx.getBean(SecurityConfiguration)

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
        ctx.getBean(SecurityConfiguration)

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
        SecurityConfiguration config = ctx.getBean(SecurityConfiguration)

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
