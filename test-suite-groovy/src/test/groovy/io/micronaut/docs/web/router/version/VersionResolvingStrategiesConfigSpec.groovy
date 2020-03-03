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
package io.micronaut.docs.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.web.router.DefaultRouter
import io.micronaut.web.router.Router
import io.micronaut.web.router.filter.FilteredRouter
import io.micronaut.web.router.version.resolution.HeaderVersionResolver
import io.micronaut.web.router.version.resolution.HeaderVersionResolverConfiguration
import io.micronaut.web.router.version.resolution.ParameterVersionResolver
import io.micronaut.web.router.version.resolution.RequestVersionResolver
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
        !context.containsBean(RequestVersionResolver)

        cleanup:
        context.close()
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
        context.containsBean(HeaderVersionResolver)
        !context.containsBean(ParameterVersionResolver)

        cleanup:
        context.close()
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
        context.containsBean(ParameterVersionResolver)
        !context.containsBean(HeaderVersionResolver)

        cleanup:
        context.close()
    }

    def "'Router' is not decorated with 'VersionedRouter'"() {
        when:
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                ["micronaut.router.versioning.enabled": "false"]
        ))
        then:
        context.getBean(Router).class != FilteredRouter

        cleanup:
        context.close()
    }

    def "'Router' is instance of 'VersionedRouter'"() {
        when:
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                ["micronaut.router.versioning.enabled": "true"]
        ))
        then:
        context.getBean(Router).class == FilteredRouter

        cleanup:
        context.close()
    }

    def "'Configuration' picked up the header name"() {
        when:
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                ["micronaut.router.versioning.enabled"       : "true",
                 "micronaut.router.versioning.header.enabled": "true",
                 "micronaut.router.versioning.header.names"   : ["X-API", "X-VERSION"]]
        ))
        def bean = context.getBean(HeaderVersionResolverConfiguration)
        then:
        bean.getNames().contains("X-API")
        bean.getNames().contains("X-VERSION")

        cleanup:
        context.close()
    }

    def "Decorating the existing router bean works properly"() {
        when:
        def context = ApplicationContext.run(PropertySource.of(
                "test",
                ["micronaut.router.versioning.enabled": "true"]
        ))
        def bean = context.getBean(DefaultRouter)
        then:
        bean instanceof FilteredRouter

        cleanup:
        context.close()
    }

}