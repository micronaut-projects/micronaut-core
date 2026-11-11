package io.micronaut.http.server.netty.binding

import tools.jackson.databind.JsonNode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JsonPartCollectionSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JsonPartCollectionSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URI)

    void "a JSON file part is read by its content type into a #path"(String path) {
        when:
        def body = MultipartBody.builder()
                .addPart('people', 'people.json', MediaType.APPLICATION_JSON_TYPE, '[{"name":"Fred"},{"name":"Barney"}]'.bytes)
                .build()
        def response = client.toBlocking().retrieve(HttpRequest.POST("/json-part/$path", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)

        then:
        response == 'Fred,Barney'

        where:
        path << ['list', 'array', 'node']
    }

    @Requires(property = 'spec.name', value = 'JsonPartCollectionSpec')
    @Controller('/json-part')
    @Produces(MediaType.TEXT_PLAIN)
    static class JsonPartController {

        @Post(value = '/list', consumes = MediaType.MULTIPART_FORM_DATA)
        String list(@Part('people') List<Person> people) {
            return people*.name.join(',')
        }

        @Post(value = '/array', consumes = MediaType.MULTIPART_FORM_DATA)
        String array(@Part('people') Person[] people) {
            return people*.name.join(',')
        }

        @Post(value = '/node', consumes = MediaType.MULTIPART_FORM_DATA)
        String node(@Part('people') JsonNode people) {
            return people.collect { it.get("name").asString() }.join(',')
        }
    }

    @Introspected
    static class Person {
        String name
    }
}
