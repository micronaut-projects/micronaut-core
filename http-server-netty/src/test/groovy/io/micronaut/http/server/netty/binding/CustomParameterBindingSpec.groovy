package io.micronaut.http.server.netty.binding

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.format.Format
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CustomHttpMethod
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.reactivex.Flowable
import org.junit.Ignore
import spock.lang.Unroll

import javax.annotation.Nullable

class CustomParameterBindingSpec extends AbstractMicronautSpec {

    @Unroll
    void "test bind HTTP parameters for URI #httpMethod #uri"() {
        given:
        def req = HttpRequest.create(HttpMethod.CUSTOM, uri, httpMethod)
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
        "LOCK" | '/parameter/save?max=30'                        | null                        | HttpStatus.BAD_REQUEST
        "REPORT"  | '/parameter/path/20/foo/10'                     | "Parameter Values: 20 10"   | HttpStatus.OK
        "REPORT"  | '/parameter/path/20/bar/10'                     | "Parameter Values: 20 10"   | HttpStatus.OK
        "REPORT"  | '/parameter/path/20/bar'                        | "Parameter Values: 20 "     | HttpStatus.OK
        "REPORT"  | '/parameter/named?maximum=20'                   | "Parameter Value: 20"       | HttpStatus.OK
        "LOCK" | '/parameter/save-again?max=30'                  | "Parameter Value: 30"       | HttpStatus.OK
        "REPORT"  | '/parameter/path/20'                            | "Parameter Value: 20"       | HttpStatus.OK
        "REPORT"  | '/parameter/simple'                             | null                        | HttpStatus.BAD_REQUEST
        "REPORT"  | '/parameter/named'                              | null                        | HttpStatus.BAD_REQUEST
        "REPORT"  | '/parameter/overlap/30'                         | "Parameter Value: 30"       | HttpStatus.OK
        "REPORT"  | '/parameter/overlap/30?max=50'                  | "Parameter Value: 30"       | HttpStatus.OK
        "REPORT"  | '/parameter/map?values.max=20&values.offset=30' | "Parameter Value: 20 30"    | HttpStatus.OK
        "REPORT"  | '/parameter/optional?max=20'                    | "Parameter Value: 20"       | HttpStatus.OK

        "REPORT"  | '/parameter/set?values=10,20'                   | "Parameter Value: [10, 20]" | HttpStatus.OK
        "REPORT"  | '/parameter/list?values=10,20'                  | "Parameter Value: [10, 20]" | HttpStatus.OK
        "REPORT"  | '/parameter/list?values=10&values=20'           | "Parameter Value: [10, 20]" | HttpStatus.OK
        "REPORT"  | '/parameter/set?values=10&values=20'            | "Parameter Value: [10, 20]" | HttpStatus.OK
        "REPORT"  | '/parameter/optional-list?values=10&values=20'  | "Parameter Value: [10, 20]" | HttpStatus.OK
        "REPORT"  | '/parameter/optional-date?date=1941-01-05'      | "Parameter Value: 1941"     | HttpStatus.OK
        "REPORT"  | '/parameter?max=20'                             | "Parameter Value: 20"       | HttpStatus.OK
        "REPORT"  | '/parameter/simple?max=20'                      | "Parameter Value: 20"       | HttpStatus.OK

        "REPORT"  | '/parameter/optional'                           | "Parameter Value: 10"       | HttpStatus.OK
        "REPORT"  | '/parameter/all'                                | "Parameter Value: 10"       | HttpStatus.OK
        "REPORT"  | '/parameter/all?max=20'                         | "Parameter Value: 20"       | HttpStatus.OK

        "REPORT"  | '/parameter/exploded?title=The%20Stand'         | "Parameter Value: The Stand" | HttpStatus.OK
        "REPORT"  | '/parameter/queryName/Fr%20ed'                  | "Parameter Value: Fr ed"    | HttpStatus.OK
        "LOCK" | '/parameter/query?name=Fr%20ed'                 | "Parameter Value: Fr ed"    | HttpStatus.OK
    }

