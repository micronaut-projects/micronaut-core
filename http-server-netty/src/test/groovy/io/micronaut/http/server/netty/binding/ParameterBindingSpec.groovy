package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Parameter
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpParameters
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import spock.lang.Unroll

import javax.annotation.Nullable

/**
 * Created by graemerocher on 25/08/2017.
 */
class ParameterBindingSpec extends AbstractMicronautSpec {


    @Unroll
    void "test bind HTTP parameters for URI #httpMethod #uri"() {

        given:
        def req = httpMethod == HttpMethod.GET ? HttpRequest.GET(uri) : HttpRequest.POST(uri, '{}')
        def exchange = rxClient.exchange(req, String)
        def response = exchange.onErrorReturn({ t -> t.response }).blockingFirst()
        def status = response.status
        def body = null
        if (status == HttpStatus.OK) {
            body = response.body()
        }

        expect:
        body == result
        status == httpStatus



        where:
        httpMethod      | uri                                             | result                      | httpStatus
        // you can't populate post request data from query parameters without explicit @QueryValue
        HttpMethod.POST | '/parameter/save?max=30'                        | null                        | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameter/path/20/foo/10'                     | "Parameter Values: 20 10"    | HttpStatus.OK
        HttpMethod.GET  | '/parameter/path/20/bar/10'                     | "Parameter Values: 20 10"    | HttpStatus.OK
        HttpMethod.GET  | '/parameter/path/20/bar'                        | "Parameter Values: 20 "      | HttpStatus.OK
        HttpMethod.GET  | '/parameter/named?maximum=20'                   | "Parameter Value: 20"       | HttpStatus.OK
        HttpMethod.POST | '/parameter/saveAgain?max=30'                   | "Parameter Value: 30"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/path/20'                            | "Parameter Value: 20"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/simple'                             | null                        | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameter/named'                              | null                        | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameter/overlap/30'                         | "Parameter Value: 30"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/overlap/30?max=50'                  | "Parameter Value: 30"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/map?values.max=20&values.offset=30' | "Parameter Value: 20 30"    | HttpStatus.OK
        HttpMethod.GET  | '/parameter/optional?max=20'                    | "Parameter Value: 20"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/list?values=10,20'                  | "Parameter Value: [10, 20]" | HttpStatus.OK
        HttpMethod.GET  | '/parameter/list?values=10&values=20'           | "Parameter Value: [10, 20]" | HttpStatus.OK
        HttpMethod.GET  | '/parameter/optionalList?values=10&values=20'   | "Parameter Value: [10, 20]" | HttpStatus.OK
        HttpMethod.GET  | '/parameter?max=20'                             | "Parameter Value: 20"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/simple?max=20'                      | "Parameter Value: 20"       | HttpStatus.OK

        HttpMethod.GET  | '/parameter/optional'                           | "Parameter Value: 10"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/all'                                | "Parameter Value: 10"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/all?max=20'                         | "Parameter Value: 20"       | HttpStatus.OK

    }

    @Controller(produces = MediaType.TEXT_PLAIN)
    static class ParameterController {
        @Get('/')
        String index(Integer max) {
            "Parameter Value: $max"
        }

        @Post
        String save(Integer max) {
            "Parameter Value: $max"
        }

        @Post
        String saveAgain(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @Get('/overlap/{max}')
        String overlap(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @Get
        String simple(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @Get('/path/{max}')
        String path(@Parameter("max") Integer maximum) {
            "Parameter Value: $maximum"
        }

        @Get('/path/{id}/foo/{fooId}')
        String path2(@Parameter("id") Long someId, Long fooId) {
            "Parameter Values: $someId $fooId"
        }

        @Get('/path/{id}/bar{/barId}')
        String optionalPath(@Parameter("id") Long someId, @Nullable Long barId) {
            "Parameter Values: $someId ${barId ?: ''}"
        }

        @Get
        String named(@QueryValue('maximum') Integer max) {
            "Parameter Value: $max"
        }

        @Get
        String optional(@QueryValue Optional<Integer> max) {
            "Parameter Value: ${max.orElse(10)}"
        }


        @Get
        String all(HttpParameters parameters) {
            "Parameter Value: ${parameters.get('max', Integer, 10)}"
        }

        @Get
        String map(Map<String, Integer> values) {
            "Parameter Value: ${values.max} ${values.offset}"
        }

        @Get
        String list(List<Integer> values) {
            assert values.every() { it instanceof Integer }
            "Parameter Value: ${values.inspect()}"
        }

        @Get
        String optionalList(Optional<List<Integer>> values) {
            if (values.isPresent()) {
                assert values.get().every() { it instanceof Integer }
                "Parameter Value: ${values.get()}"
            } else {
                "Parameter Value: none"
            }
        }
    }
}
