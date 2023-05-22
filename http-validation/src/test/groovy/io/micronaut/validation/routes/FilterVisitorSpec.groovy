package io.micronaut.validation.routes

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class FilterVisitorSpec extends AbstractTypeElementSpec {
    def 'unknown parameter type'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;

@ServerFilter
class Foo {
    @RequestFilter
    void test(String foo) {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Unsupported type for filter method: java.lang.String")
    }

    def 'unknown return type'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;

@ServerFilter
class Foo {
    @RequestFilter
    String test() {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Unsupported type for filter method: java.lang.String")
    }

    def 'response on request filter'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;

@ServerFilter
class Foo {
    @RequestFilter
    void test(HttpResponse<?> response) {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Filter is called before the response is known, can't have a response argument")
    }

    def 'publisher request on response filter'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import org.reactivestreams.Publisher;

@ServerFilter
class Foo {
    @ResponseFilter
    Publisher<HttpRequest<?>> test() {
        return null;
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Unsupported filter return type io.micronaut.http.HttpRequest")
    }
}
