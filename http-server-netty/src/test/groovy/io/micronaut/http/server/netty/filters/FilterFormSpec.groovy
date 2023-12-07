package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import spock.lang.Issue
import spock.lang.Specification

class FilterFormSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/9917')
    def "exception in form parameter"() {
        given:
        def ctx = ApplicationContext.run(["spec.name": "FilterFormSpec"])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def resp = client.exchange(HttpRequest.POST("/form-error", MultipartBody.builder().addPart("file", "foo").build()).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)
        then:
        resp.body() == "Ok 3"

        cleanup:
        client.close()
        server.close()
        ctx.close()
    }

    @Controller
    @Requires(property = "spec.name", value = "FilterFormSpec")
    static class Ctrl {
        @Post(value = "/form-error", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        def upload(byte[] file) {
            return "Ok $file.length"
        }
    }

    @Requires(property = "spec.name", value = "FilterFormSpec")
    @ServerFilter("/form-error")
    static class Fltr {
        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        void filterRequest() {
        }
    }
}
