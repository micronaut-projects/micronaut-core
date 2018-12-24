package io.micronaut.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.web.router.Router
import io.micronaut.web.router.version.strategy.HeaderVersionExtractingStrategy
import io.micronaut.web.router.version.strategy.ParameterVersionExtractingStrategy
import io.micronaut.web.router.version.strategy.VersionExtractingStrategy
import spock.lang.Specification

class VersionResolvingStrategiesConfigSpec extends Specification {

    def "should contain no version resolvers due to disabled configuration"() {
        when:
        def context = ApplicationContext.run(
                PropertySource.of(
                        "test",
                        ["micronaut.router.versioning.enabled"       : "false",
                         "micronaut.router.versioning.header.enabled": "true"]
                )
        )
        then:
        !context.containsBean(VersionExtractingStrategy)
    }

    def "contains 'Header resolver' in context"() {
        when:
        def props = ["micronaut.router.versioning.header.enabled": "true",
                     "micronaut.router.versioning.enabled"       : "true"]
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                props
        ))
        then:
        context.containsBean(HeaderVersionExtractingStrategy)
        !context.containsBean(ParameterVersionExtractingStrategy)
    }

    def "contains 'Parameter resolver' in context"() {
        when:
        def props = ["micronaut.router.versioning.parameter.enabled": "true",
                     "micronaut.router.versioning.enabled"          : "true"]
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                props
        ))
        then:
        context.containsBean(ParameterVersionExtractingStrategy)
        !context.containsBean(HeaderVersionExtractingStrategy)
    }

    def "'Router' is not decorated with 'VersionedRouter'"() {
        when:
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                ["micronaut.router.versioning.enabled": "false"]
        ))
        then:
        context.getBean(Router).class != VersionedRouter
    }

    def "'Router' is instance of 'VersionedRouter'"() {
        when:
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                ["micronaut.router.versioning.enabled": "true"]
        ))
        then:
        context.getBean(Router).class == VersionedRouter
    }

    def "'Configuration' picked up the header name"() {
        when:
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                ["micronaut.router.versioning.enabled"       : "true",
                 "micronaut.router.versioning.header.enabled": "true",
                 "micronaut.router.versioning.header.name"   : "X-API"]
        ))
        def bean = context.getBean(RoutesVersioningConfiguration.HeaderBasedVersioningConfiguration)
        then:
        bean.getName() == "X-API"
    }

}