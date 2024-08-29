package io.micronaut

import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.web.router.Router

class PleaseEnableRoutesBySpecNameSpec extends AbstractMicronautSpec {

    void "don't add more public routes in test"() {
        // A lot of activate routes, filters etc. complicates debugging
        // Please use @Requires(property="spec.name.. to enable your route and filters only for particular tests
        given:
            Router router = applicationContext.getBean(Router)
        expect:
            router.uriRoutes().toList().size() == 0
    }


}
