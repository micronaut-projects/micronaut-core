package io.micronaut.http.server.netty.errors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.server.exceptions.response.ErrorContext
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HeaderTooLongSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'HeaderTooLongSpec',
            'micronaut.server.netty.log-level': 'info'
    ])

    def 'header too long'() {
        given:
        def connection = new URL("$embeddedServer.URL/malformed-proxy/xyz").openConnection()
        connection.setRequestProperty("foo", "b".repeat(9000))

        def myFilter = embeddedServer.applicationContext.getBean(MyFilter)

        when:
        connection.inputStream
        then:
        thrown IOException
        ((HttpURLConnection) connection).errorStream == null
        myFilter.filteredRequest == null
    }

    @Singleton
    @Requires(property = "spec.name", value = "HeaderTooLongSpec")
    static class BrokenProcessor implements ErrorResponseProcessor<String> {
        @Override
        MutableHttpResponse<String> processResponse(@NonNull ErrorContext errorContext, @NonNull MutableHttpResponse<?> baseResponse) {
            throw new Exception("This processor is intentionally broken")
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "HeaderTooLongSpec")
    @ServerFilter("/**")
    static class MyFilter {
        HttpRequest<?> filteredRequest

        @RequestFilter
        void requestFilter(HttpRequest<?> request) {
            filteredRequest = request
        }
    }
}