    void "test exploded with no default constructor"() {
        when:
        Flowable<HttpResponse<String>> exchange = rxClient.exchange(HttpRequest.create(HttpMethod.CUSTOM, "/parameter/exploded?title=The%20Stand", "REPORT"), String)
        HttpResponse<String> response = exchange.onErrorReturn({ t -> t.response }).blockingFirst()

        then:
        response.status() == HttpStatus.OK
        response.getBody().isPresent()
        response.getBody().get() == "Parameter Value: The Stand"
    }

    @Controller(value = "/parameter", produces = MediaType.TEXT_PLAIN)
    static class ParameterController {
        @CustomHttpMethod(method="REPORT")
        String index(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @CustomHttpMethod(method="LOCK", value="/save")
        String save(Integer max) {
            "Parameter Value: $max"
        }

        @CustomHttpMethod(method="LOCK", value="/save-again")
        String saveAgain(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @CustomHttpMethod(method="REPORT", value='/overlap/{max}')
        String overlap(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @CustomHttpMethod(method="REPORT", value="/simple")
        String simple(@QueryValue Integer max) {
            "Parameter Value: $max"
        }

        @CustomHttpMethod(method="REPORT", value='/path/{max}')
        String path(@QueryValue("max") Integer maximum) {
            "Parameter Value: $maximum"
        }

        @CustomHttpMethod(method="REPORT", value='/path/{id}/foo/{fooId}')
        String path2(@QueryValue("id") Long someId, Long fooId) {
            "Parameter Values: $someId $fooId"
        }

        @CustomHttpMethod(method="REPORT", value='/path/{id}/bar{/barId}')
        String optionalPath(@QueryValue("id") Long someId, @Nullable Long barId) {
            "Parameter Values: $someId ${barId ?: ''}"
        }

        @CustomHttpMethod(method="REPORT", value="/named")
        String named(@QueryValue('maximum') Integer max) {
            "Parameter Value: $max"
        }

        @CustomHttpMethod(method="REPORT", value="/optional")
        String optional(@QueryValue Optional<Integer> max) {
            "Parameter Value: ${max.orElse(10)}"
        }

        @CustomHttpMethod(method="REPORT", value="/all")
        String all(HttpParameters parameters) {
            "Parameter Value: ${parameters.get('max', Integer, 10)}"
        }

        @CustomHttpMethod(method="REPORT", value="/map")
        String map(@QueryValue Map<String, Integer> values) {
            "Parameter Value: ${values.max} ${values.offset}"
        }

        @CustomHttpMethod(method="REPORT", value="/list")
        String list(@QueryValue List<Integer> values) {
            assert values.every() { it instanceof Integer }
            "Parameter Value: ${values.inspect()}"
        }

        @CustomHttpMethod(method="REPORT", value="/set")
        String set(@QueryValue Set<Integer> values) {
            assert values.every() { it instanceof Integer }
            "Parameter Value: ${values.toList().sort().inspect()}"
        }

        @CustomHttpMethod(method="REPORT", value="/optional-list")
        String optionalList(@QueryValue Optional<List<Integer>> values) {
            if (values.isPresent()) {
                assert values.get().every() { it instanceof Integer }
                "Parameter Value: ${values.get()}"
            } else {
                "Parameter Value: none"
            }
        }

        @CustomHttpMethod(method="REPORT", value="/optional-date")
        String optionalDate(@QueryValue @Format("yyyy-MM-dd") Optional<Date> date) {
            if (date.isPresent()) {
                Calendar c = new GregorianCalendar()
                c.setTime(date.get())
                "Parameter Value: ${c.get(Calendar.YEAR)}"
            } else {
                "Parameter Value: empty"
            }
        }

        @CustomHttpMethod(method="REPORT", value='/query')
        String query(String name) {
            "Parameter Value: $name"
        }

        @CustomHttpMethod(method="REPORT", value="/exploded{?book*}")
        String exploded(@QueryValue Book book) {
            "Parameter Value: $book.title"
        }

        @CustomHttpMethod(method="REPORT", value='/queryName/{name}')
        String queryName(String name) {
            "Parameter Value: $name"
        }

        @CustomHttpMethod(method="LOCK", value='/query')
        String queryPost(@QueryValue String name) {
            "Parameter Value: $name"
        }

        @Introspected
        static class Book {

            private String title
            private String author

            Book(String title, @Nullable String author) {
                this.title = title
                this.author = author
            }

            String getTitle() {
                return title
            }
        }
    }
}
