package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.format.Format
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Unroll

public class QueryParameterFormattingSpec extends AbstractMicronautSpec {
    private static String PIPE = "%7C";

    @Unroll
    void "test bind formatted query parameters for URI #uri"() {
        given:
        var encodedUri = uri.replace("|", PIPE).replace(" ", "+")
        HttpRequest<?> req = HttpRequest.create(HttpMethod.GET, encodedUri)
        Publisher<HttpResponse<?>> exchange = httpClient.exchange(req, String)
        HttpResponse<?> response = Flux.from(exchange).blockFirst()
        def body = response.body()

        expect:
        body == result

        where:
        uri                                                           | result
        // List
        '/formatted/csv?param=a,b,c'                                  | ["a", "b", "c"].inspect()
        '/formatted/csv?param=a'                                      | ["a"].inspect()
        '/formatted/ssv?v=a b d'                                      | ["a", "b", "d"].inspect()
        '/formatted/ssv?v=a'                                          | ["a"].inspect()
        '/formatted/pipes?param=what|is|life'                         | ["what", "is", "life"].inspect()
        '/formatted/pipes?param=what'                                 | ["what"].inspect()
        '/formatted/multi?value=one&value=two&value=3'                | ["one", "two", "3"].inspect()
        '/formatted/deep?param[0]=a&param[1]=b'                       | ["a", "b"].inspect()
        '/formatted/csv'                                              | [].inspect()
        '/formatted/csv?param='                                       | [].inspect()
        '/formatted/pipes?param=|two'                                 | ['', 'two'].inspect()
        // Map
        '/formatted/m/csv?p=key,value,a,b,c,d'                        | ["key": "value", "a": "b", "c": "d"].toSorted().inspect()
        '/formatted/m/ssv?param=start 0 end 1'                        | ["start": 0, "end": 1].toSorted().inspect()
        '/formatted/m/pipes?param=a|b|c|d|e'                          | ["a": "b", "c": "d"].toSorted().inspect()
        '/formatted/m/multi?k=1&val=2&c=3'                            | ["k": "1", "val": "2", "c": "3"].toSorted().inspect()
        '/formatted/m/deep?v[start]=0&v[end]=2&v[middle]=3'           | ["start": 0, "end": 2, "middle": 3].toSorted().inspect()
        '/formatted/m/ssv'                                            | [:].inspect()
        '/formatted/m/ssv?param='                                     | [:].inspect()
        // Object
        '/formatted/o/csv?p=name,Doggo,age,12'                        | "name: Doggo, age: 12, weight: null"
        '/formatted/o/ssv?param=name Fred weight 2'                   | "name: Fred, age: null, weight: 2.0"
        '/formatted/o/pipes?param=name|Fred|age|2|weight|3|unknown|1' | "name: Fred, age: 2, weight: 3.0"
        '/formatted/o/multi?name=Doggo&age=12'                        | "name: Doggo, age: 12, weight: null"
        '/formatted/o/deep?v[name]=Doggo&v[age]=1&v[weight]=0.5'      | "name: Doggo, age: 1, weight: 0.5"
    }

    void "test bind formatted query parameters object initialization error"() {
        when:
        HttpRequest<?> req = HttpRequest.create(HttpMethod.GET, '/formatted/o/csv')
        Publisher<HttpResponse<?>> exchange = httpClient.exchange(req, String)
        Flux.from(exchange).blockFirst()
        then:
        var e = thrown(HttpClientResponseException)
    }

    @Requires(property = 'spec.name', value = 'QueryParameterFormattingSpec')
    @Controller(value = "/formatted", produces = MediaType.TEXT_PLAIN)
    static class FormattedController {
        @Get("csv")
        String csvList(@QueryValue @Format("CSV") List<String> param) {
            return param.inspect()
        }

        @Get("ssv")
        String ssvList(@QueryValue("v") @Format("SSV") ArrayList<String> param) {
            return param.inspect()
        }

        @Get("pipes")
        String pipesList(@QueryValue @Format("PIPES") Iterable<String> param) {
            return param.inspect()
        }

        @Get("multi")
        String multiList(@QueryValue("value") @Format("MULTI") Iterable<String> param) {
            return param.inspect()
        }

        @Get("deep")
        String deepList(@QueryValue @Format("DEEP_OBJECT") List<String> param) {
            return param.inspect();
        }

        @Get("m/csv")
        String csvMap(@QueryValue("p") @Format("CSV") Map<String, String> param) {
            return param.toSorted().inspect()
        }

        @Get("m/ssv")
        String ssvMap(@QueryValue @Format("SSV") HashMap<String, Integer> param) {
            return param.toSorted().inspect()
        }

        @Get("m/pipes")
        String pipesMap(@QueryValue @Format("PIPES") Map<CharSequence, CharSequence> param) {
            return param.toSorted().inspect()
        }

        @Get("m/multi")
        String multiMap(@QueryValue("v") @Format("MULTI") Map<String, String> param) {
            return param.toSorted().inspect()
        }

        @Get("m/deep")
        String deepMap(@QueryValue("v") @Format("DEEP_OBJECT") Map<String, Integer> param) {
            return param.toSorted().inspect()
        }

        @Get("o/csv")
        String csvObject(@QueryValue("p") @Format("CSV") Dog param) {
            return param.inspect()
        }

        @Get("o/ssv")
        String ssvObject(@QueryValue @Format("SSV") Dog param) {
            return param.inspect()
        }

        @Get("o/pipes")
        String pipesObject(@QueryValue @Format("PIPES") Dog param) {
            return param.inspect()
        }

        @Get("o/multi")
        String multiObject(@QueryValue("v") @Format("MULTI") Dog param) {
            return param.inspect()
        }

        @Get("o/deep")
        String deepObject(@QueryValue("v") @Format("DEEP_OBJECT") Dog param) {
            return param.inspect()
        }
    }

    @Introspected
    static class Dog {
        private final String name
        private Integer age
        private Float weight

        Dog(String name) {
            this.name = name
        }

        Integer getAge() {
            return age
        }

        void setAge(Integer age) {
            this.age = age
        }

        Float getWeight() {
            return weight
        }

        void setWeight(Float weight) {
            this.weight = weight
        }

        String getName() {
            return name
        }

        @Override
        String toString() {
            return "name: $name, age: $age, weight: $weight"
        }
    }
}
