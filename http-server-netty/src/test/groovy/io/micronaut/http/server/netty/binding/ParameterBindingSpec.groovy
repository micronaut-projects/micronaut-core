package io.micronaut.http.server.netty.binding

import io.micronaut.http.HttpParameters
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Parameter
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import spock.lang.Unroll

/**
 * Created by graemerocher on 25/08/2017.
 */
class ParameterBindingSpec extends AbstractMicronautSpec {


    @Unroll
    void "test bind HTTP parameters for URI #uri"() {
        given:
        def response = rxClient.exchange(uri, String)
                               .onErrorReturn({t -> t.response}).blockingFirst()
        def status = response.status
        def body = null
        if (status == HttpStatus.OK) {
            body = response.body()
        }

        expect:
        body == result
        status == httpStatus



        where:
        uri                                             | result                      | httpStatus
        '/parameter/simple'                             | null                        | HttpStatus.BAD_REQUEST
        '/parameter/named'                              | null                        | HttpStatus.BAD_REQUEST
        '/parameter/map?values.max=20&values.offset=30' | "Parameter Value: 20 30"    | HttpStatus.OK
        '/parameter/optional?max=20'                    | "Parameter Value: 20"       | HttpStatus.OK
        '/parameter/list?values=10,20'                  | "Parameter Value: [10, 20]" | HttpStatus.OK
        '/parameter/list?values=10&values=20'           | "Parameter Value: [10, 20]" | HttpStatus.OK
        '/parameter/optionalList?values=10&values=20'   | "Parameter Value: [10, 20]" | HttpStatus.OK
        '/parameter?max=20'                             | "Parameter Value: 20"       | HttpStatus.OK
        '/parameter/simple?max=20'                      | "Parameter Value: 20"       | HttpStatus.OK
        '/parameter/named?maximum=20'                   | "Parameter Value: 20"       | HttpStatus.OK
        '/parameter/optional'                           | "Parameter Value: 10"       | HttpStatus.OK
        '/parameter/all'                                | "Parameter Value: 10"       | HttpStatus.OK
        '/parameter/all?max=20'                         | "Parameter Value: 20"       | HttpStatus.OK

    }

    @Controller(produces = MediaType.TEXT_PLAIN)
    static class ParameterController {
        @Get('/')
        String index(Integer max) {
            "Parameter Value: $max"
        }

        @Get
        String simple(@Parameter Integer max) {
            "Parameter Value: $max"
        }

        @Get
        String named(@Parameter('maximum') Integer max) {
            "Parameter Value: $max"
        }

        @Get
        String optional(@Parameter Optional<Integer> max) {
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
