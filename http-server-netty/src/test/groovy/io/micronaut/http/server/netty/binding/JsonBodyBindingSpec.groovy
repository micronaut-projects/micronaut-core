package io.micronaut.http.server.netty.binding

import com.fasterxml.jackson.annotation.JsonCreator
import groovy.json.JsonSlurper
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.json.JsonSyntaxException
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import spock.lang.Issue

import java.util.concurrent.CompletableFuture

class JsonBodyBindingSpec extends AbstractMicronautSpec {

    @Override
    Map<String, Object> getConfiguration() {
        return super.getConfiguration() + ['test.controller': 'JsonController']
    }

    void "test JSON is not parsed when the body is a raw body type"() {
        when:
        String json = '{"title":"The Stand"'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/string', json), String
        )).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'Body: {"title":"The Stand"'
    }

    void "test JSON is not parsed when the body is a raw body type in a request argument"() {
        when:
        String json = '{"title":"The Stand"'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/request-string', json), String
        )).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'Body: {"title":"The Stand"'
    }

    void "test parse body into parameters if no @Body specified"() {
        when:
        String json = '{"name":"Fred", "age":10}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/params', json), String
        )).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Body: Foo(Fred, 10)"
    }

    void "test map-based body parsing with invalid JSON"() {

        when:
        String json = '{"title":The Stand}'
        Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/map', json), String
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.message == """Invalid JSON: Unrecognized token 'The': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 14]"""
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        HttpResponse<?> response = e.response
        String body = e.response.getBody(String).orElse(null)
        def result = new JsonSlurper().parseText(body)

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        response.headers.get(HttpHeaders.CONTENT_TYPE) == io.micronaut.http.MediaType.APPLICATION_JSON
        result['_links'].self.href == '/json/map'
        result.message.startsWith('Invalid JSON')
    }

    void "test simple map body parsing"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/map', json), String
        )).blockFirst()

        then:
        response.body() == "Body: [title:The Stand]"
    }

    void "test simple string-based body parsing"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/string', json), String
        )).blockFirst()

        then:
        response.body() == "Body: $json"
    }

    void "test binding to part of body with @Body(name)"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/body-title', json), String
        )).blockFirst()

        then:
        response.body() == "Body Title: The Stand"
    }

    void  "test simple string-based body parsing with request argument"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/request-string', json), String
        )).blockFirst()

        then:
        response.body() == "Body: $json"
    }

    void "test simple string-based body parsing with invalid mime type"() {
        when:
        String json = '{"title":"The Stand"}'
        Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/map', json).contentType(io.micronaut.http.MediaType.APPLICATION_ATOM_XML_TYPE), String
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNSUPPORTED_MEDIA_TYPE
    }

    void "test simple POGO body parsing"() {
        when:
        String json = '{"name":"Fred", "age":10}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/object', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10)"
    }

    void "test simple POGO body parse and return"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/object-to-object', json), String
        )).blockFirst()

        then:
        response.body() == json
    }

    void "test array POGO body parsing"() {
        when:
        String json = '[{"name":"Fred", "age":10},{"name":"Barney", "age":11}]'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/array', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10),Foo(Barney, 11)"
    }

    void "test array POGO body parsing and return"() {
        when:
        String json = '[{"name":"Fred","age":10},{"name":"Barney","age":11}]'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/array-to-array', json), String
        )).blockFirst()

        then:
        response.body() == json
    }

    void "test list POGO body parsing"() {
        when:
        String json = '[{"name":"Fred", "age":10},{"name":"Barney", "age":11}]'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/list', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10),Foo(Barney, 11)"
    }

    void "test nested POGO body parsing"() {
        when:
        String json = '{"foo":{"name":"Fred", "age":10}}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/nested', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10)"
    }

    void "test future argument handling with string"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/future', json), String
        )).blockFirst()

        then:
        response.body() == "Body: $json".toString()
    }

    void "test future argument handling with map"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/future-map', json), String
        )).blockFirst()

        then:
        response.body() == "Body: [name:Fred, age:10]".toString()
    }

    void "test future argument handling with POGO"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/future-object', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10)".toString()
    }

    void "test publisher argument handling with POGO"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/publisher-object', json), String
        )).blockFirst()

        then:
        response.body() == "[Foo(Fred, 10)]".toString()
    }

    void "test singe argument handling"() {
        when:
        String json = '{"message":"foo"}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/single', json), String
        )).blockFirst()

        then:
        response.body() == "$json".toString()
    }

    void "test request generic type binding"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/request-generic', json), String
        )).blockFirst()

        then:
        response.body() == "Foo(Fred, 10)".toString()
    }

    void "test request generic type no body"() {
        when:
        String json = ''
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/request-generic', json), String
        )).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'not found'
    }

    void "test request generic type conversion error"() {
        when:
        String json = '[1,2,3]'
        Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/request-generic', json), String
        )).blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.getStatus() == HttpStatus.BAD_REQUEST
        response.body().toString().contains("no int/Int-argument constructor/factory method to deserialize from Number value")
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/5088")
    void "test deserializing a wrapper of list of pojos"() {
        when:
        String json = '[{"name":"Joe"},{"name":"Sally"}]'
        HttpResponse<String> response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/json/deserialize-listwrapper', json), String
        )).blockFirst()

        then:
        response.body() == '["Joe","Sally"]'
    }

  @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10665")
  void "ServerRequestContext not available on deserialization for large payloads "() {
    when:
    String json = """
{"payload":"JVBERi0xLjMNCiXi48/TDQoNCjEgMCBvYmoNCjw8DQovVHlwZSAvQ2F0YWxvZw0KL091dGxpbmVzIDIgMCBSDQovUGFnZXMgMyAwIFINCj4+DQplbmRvYmoNCg0KMiAwIG9iag0KPDwNCi9UeXBlIC9PdXRsaW5lcw0KL0NvdW50IDANCj4+DQplbmRvYmoNCg0KMyAwIG9iag0KPDwNCi9UeXBlIC9QYWdlcw0KL0NvdW50IDINCi9LaWRzIFsgNCAwIFIgNiAwIFIgXSANCj4+DQplbmRvYmoNCg0KNCAwIG9iag0KPDwNCi9UeXBlIC9QYWdlDQovUGFyZW50IDMgMCBSDQovUmVzb3VyY2VzIDw8DQovRm9udCA8PA0KL0YxIDkgMCBSIA0KPj4NCi9Qcm9jU2V0IDggMCBSDQo+Pg0KL01lZGlhQm94IFswIDAgNjEyLjAwMDAgNzkyLjAwMDBdDQovQ29udGVudHMgNSAwIFINCj4+DQplbmRvYmoNCg0KNSAwIG9iag0KPDwgL0xlbmd0aCAxMDc0ID4+DQpzdHJlYW0NCjIgSg0KQlQNCjAgMCAwIHJnDQovRjEgMDAyNyBUZg0KNTcuMzc1MCA3MjIuMjgwMCBUZA0KKCBBIFNpbXBsZSBQREYgRmlsZSApIFRqDQpFVA0KQlQNCi9GMSAwMDEwIFRmDQo2OS4yNTAwIDY4OC42MDgwIFRkDQooIFRoaXMgaXMgYSBzbWFsbCBkZW1vbnN0cmF0aW9uIC5wZGYgZmlsZSAtICkgVGoNCkVUDQpCVA0KL0YxIDAwMTAgVGYNCjY5LjI1MDAgNjY0LjcwNDAgVGQNCigganVzdCBmb3IgdXNlIGluIHRoZSBWaXJ0dWFsIE1lY2hhbmljcyB0dXRvcmlhbHMuIE1vcmUgdGV4dC4gQW5kIG1vcmUgKSBUag0KRVQNCkJUDQovRjEgMDAxMCBUZg0KNjkuMjUwMCA2NTIuNzUyMCBUZA0KKCB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiApIFRqDQpFVA0KQlQNCi9GMSAwMDEwIFRmDQo2OS4yNTAwIDYyOC44NDgwIFRkDQooIEFuZCBtb3JlIHRleHQuIEFuZCBtb3JlIHRleHQuIEFuZCBtb3JlIHRleHQuIEFuZCBtb3JlIHRleHQuIEFuZCBtb3JlICkgVGoNCkVUDQpCVA0KL0YxIDAwMTAgVGYNCjY5LjI1MDAgNjE2Ljg5NjAgVGQNCiggdGV4dC4gQW5kIG1vcmUgdGV4dC4gQm9yaW5nLCB6enp6ei4gQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gQW5kICkgVGoNCkVUDQpCVA0KL0YxIDAwMTAgVGYNCjY5LjI1MDAgNjA0Ljk0NDAgVGQNCiggbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiApIFRqDQpFVA0KQlQNCi9GMSAwMDEwIFRmDQo2OS4yNTAwIDU5Mi45OTIwIFRkDQooIEFuZCBtb3JlIHRleHQuIEFuZCBtb3JlIHRleHQuICkgVGoNCkVUDQpCVA0KL0YxIDAwMTAgVGYNCjY5LjI1MDAgNTY5LjA4ODAgVGQNCiggQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgKSBUag0KRVQNCkJUDQovRjEgMDAxMCBUZg0KNjkuMjUwMCA1NTcuMTM2MCBUZA0KKCB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBFdmVuIG1vcmUuIENvbnRpbnVlZCBvbiBwYWdlIDIgLi4uKSBUag0KRVQNCmVuZHN0cmVhbQ0KZW5kb2JqDQoNCjYgMCBvYmoNCjw8DQovVHlwZSAvUGFnZQ0KL1BhcmVudCAzIDAgUg0KL1Jlc291cmNlcyA8PA0KL0ZvbnQgPDwNCi9GMSA5IDAgUiANCj4+DQovUHJvY1NldCA4IDAgUg0KPj4NCi9NZWRpYUJveCBbMCAwIDYxMi4wMDAwIDc5Mi4wMDAwXQ0KL0NvbnRlbnRzIDcgMCBSDQo+Pg0KZW5kb2JqDQoNCjcgMCBvYmoNCjw8IC9MZW5ndGggNjc2ID4+DQpzdHJlYW0NCjIgSg0KQlQNCjAgMCAwIHJnDQovRjEgMDAyNyBUZg0KNTcuMzc1MCA3MjIuMjgwMCBUZA0KKCBTaW1wbGUgUERGIEZpbGUgMiApIFRqDQpFVA0KQlQNCi9GMSAwMDEwIFRmDQo2OS4yNTAwIDY4OC42MDgwIFRkDQooIC4uLmNvbnRpbnVlZCBmcm9tIHBhZ2UgMS4gWWV0IG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gKSBUag0KRVQNCkJUDQovRjEgMDAxMCBUZg0KNjkuMjUwMCA2NzYuNjU2MCBUZA0KKCBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSB0ZXh0LiBBbmQgbW9yZSApIFRqDQpFVA0KQlQNCi9GMSAwMDEwIFRmDQo2OS4yNTAwIDY2NC43MDQwIFRkDQooIHRleHQuIE9oLCBob3cgYm9yaW5nIHR5cGluZyB0aGlzIHN0dWZmLiBCdXQgbm90IGFzIGJvcmluZyBhcyB3YXRjaGluZyApIFRqDQpFVA0KQlQNCi9GMSAwMDEwIFRmDQo2OS4yNTAwIDY1Mi43NTIwIFRkDQooIHBhaW50IGRyeS4gQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gQW5kIG1vcmUgdGV4dC4gKSBUag0KRVQNCkJUDQovRjEgMDAxMCBUZg0KNjkuMjUwMCA2NDAuODAwMCBUZA0KKCBCb3JpbmcuICBNb3JlLCBhIGxpdHRsZSBtb3JlIHRleHQuIFRoZSBlbmQsIGFuZCBqdXN0IGFzIHdlbGwuICkgVGoNCkVUDQplbmRzdHJlYW0NCmVuZG9iag0KDQo4IDAgb2JqDQpbL1BERiAvVGV4dF0NCmVuZG9iag0KDQo5IDAgb2JqDQo8PA0KL1R5cGUgL0ZvbnQNCi9TdWJ0eXBlIC9UeXBlMQ0KL05hbWUgL0YxDQovQmFzZUZvbnQgL0hlbHZldGljYQ0KL0VuY29kaW5nIC9XaW5BbnNpRW5jb2RpbmcNCj4+DQplbmRvYmoNCg0KMTAgMCBvYmoNCjw8DQovQ3JlYXRvciAoUmF2ZSBcKGh0dHA6Ly93d3cubmV2cm9uYS5jb20vcmF2ZVwpKQ0KL1Byb2R1Y2VyIChOZXZyb25hIERlc2lnbnMpDQovQ3JlYXRpb25EYXRlIChEOjIwMDYwMzAxMDcyODI2KQ0KPj4NCmVuZG9iag0KDQp4cmVmDQowIDExDQowMDAwMDAwMDAwIDY1NTM1IGYNCjAwMDAwMDAwMTkgMDAwMDAgbg0KMDAwMDAwMDA5MyAwMDAwMCBuDQowMDAwMDAwMTQ3IDAwMDAwIG4NCjAwMDAwMDAyMjIgMDAwMDAgbg0KMDAwMDAwMDM5MCAwMDAwMCBuDQowMDAwMDAxNTIyIDAwMDAwIG4NCjAwMDAwMDE2OTAgMDAwMDAgbg0KMDAwMDAwMjQyMyAwMDAwMCBuDQowMDAwMDAyNDU2IDAwMDAwIG4NCjAwMDAwMDI1NzQgMDAwMDAgbg0KDQp0cmFpbGVyDQo8PA0KL1NpemUgMTENCi9Sb290IDEgMCBSDQovSW5mbyAxMCAwIFINCj4+DQoNCnN0YXJ0eHJlZg0KMjcxNA0KJSVFT0YNCg=="}
"""
    HttpResponse<Boolean> response = Flux.from(httpClient.exchange(
      HttpRequest.POST('/json/request-context', json), String
    )).blockFirst()

    then:
    response.body() == 'true'
  }

    void "Test part @Body with primitive defaultValue"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response1 = Flux.from(httpClient.exchange(
                HttpRequest.POST("/json/part-body-default-primitive", json), String
        )).blockFirst()

        then:
        response1.code() == 200
        response1.body() == "Body: {\"name\":\"Fred\", \"age\":10}"
    }

    void "Test part @Body with object defaultValue"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response1 = Flux.from(httpClient.exchange(
                HttpRequest.POST("/json/part-body-default-object", json), String
        )).blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.getStatus() == HttpStatus.BAD_REQUEST
    }

    void "Test full @Body with primitive defaultValue"() {
        when:
        String json = ''
        HttpResponse<String> response1 = Flux.from(httpClient.exchange(
                HttpRequest.POST("/json/full-body-default-primitive", json), String
        )).blockFirst()

        then:
        response1.code() == 200
        response1.body() == "Body: {\"name\":\"Fred\", \"age\":10}"
    }

    void "Test full @Body with object defaultValue"() {
        when:
        String json = ''
        HttpResponse<String> response1 = Flux.from(httpClient.exchange(
                HttpRequest.POST("/json/full-body-default-object", json), String
        )).blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.getStatus() == HttpStatus.BAD_REQUEST
    }

  @Controller(value = "/json", produces = io.micronaut.http.MediaType.APPLICATION_JSON)
    @Requires(property = "test.controller", value = "JsonController")
    static class JsonController {

        @Post("/params")
        String params(String name, int age) {
            "Body: ${new Foo(name: name, age: age)}"
        }

        @Post("/string")
        String string(@Body String text) {
            "Body: ${text}"
        }

        @Post("/body-title")
        String bodyNamed(@Body("title") String text) {
            "Body Title: ${text}"
        }

        @Post("/request-string")
        String requestString(HttpRequest<String> req) {
            "Body: ${req.body.orElse("empty")}"
        }

        @Post("/map")
        String map(@Body Map<String, Object> json) {
            "Body: ${json}"
        }

        @Post("/object")
        String object(@Body Foo foo) {
            "Body: $foo"
        }

        @Post("/object-to-object")
        Foo objectToObject(@Body Foo foo) {
            return foo
        }

        @Post("/array") array(@Body Foo[] foos) {
            "Body: ${foos.join(',')}"
        }

        @Post("/array-to-array") arrayToArray(@Body Foo[] foos) {
            return foos
        }

        @Post("/list") list(@Body List<Foo> foos) {
            "Body: ${foos.join(',')}"
        }

        @Post("/nested")
        String nested(@Body('foo') Foo foo) {
            "Body: $foo"
        }

        @Post("/single")
        @SingleResult
        Publisher<String> single(@Body Publisher<String> message) {
            message
        }

        @Post("/future")
        CompletableFuture<String> future(@Body CompletableFuture<String> future) {
            future.thenApply({ String json ->
                "Body: $json".toString()
            })
        }

        @Post("/future-map")
        CompletableFuture<String> futureMap(@Body CompletableFuture<Map<String,Object>> future) {
            future.thenApply({ Map<String,Object> json ->
                "Body: $json".toString()
            })
        }


        @Post("/future-object")
        CompletableFuture<String> futureObject(@Body CompletableFuture<Foo> future) {
            future.thenApply({ Foo foo ->
                "Body: $foo".toString()
            })
        }

        @Post("/publisher-object")
        Publisher<String> publisherObject(@Body Publisher<Foo> publisher) {
            return Flux.from(publisher)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map({ Foo foo ->
                        foo.toString()
            })
        }

        @Post("/request-generic")
        String requestGeneric(HttpRequest<Foo> request) {
            return request.getBody().map({ foo -> foo.toString()}).orElse("not found")
        }

        @Post("/deserialize-listwrapper")
        List<String> requestListWrapper(@Body MyReqBody myReqBody) {
            return myReqBody.items*.name
        }

        @Post("/request-context")
        String verifyContextAccessOnDeserialization(@Body ContextChecker contextChecker) {
            return Boolean.toString(contextChecker.contextAccess)
        }

        @Post("/part-body-default-primitive")
        String partBodyDefaultPrimitive(@Body(value = 'foo', defaultValue = '{"name":"Fred", "age":10}') String body) {
            return "Body: $body"
        }

        @Post("/part-body-default-object")
        String partBodyDefaultObject(@Body(value = 'foo', defaultValue = '{"name":"Fred", "age":10}') Foo body) {
            return "Body: $body"
        }

        @Post("/full-body-default-primitive")
        String fullBodyDefaultPrimitive(@Body(defaultValue = '{"name":"Fred", "age":10}') String body) {
            return "Body: $body"
        }

        @Post("/full-body-default-object")
        String fullBodyDefaultObject(@Body(defaultValue = '{"name":"Fred", "age":10}') Foo body) {
            return "Body: $body"
        }


        @Error(JsonSyntaxException)
        HttpResponse jsonError(HttpRequest request, JsonSyntaxException jsonSyntaxException) {
            def response = HttpResponse.status(HttpStatus.BAD_REQUEST, "No!! Invalid JSON")
            def error = new JsonError("Invalid JSON: ${jsonSyntaxException.message}")
            error.link(Link.SELF, Link.of(request.getUri()))
            response.body(error)
            return response
        }
    }

    static class Foo {
        String name
        Integer age

        @Override
        String toString() {
            "Foo($name, $age)"
        }
    }

    @Introspected
    static class MyReqBody {

        private final List<MyItem> items

        @JsonCreator
        MyReqBody(final List<MyItem> items) {
            this.items = items
        }

        List<MyItem> getItems() {
            items
        }
    }

    @Introspected
    static class MyItem {
        String name
    }

  @Introspected
  static class ContextChecker {
    private final String payload;
    public Boolean contextAccess;

    @JsonCreator
    ContextChecker(final payload) {
      this.payload = payload
      this.contextAccess = ServerRequestContext.currentRequest().isPresent()
    }
  }
}
