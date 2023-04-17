package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class ParameterFailureBindingSpec extends Specification {

    @Unroll
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5135')
    void "no exeception when path variable and query parameter match"(boolean stringConversionChecking, HttpMethod httpMethod, String uri, String result) {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder()
                .strictConversionChecking(stringConversionChecking)
                .properties(["spec.name": "ParameterFailureBindingSpec"])
                .run(EmbeddedServer)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URI)
        BlockingHttpClient client = httpClient.toBlocking()
        HttpRequest req = httpMethod == HttpMethod.GET ? HttpRequest.GET(uri) : HttpRequest.POST(uri, '{}')

        when:
        HttpResponse response = client.exchange(req, String)

        then:
        HttpStatus.OK == response.status()
        result == response.body()

        cleanup:
        httpClient.close()
        embeddedServer.close()

        where:
        stringConversionChecking | httpMethod      | uri                                     | result
        false                    | HttpMethod.GET  | '/parameterNullable/overlap/30?max=inv' | "Parameter Value: 30"
        true                     | HttpMethod.GET  | '/parameterNullable/overlap/30?max=inv' | "Parameter Value: 30"
    }

    @Unroll
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5135')
    void "stringConversionChecking #stringConversionChecking bind HTTP parameters for URI #httpMethod #uri"(boolean stringConversionChecking, HttpMethod httpMethod, String uri) {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder()
                .strictConversionChecking(stringConversionChecking)
                .properties(["spec.name": "ParameterFailureBindingSpec"])
                .run(EmbeddedServer)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URI)
        BlockingHttpClient client = httpClient.toBlocking()

        HttpRequest req = httpMethod == HttpMethod.GET ? HttpRequest.GET(uri) : HttpRequest.POST(uri, '{}')

        when:
        client.exchange(req, Argument.of(String), Argument.of(String))

        then:
        HttpClientResponseException e = thrown()

        when:
        HttpResponse response = e.response
        HttpStatus status = response.status

        then:
        HttpStatus.BAD_REQUEST == status

        cleanup:
        httpClient.close()
        embeddedServer.close()

        where:
        stringConversionChecking | httpMethod      | uri
        true                     | HttpMethod.GET  | '/parameterNullable/path/20/foo/inv'
        true                     | HttpMethod.GET  | '/parameterNullable/path/inv/foo/10'
        true                     | HttpMethod.GET  | '/parameterNullable/path/20/bar/inv'
        true                     | HttpMethod.GET  | '/parameterNullable/path/inv/bar/10'
        true                     | HttpMethod.GET  | '/parameterNullable/path/inv/bar'
        true                     | HttpMethod.GET  | '/parameterNullable/named?maximum=inv'
        true                     | HttpMethod.POST | '/parameterNullable/save-again?max=inv'
        true                     | HttpMethod.GET  | '/parameterNullable/path/inv'
        true                     | HttpMethod.GET  | '/parameterNullable/overlap/inv'
        true                     | HttpMethod.GET  | '/parameterNullable/overlap/inv?max=20'
        true                     | HttpMethod.GET  | '/parameterNullable?max=inv'
        true                     | HttpMethod.GET  | '/parameterNullable/simple?max=inv'
        true                     | HttpMethod.GET  | '/parameterNullable/exploded?title=The%20Stand&age=inv'
        true                     | HttpMethod.GET  | '/parameter/list?values=10,inv'
        true                     | HttpMethod.GET  | '/parameter/list?values=10&values=inv'
        false                    | HttpMethod.GET  | '/parameterNullable/path/20/foo/inv'
        false                    | HttpMethod.GET  | '/parameterNullable/path/20/bar/inv'
        false                    | HttpMethod.GET  | '/parameterNullable/overlap/inv'
        false                    | HttpMethod.GET  | '/parameterNullable/overlap/inv?max=20'
        false                    | HttpMethod.GET  | '/parameterNullable?max=inv'
        false                    | HttpMethod.GET  | '/parameterNullable/exploded?title=The%20Stand&age=inv'
    }

    @Unroll
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5135')
    void "stringConversionChecking false is the default for binding HTTP parameters for URI #httpMethod #uri"(Boolean stringConversionChecking, HttpMethod httpMethod, String uri, String result) {
        given:
        ApplicationContextBuilder builder = ApplicationContext.builder()
        if (stringConversionChecking != null) {
            builder = builder.strictConversionChecking(stringConversionChecking)
        }
        EmbeddedServer embeddedServer = builder
                .properties(["spec.name": "ParameterFailureBindingSpec"])
                .run(EmbeddedServer)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URI)
        BlockingHttpClient client = httpClient.toBlocking()

        HttpRequest req = httpMethod == HttpMethod.GET ? HttpRequest.GET(uri).accept(MediaType.TEXT_PLAIN) :
                HttpRequest.POST(uri, '{}').accept(MediaType.TEXT_PLAIN)

        when:
        HttpResponse response = client.exchange(req, Argument.of(String), Argument.of(String))

        then:
        noExceptionThrown()

        when:
        HttpStatus status = response.status

        then:
        HttpStatus.OK == status
        and:
        result == response.body()

        cleanup:
        httpClient.close()
        embeddedServer.close()

        where:
        stringConversionChecking | httpMethod      | uri                                      | result
        false                     | HttpMethod.GET  | '/parameterNullable/path/inv/foo/10'    | 'Parameter Values: null 10'
        false                     | HttpMethod.GET  | '/parameterNullable/path/inv/bar/10'    | 'Parameter Values: null 10'
        false                     | HttpMethod.GET  | '/parameterNullable/path/inv/bar'       | 'Parameter Values: null '
        false                     | HttpMethod.GET  | '/parameterNullable/named?maximum=inv'  | 'Parameter Value: null'
        false                     | HttpMethod.POST | '/parameterNullable/save-again?max=inv' | 'Parameter Value: null'
        false                     | HttpMethod.GET  | '/parameterNullable/path/inv'           | 'Parameter Value: null'
        false                     | HttpMethod.GET  | '/parameterNullable/simple?max=inv'     | 'Parameter Value: null'
        false                     | HttpMethod.GET  | '/parameter/list?values=10,inv'         | 'Parameter Value: [10]'
        false                     | HttpMethod.GET  | '/parameter/list?values=10&values=inv'  | 'Parameter Value: [10]'
        null                      | HttpMethod.GET  | '/parameterNullable/path/inv/foo/10'    | 'Parameter Values: null 10'
        null                      | HttpMethod.GET  | '/parameterNullable/path/inv/bar/10'    | 'Parameter Values: null 10'
        null                      | HttpMethod.GET  | '/parameterNullable/path/inv/bar'       | 'Parameter Values: null '
        null                      | HttpMethod.GET  | '/parameterNullable/named?maximum=inv'  | 'Parameter Value: null'
        null                      | HttpMethod.POST | '/parameterNullable/save-again?max=inv' | 'Parameter Value: null'
        null                      | HttpMethod.GET  | '/parameterNullable/path/inv'           | 'Parameter Value: null'
        null                      | HttpMethod.GET  | '/parameterNullable/simple?max=inv'     | 'Parameter Value: null'
        null                      | HttpMethod.GET  | '/parameter/list?values=10,inv'         | 'Parameter Value: [10]'
        null                      | HttpMethod.GET  | '/parameter/list?values=10&values=inv'  | 'Parameter Value: [10]'
    }

    @Requires(property = "spec.name", value = "ParameterFailureBindingSpec")
    @Controller(value = "/parameterNullable", produces = MediaType.TEXT_PLAIN)
    static class ParameterNullableController {
        @Get
        String index(@Nullable Integer max) {
            "Parameter Value: $max"
        }

        @Post("/save")
        String save(@Nullable Integer max) {
            "Parameter Value: $max"
        }

        @Post("/save-again")
        String saveAgain(@QueryValue @Nullable Integer max) {
            "Parameter Value: $max"
        }

        @Get('/overlap/{max}')
        String overlap(@PathVariable @Nullable Integer max) {
            "Parameter Value: $max"
        }

        @Get("/simple")
        String simple(@QueryValue @Nullable Integer max) {
            "Parameter Value: $max"
        }

        @Get('/path/{max}')
        String path(@PathVariable("max") @Nullable Integer maximum) {
            "Parameter Value: $maximum"
        }

        @Get('/path/{id}/foo/{fooId}')
        String path2(@PathVariable("id") @Nullable Long someId, @Nullable Long fooId) {
            "Parameter Values: $someId $fooId"
        }

        @Get('/path/{id}/bar{/barId}')
        String optionalPath(@PathVariable("id") @Nullable Long someId, @Nullable Long barId) {
            "Parameter Values: $someId ${barId ?: ''}"
        }

        @Get("/named")
        String named(@QueryValue('maximum') @Nullable Integer max) {
            "Parameter Value: $max"
        }

        @Get("/exploded{?book*}")
        String exploded(@Nullable Book book) {
            "Parameter Value: $book.title $book.age"
        }

        @Post('/query')
        String queryPost(@QueryValue @Nullable String name) {
            "Parameter Value: $name"
        }

        @Get("/list")
        String list(List<Integer> values) {
            assert values.every() { it instanceof Integer }
            "Parameter Value: ${values.inspect()}"
        }

        @Introspected
        static class Book {

            private final String title
            private final String author
            private final int age

            Book(String title, Integer age, @Nullable String author) {
                this.age = age
                this.title = title
                this.author = author
            }

            String getTitle() {
                return title
            }

            int getAge() {
                return age
            }

        }
    }
}
