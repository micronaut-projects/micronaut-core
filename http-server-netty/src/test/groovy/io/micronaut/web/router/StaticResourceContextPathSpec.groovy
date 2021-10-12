package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.web.router.resource.StaticResourceResolver
import spock.lang.Specification

class StaticResourceContextPathSpec extends Specification {

    void "test server contextpath with static resources"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.server.context-path': "/context/path",
                'micronaut.router.static-resources.public.paths': "classpath:public",
                'micronaut.router.static-resources.public.mapping': "/**",
                'micronaut.router.static-resources.other.paths': "classpath:other",
                'micronaut.router.static-resources.other.mapping': "/other/**",
        )
        StaticResourceResolver resolver = ctx.getBean(StaticResourceResolver)

        when:
        Optional<URL> url = resolver.resolve("/")

        then:
        !url.isPresent()

        when:
        url = resolver.resolve("/other")

        then:
        !url.isPresent()

        when:
        url = resolver.resolve("/context/path/")

        then:
        url.isPresent()
        url.get().toString().endsWith("public/index.html")

        when:
        url = resolver.resolve("/context/path/other")

        then:
        url.isPresent()
        url.get().toString().endsWith("other/index.html")

        cleanup:
        ctx.close()
    }

    void "test server contextpath with static resources with context path set"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.server.context-path': "/context/path",
                'micronaut.router.static-resources.public.paths': "classpath:public",
                'micronaut.router.static-resources.public.mapping': "/context/path/**",
                'micronaut.router.static-resources.other.paths': "classpath:other",
                'micronaut.router.static-resources.other.mapping': "/context/path/other/**",
        )
        StaticResourceResolver resolver = ctx.getBean(StaticResourceResolver)

        when:
        Optional<URL> url = resolver.resolve("/")

        then:
        !url.isPresent()

        when:
        url = resolver.resolve("/other")

        then:
        !url.isPresent()

        when:
        url = resolver.resolve("/context/path/")

        then:
        url.isPresent()
        url.get().toString().endsWith("public/index.html")

        when:
        url = resolver.resolve("/context/path/other")

        then:
        url.isPresent()
        url.get().toString().endsWith("other/index.html")

        cleanup:
        ctx.close()
    }
}
