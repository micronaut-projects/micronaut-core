package io.micronaut.http.server.netty.binding

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.convert.format.Format
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpParameters
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Unroll

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/5135')
class ParameterFailureBindingSpec extends AbstractMicronautSpec {

    @Override
    Map<String, Object> getConfiguration() {
        return super.getConfiguration() << [
                'micronaut.server.strict-argument-conversion': true
        ]
    }

    @Unroll
    void "test bind HTTP parameters for URI #httpMethod #uri"() {
        given:
        HttpRequest req = httpMethod == HttpMethod.GET ? HttpRequest.GET(uri) : HttpRequest.POST(uri, '{}')
        Flux exchange = Flux.from(rxClient.exchange(req, String))
        HttpResponse response = exchange.onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()
        HttpStatus status = response.status
        String body = null
        if (status == HttpStatus.OK) {
            body = response.body()
        }

        expect:
        body == result
        status == httpStatus

        where:
        httpMethod      | uri                                                     | result                | httpStatus
        HttpMethod.GET  | '/parameterNullable/path/20/foo/inv'                    | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/path/inv/foo/10'                    | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/path/20/bar/inv'                    | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/path/inv/bar/10'                    | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/path/inv/bar'                       | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/named?maximum=inv'                  | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.POST | '/parameterNullable/save-again?max=inv'                 | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/path/inv'                           | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/overlap/inv'                        | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/overlap/30?max=inv'                 | "Parameter Value: 30" | HttpStatus.OK
        HttpMethod.GET  | '/parameterNullable/overlap/inv?max=20'                 | null                  | HttpStatus.BAD_REQUEST

        HttpMethod.GET  | '/parameterNullable?max=inv'                            | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameterNullable/simple?max=inv'                     | null                  | HttpStatus.BAD_REQUEST

        HttpMethod.GET  | '/parameterNullable/exploded?title=The%20Stand&age=inv' | null                  | HttpStatus.BAD_REQUEST

        HttpMethod.GET  | '/parameter/list?values=10,inv'                         | null                  | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameter/list?values=10&values=inv'                  | null                  | HttpStatus.BAD_REQUEST
    }

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

            private String title
            private String author
            private int age

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
