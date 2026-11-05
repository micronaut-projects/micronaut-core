package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MutableRequestMethodNameSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'MutableRequestMethodNameSpec'])
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "the mutable view of a request keeps the name of a custom method"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.create(HttpMethod.CUSTOM, '/mutable-method-name', methodName), String)

        then:
        response.body() == "$methodName $methodName $methodName"

        where:
        methodName << ['REPORT', 'PROPFIND']
    }

    @Requires(property = 'spec.name', value = 'MutableRequestMethodNameSpec')
    @ServerFilter('/mutable-method-name')
    static class MethodNameFilter {

        @RequestFilter
        HttpResponse<String> filter(HttpRequest<?> request) {
            def mutable = request.mutate()
            return HttpResponse.ok(request.getMethodName() + ' ' + mutable.getMethodName() + ' ' + mutable.mutate().getMethodName())
        }
    }
}
