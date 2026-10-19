package io.micronaut.http.server.netty.resources

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class StaticResourceWatchSpec extends Specification {

    void "a static resource mapping that changed applies to the next request"() {
        given:
        Map<String, Object> values = [
            "micronaut.router.static-resources.default.paths": ["classpath:public"],
            "micronaut.router.static-resources.default.mapping": "/static/**",
        ]
        def server = ApplicationContext.run(EmbeddedServer, values)
        def context = server.applicationContext
        def client = context.createBean(HttpClient, server.URL).toBlocking()

        expect:
        client.exchange(HttpRequest.GET("/static/index.html"), String).status == HttpStatus.OK

        when: "the mapping moves and the configuration is refreshed"
        values["micronaut.router.static-resources.default.mapping"] = "/assets/**"
        def result = context.getBean(ConfigurationRefresher).refresh()

        then:
        !result.requiresRestart()
        client.exchange(HttpRequest.GET("/assets/index.html"), String).status == HttpStatus.OK

        when:
        client.exchange(HttpRequest.GET("/static/index.html"), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        client.close()
        server.close()
    }
}
