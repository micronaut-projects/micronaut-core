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
        ex.message.contains("Unsupported filter method parameter type: java.lang.String")
    }

    def 'continuation parameter type'() {
        expect:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.filter.FilterContinuation;

@ServerFilter
class Foo {
    @RequestFilter
    public void requestFilterContinuationBlocking(HttpRequest<?> request, FilterContinuation<HttpResponse<?>> continuation) {
    }
}

""")

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
        ex.message.contains("Unsupported filter return type: java.lang.String")
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
        ex.message.contains("Unsupported filter return type: io.micronaut.http.HttpRequest")
    }

    def 'server request filter reads the body and the form'() {
        expect:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.form.*;
import java.util.List;
import java.util.Optional;

@ServerFilter
class Foo {
    @RequestFilter("/data")
    void data(HttpRequest<?> request, FormData form, @Part("name") String name, @Part("avatar") FileUpload avatar,
              List<FileUpload> docs, Optional<FileUpload> cover) {
    }

    @RequestFilter("/parts")
    void parts(FormParts parts) {
    }

    @RequestFilter("/part")
    void part(FormPart part) {
    }

    @RequestFilter("/body")
    void body(AsyncRequestBody body) {
    }
}

""")
    }

    def 'response filter cannot read the form'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.form.FormData;

@ServerFilter
class Foo {
    @ResponseFilter
    void test(HttpResponse<?> response, FormData form) {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("can only be bound in a request filter method of a @ServerFilter")
    }

    def 'client filter cannot bind a part'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;

@ClientFilter
class Foo {
    @RequestFilter
    void test(HttpRequest<?> request, @Part("name") String name) {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@Part can only be bound in a request filter method of a @ServerFilter")
    }

    def 'server filter reads the path variables after routing'() {
        expect:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;

@ServerFilter
class Foo {
    @RequestFilter("/items/{id}")
    void request(HttpRequest<?> request, PathVariables pathVariables) {
    }

    @ResponseFilter("/items/{id}")
    void response(HttpResponse<?> response, PathVariables pathVariables) {
    }
}

""")
    }

    def 'pre-matching filter cannot bind the path variables'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.annotation.PreMatching;

@ServerFilter
class Foo {
    @PreMatching
    @RequestFilter
    void test(HttpRequest<?> request, PathVariables pathVariables) {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("A @PreMatching filter method runs before the request is routed and cannot bind the path variables of the route (io.micronaut.http.PathVariables)")
    }

    def 'client filter cannot bind the path variables'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;

@ClientFilter
class Foo {
    @RequestFilter
    void test(HttpRequest<?> request, PathVariables pathVariables) {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("can only be bound in a filter method of a @ServerFilter")
    }
}
