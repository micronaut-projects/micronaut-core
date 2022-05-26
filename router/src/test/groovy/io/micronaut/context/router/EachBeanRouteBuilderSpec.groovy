package io.micronaut.context.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.naming.NameResolver
import io.micronaut.web.router.DefaultRouteBuilder
import io.micronaut.web.router.RouteMatch
import io.micronaut.web.router.Router
import spock.lang.Specification

import jakarta.inject.Singleton

class EachBeanRouteBuilderSpec extends Specification {

    void "test route builder with iterable bean"() {
        def ctx = ApplicationContext.run([
                'connection.one': 1,
                'connection.two': 2
        ])
        Router router = ctx.getBean(Router)

        when:
        Optional<RouteMatch> route = router.GET("/connection/one")

        then:
        route.isPresent()
        route.get().invoke() == "one"

        when:
        route = router.GET("/connection/two")

        then:
        route.isPresent()
        route.get().invoke() == "two"
    }

    @EachProperty("connection")
    static class ConnectionController {

        private final String name

        ConnectionController(@Parameter String name) {
            this.name = name
        }

        @Executable
        String index() {
            name
        }
    }

    @Singleton
    static class MyRouteBuilder extends DefaultRouteBuilder {

        MyRouteBuilder(ExecutionHandleLocator executionHandleLocator,
                       UriNamingStrategy uriNamingStrategy,
                       ConversionService<?> conversionService,
                       BeanContext beanContext) {
            super(executionHandleLocator, uriNamingStrategy, conversionService)

            beanContext.getBeanDefinitions(ConnectionController.class).forEach({ bd ->
                if (bd instanceof NameResolver) {
                    ((NameResolver) bd).resolveName().ifPresent({ name ->
                        bd.findMethod("index").ifPresent({ m ->
                            GET("/connection/$name", bd, m)
                        })
                    })
                }
            })
        }
    }
}
