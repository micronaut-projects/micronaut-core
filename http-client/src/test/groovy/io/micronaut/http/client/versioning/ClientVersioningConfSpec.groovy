package io.micronaut.http.client.versioning

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.interceptor.configuration.ClientVersioningConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class ClientVersioningConfSpec extends Specification {

    def "should not contain any versioning configurations in context"() {
        when:
        def context = ApplicationContext.run("test")
        then:
        context.getBeansOfType(ClientVersioningConfiguration, Qualifiers.byName("simple")).isEmpty()
    }

    def "should contain versioning configuration in context"() {
        when:
        def context = ApplicationContext.run(["micronaut.http.client.versioning.simple.headers": ["X-API"]],
                "test")
        then:
        context.getBean(ClientVersioningConfiguration, Qualifiers.byName("simple")) != null
    }

    def "should contain headers and parameters for configuration"() {
        when:
        def context = ApplicationContext.run(["micronaut.http.client.versioning.simple.headers"   : ["X-API"],
                                              "micronaut.http.client.versioning.simple.parameters": ["api-version"],
                                              "micronaut.http.client.versioning.default.parameters": ["version"]],
                "test")
        def simpleConfig = context.getBean(ClientVersioningConfiguration, Qualifiers.byName("simple"))
        def defaultConfig = context.getBean(ClientVersioningConfiguration)
        then:
        simpleConfig.getHeaders() == ["X-API"]
        simpleConfig.getParameters() == ["api-version"]
        defaultConfig.getParameters() == ["version"]
    }


}