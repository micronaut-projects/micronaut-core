/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.binding

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.*
import io.micronaut.core.convert.format.Format
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.server.netty.AbstractMicronautSpec
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
        HttpMethod.GET  | '/parameter/path/20/foo/10'                     | "Parameter Values: 20 10"   | HttpStatus.OK
        HttpMethod.GET  | '/parameter/path/20/bar/10'                     | "Parameter Values: 20 10"   | HttpStatus.OK
        HttpMethod.GET  | '/parameter/path/20/bar'                        | "Parameter Values: 20 "     | HttpStatus.OK
        HttpMethod.GET  | '/parameter/named?maximum=20'                   | "Parameter Value: 20"       | HttpStatus.OK
        HttpMethod.POST | '/parameter/save-again?max=30'                  | "Parameter Value: 30"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/path/20'                            | "Parameter Value: 20"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/simple'                             | null                        | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameter/named'                              | null                        | HttpStatus.BAD_REQUEST
        HttpMethod.GET  | '/parameter/overlap/30'                         | "Parameter Value: 30"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/overlap/30?max=50'                  | "Parameter Value: 30"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/map?values.max=20&values.offset=30' | "Parameter Value: 20 30"    | HttpStatus.OK
        HttpMethod.GET  | '/parameter/optional?max=20'                    | "Parameter Value: 20"       | HttpStatus.OK

        HttpMethod.GET  | '/parameter/set?values=10,20'                   | "Parameter Value: [10, 20]" | HttpStatus.OK
        HttpMethod.GET  | '/parameter/list?values=10,20'                  | "Parameter Value: [10, 20]" | HttpStatus.OK
        HttpMethod.GET  | '/parameter/list?values=10&values=20'           | "Parameter Value: [10, 20]" | HttpStatus.OK
        HttpMethod.GET  | '/parameter/set?values=10&values=20'            | "Parameter Value: [10, 20]" | HttpStatus.OK
        HttpMethod.GET  | '/parameter/optional-list?values=10&values=20'  | "Parameter Value: [10, 20]" | HttpStatus.OK
        HttpMethod.GET  | '/parameter/optional-date?date=1941-01-05'      | "Parameter Value: 1941"     | HttpStatus.OK
        HttpMethod.GET  | '/parameter?max=20'                             | "Parameter Value: 20"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/simple?max=20'                      | "Parameter Value: 20"       | HttpStatus.OK

        HttpMethod.GET  | '/parameter/optional'                           | "Parameter Value: 10"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/all'                                | "Parameter Value: 10"       | HttpStatus.OK
        HttpMethod.GET  | '/parameter/all?max=20'                         | "Parameter Value: 20"       | HttpStatus.OK

        HttpMethod.GET  | '/parameter/exploded?title=The%20Stand&age=20'  | "Parameter Value: The Stand 20" | HttpStatus.OK
        HttpMethod.GET  | '/parameter/queryName/Fr%20ed'                  | "Parameter Value: Fr ed"    | HttpStatus.OK
        HttpMethod.POST | '/parameter/query?name=Fr%20ed'                 | "Parameter Value: Fr ed"    | HttpStatus.OK
        HttpMethod.GET  | '/parameter/arrayStyle?param[]=a&param[]=b&param[]=c' | "Parameter Value: [a, b, c]"    | HttpStatus.OK
    }

    void "test list to single error"() {
        given:
        def req = HttpRequest.GET('/parameter/exploded?title=The%20Stand&age=20&age=30')
        def exchange = rxClient.exchange(req, String)
        def response = exchange.onErrorReturn({ t -> t.response }).blockingFirst()

        expect:
        response.status() == HttpStatus.BAD_REQUEST
        response.body().contains('Unexpected token (VALUE_STRING), expected END_ARRAY')
    }

    @Controller(value = "/parameter", produces = MediaType.TEXT_PLAIN)
    static class ParameterController {
        @Get
        String index(Integer max) {
            "Parameter Value: $max"
        }

        @Post("/save")
        String save(Integer max) {
            "Parameter Value: $max"
        }

        @Post("/save-again")
        String saveAgain(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @Get('/overlap/{max}')
        String overlap(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @Get("/simple")
        String simple(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @Get('/path/{max}')
        String path(@QueryValue("max") Integer maximum) {
            "Parameter Value: $maximum"
        }

        @Get('/path/{id}/foo/{fooId}')
        String path2(@QueryValue("id") Long someId, Long fooId) {
            "Parameter Values: $someId $fooId"
        }

        @Get('/path/{id}/bar{/barId}')
        String optionalPath(@QueryValue("id") Long someId, @Nullable Long barId) {
            "Parameter Values: $someId ${barId ?: ''}"
        }

        @Get("/named")
        String named(@QueryValue('maximum') Integer max) {
            "Parameter Value: $max"
        }

        @Get("/optional")
        String optional(@QueryValue Optional<Integer> max) {
            "Parameter Value: ${max.orElse(10)}"
        }

        @Get("/all")
        String all(HttpParameters parameters) {
            "Parameter Value: ${parameters.get('max', Integer, 10)}"
        }

        @Get("/map")
        String map(Map<String, Integer> values) {
            "Parameter Value: ${values.max} ${values.offset}"
        }

        @Get("/list")
        String list(List<Integer> values) {
            assert values.every() { it instanceof Integer }
            "Parameter Value: ${values.inspect()}"
        }

        @Get("/set")
        String set(Set<Integer> values) {
            assert values.every() { it instanceof Integer }
            "Parameter Value: ${values.toList().sort().inspect()}"
        }

        @Get("/optional-list")
        String optionalList(Optional<List<Integer>> values) {
            if (values.isPresent()) {
                assert values.get().every() { it instanceof Integer }
                "Parameter Value: ${values.get()}"
            } else {
                "Parameter Value: none"
            }
        }

        @Get("/optional-date")
        String optionalDate(@Format("yyyy-MM-dd") Optional<Date> date) {
            if (date.isPresent()) {
                Calendar c = new GregorianCalendar()
                c.setTime(date.get())
                "Parameter Value: ${c.get(Calendar.YEAR)}"
            } else {
                "Parameter Value: empty"
            }
        }

        @Get('/query')
        String query(String name) {
            "Parameter Value: $name"
        }

        @Get("/exploded{?book*}")
        String exploded(Book book) {
            "Parameter Value: $book.title $book.age"
        }

        @Get('/queryName/{name}')
        String queryName(String name) {
            "Parameter Value: $name"
        }

        @Post('/query')
        String queryPost(@QueryValue String name) {
            "Parameter Value: $name"
        }

        @Get('/arrayStyle{?param[]*}')
        String arrayStyle(@QueryValue("param[]") List<String> params) {
            "Parameter Value: $params"
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
