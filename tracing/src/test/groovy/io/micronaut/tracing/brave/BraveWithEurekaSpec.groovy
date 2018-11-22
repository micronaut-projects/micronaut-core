package io.micronaut.tracing.brave

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.annotation.Post
import io.micronaut.web.router.Router
import spock.lang.Issue
import spock.lang.Specification

class BraveWithEurekaSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/811')
    void "test that brave does not cause early init of beans"() {

        given:
        ApplicationContext ctx = ApplicationContext.build()
            .properties(
                'eureka.client.registration.enabled':true,
                'eureka.client.defaultZone':'${EUREKA_HOST:localhost}:${EUREKA_PORT:9001}',
                'tracing.zipkin.http.url': 'http://localhost:9411',
                'tracing.zipkin.enabled':true

        ).build().start()

        expect:
        ctx.getBean(Router).route(HttpMethod.POST, '/api/v2/spans').isPresent()
    }
}
