package io.micronaut.http.client.format

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.convert.format.Format
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = 'spec.name', value = 'ClientFormatSpec')
@MicronautTest
class ClientFormatSpec extends Specification {
    @Inject FormatClient client

    void "test PIPES formatted list"() {
        given:
        var values = ["tree", "spruce", "seed"]
        expect:
        client.pipesFormattedList(values) == "param=tree|spruce|seed"
    }

    void "test PIPES formatted Map"() {
        given:
        var values = ["name": "Mark", "age": 30]
        expect:
        client.pipesFormattedMap(values) == "param=name|Mark|age|30"
    }

    void "test PIPES formatted Object"() {
        given:
        var cafe = new Cafe(name: "Pizza Garden", "address": "Home Street 2")
        expect:
        client.pipesFormattedObject(cafe) == "param=name|Pizza+Garden|address|Home+Street+2"
    }

    void "test csv format all values"() {
        expect:
        client.csvFormattedObject(param) == query

        where:
        param                                  | query
        ["a", "b", "c"]                        | "csv=a,b,c"
        ["name": "Bob", "age": 12]             | "csv=name,Bob,age,12"
        new Cafe(name: "AAA", address: "here") | "csv=name,AAA,address,here"
    }

    void "test ssv format all values"() {
        expect:
        client.ssvFormattedObject(param) == query

        where:
        param                                    | query
        ["a", "b", "c", "d", "e,f,g"]            | "ssv=a+b+c+d+e,f,g"
        ["name": "Bob", "age": 8, "height": 120] | "ssv=name+Bob+age+8+height+120"
        new Cafe(name: "AAA", address: "here")   | "ssv=name+AAA+address+here"
    }

    void "test pipes format all values"() {
        expect:
        client.pipesFormattedObject(param) == query

        where:
        param                                           | query
        ["a", "b", "c", "d", "e,f,g"]                   | "param=a|b|c|d|e,f,g"
        ["name": "Bob", "age": 8, "height": 120]        | "param=name|Bob|age|8|height|120"
        new Cafe(name: "AAA", address: "here")          | "param=name|AAA|address|here"
    }

    void "test multi format all values"() {
        expect:
        client.multiFormattedObject(param) == query

        where:
        param                                           | query
        ["a", "b", "c,d"]                               | "multi=a&multi=b&multi=c,d"
        ["name": "Bob", "age": 8, "height": 120]        | "name=Bob&age=8&height=120"
        new Cafe(name: "AAA", address: "here")          | "name=AAA&address=here"
    }

    void "test deep-object format all values"() {
        expect:
        client.deepObjectFormattedValue(param) == query

        where:
        param                                           | query
        ["a", "b", "c,d"]                               | "param[0]=a&param[1]=b&param[2]=c,d"
        ["name": "Bob", "age": 8, "height": 120]        | "param[name]=Bob&param[age]=8&param[height]=120"
        new Cafe(name: "AAA", address: "here")          | "param[name]=AAA&param[address]=here"
    }

    void "test cannot annotate primitive type with format"() {
        when:
        var string = "hello"
        client.csvFormattedObject(string)

        then:
        thrown IntrospectionException
    }

    void "test cannot annotate non-introspected type with format"() {
        when:
        var notIntrospected = new NotIntrospected()
        client.csvFormattedObject(notIntrospected)

        then:
        thrown IntrospectionException
    }

    @Requires(property = 'spec.name', value = 'ClientFormatSpec')
    @Client("/format")
    static interface FormatClient {
        @Get("/queryIdentity")
        String pipesFormattedList(@QueryValue @Format("PIPES") List<String> param)

        @Get("/queryIdentity")
        String pipesFormattedMap(@QueryValue @Format("PIPES") Map<String, Object> param)

        @Get("/queryIdentity")
        String pipesFormattedObject(@QueryValue @Format("PIPES") Object param)

        @Get("/queryIdentity")
        String csvFormattedObject(@QueryValue @Format("CSV") Object csv)

        @Get("/queryIdentity")
        String ssvFormattedObject(@QueryValue("ssv") @Format("SSV") Object param)

        @Get("/queryIdentity")
        String multiFormattedObject(@QueryValue @Format("MULTI") Object multi)

        @Get("/queryIdentity")
        String deepObjectFormattedValue(@QueryValue @Format("DEEP_OBJECT") Object param)
    }

    @Requires(property = 'spec.name', value = 'ClientFormatSpec')
    @Controller("/format")
    static class FormatController {
        @Get("/queryIdentity")
        String queryIdentity(HttpRequest<?> request) {
            return request.getUri().getQuery()
        }
    }

    @Introspected
    static class Cafe {
        private String name;
        private String address;
        private String privateProperty;

        String getName() {
            return name
        }

        void setName(String name) {
            this.name = name
        }

        String getAddress() {
            return address
        }

        void setAddress(String address) {
            this.address = address
        }
    }

    static class NotIntrospected {
        public String property;
    }
}
