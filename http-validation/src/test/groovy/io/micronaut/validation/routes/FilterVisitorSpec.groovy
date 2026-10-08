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

    def 'filter body of a type the binder fills: #type'() {
        expect:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@ServerFilter
class Foo {
    @RequestFilter
    void test(@Body $type body) {
    }
}

""")

        where:
        type << ['byte[]', 'String', 'CharSequence', 'Object', 'io.micronaut.core.io.buffer.ByteBuffer<?>']
    }

    def 'filter body of a type the binder cannot fill: #type'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@ServerFilter
class Foo {
    @RequestFilter
    void test(@Body $type body) {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("The @Body to a filter method can only be a raw type (byte[], String, ByteBuffer etc.)")

        where:
        type << ['io.micronaut.core.io.buffer.ByteArrayByteBuffer', 'java.util.Map<String, Object>', 'int[]']
    }

    def 'filter body of a type variable'() {
        expect:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@ServerFilter
class Foo<B extends CharSequence> {
    @RequestFilter
    void test(@Body B body) {
    }
}

""")
    }

    def 'execution flow return and continuation types'() {
        expect:
        buildTypeElement("""

package test;

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.filter.FilterContinuation;

@ServerFilter
class Foo {
    @RequestFilter
    public ExecutionFlow<HttpResponse<?>> continuation(HttpRequest<?> request, FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation) {
        return continuation.proceed();
    }

    @RequestFilter
    public ExecutionFlow<HttpRequest<?>> request(HttpRequest<?> request) {
        return ExecutionFlow.just(request);
    }

    @ResponseFilter
    public ExecutionFlow<MutableHttpResponse<?>> response(MutableHttpResponse<?> response) {
        return ExecutionFlow.just(response);
    }
}

""")
    }

    def 'execution flow request on response filter'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;

@ServerFilter
class Foo {
    @ResponseFilter
    ExecutionFlow<HttpRequest<?>> test() {
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

    def 'server request filter cannot bind a part of a type that is read with the route: #type'() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.*;
import org.reactivestreams.Publisher;
import java.util.List;
import java.util.Optional;

@ServerFilter
class Foo {
    @RequestFilter
    void test(HttpRequest<?> request, @Part("file") $type file) {
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("cannot be bound in a filter method")
        ex.message.contains("use io.micronaut.http.form.FileUpload, io.micronaut.http.form.FormPart or io.micronaut.http.form.FormData")

        where:
        type << [
            'CompletedFileUpload',
            'StreamingFileUpload',
            'CompletedPart',
            'CompletedAttribute',
            'PartData',
            'Publisher<CompletedFileUpload>',
            'Publisher<StreamingFileUpload>',
            'Publisher<PartData>',
            'Publisher<CompletedPart>',
            'Publisher<Publisher<byte[]>>',
            'Publisher<String>',
            'List<CompletedFileUpload>',
            'Optional<CompletedFileUpload>'
        ]
    }

    def 'server request filter binds a part of a text field or of a form type: #type'() {
        expect:
        buildTypeElement("""

package test;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.form.*;
import java.util.List;
import java.util.Optional;

@ServerFilter
class Foo {
    @RequestFilter
    void test(HttpRequest<?> request, @Part("field") $type field) {
    }
}

""")

        where:
        type << ['String', 'int', 'Integer', 'List<String>', 'Optional<String>', 'FileUpload', 'List<FileUpload>', 'Optional<FileUpload>', 'FormPart', 'Optional<FormPart>']
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
