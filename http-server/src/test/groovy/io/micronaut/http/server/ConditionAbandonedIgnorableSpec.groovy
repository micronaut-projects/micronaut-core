package io.micronaut.http.server

import spock.lang.Specification

import java.util.concurrent.CompletionException

class ConditionAbandonedIgnorableSpec extends Specification {

    void "a request abandoned while its body conditions are decided is an ignorable error, as an abandoned location is"() {
        given: "the error BodyRouteSelector fails the selection with when the client goes away"
        def constructor = Class.forName('io.micronaut.http.server.BodyRouteSelector$ConditionAbandoned').getDeclaredConstructor()
        constructor.accessible = true
        Throwable abandoned = (Throwable) constructor.newInstance()

        expect:
        RouteExecutor.isIgnorable(abandoned)
        RouteExecutor.isIgnorable(new CompletionException(abandoned))

        and:
        !RouteExecutor.isIgnorable(new IllegalStateException("other"))
        !RouteExecutor.isIgnorable(new CompletionException(new IllegalStateException("other")))
    }
}
