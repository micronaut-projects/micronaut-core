package io.micronaut.json.body

import com.fasterxml.jackson.annotation.JsonView
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.json.JsonMapper
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The response body of a route without a declared body type, e.g. {@code @JsonView @Get Object},
 * is written with the type of the value and the annotations of the route, and the view of those
 * annotations applies to it.
 */
class JsonMessageHandlerViewSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['jackson.json-view.enabled': true])

    void "write applies the @JsonView of the type to a #description"() {
        given:
        def handler = new JsonMessageHandler<Object>(context.getBean(JsonMapper))
        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(JsonView.name, [value: new AnnotationClassValue<>(Views.Public)])
        Argument<Object> type = (Argument<Object>) Argument.of(Model).withAnnotationMetadata(metadata)
        def bodyFactory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
        def model = new Model(name: 'Bob', password: 'secret')

        when:
        def response = write(handler, bodyFactory, type, model)
        String json = response.byteBody().toInputStream().text

        then:
        json.contains('Bob')
        !json.contains('secret')

        where:
        description         | write
        'response'          | { JsonMessageHandler<Object> h, ByteBodyFactory f, Argument<Object> t, Object m -> h.write(f, HttpRequest.GET('/'), HttpResponse.ok(), t, MediaType.APPLICATION_JSON_TYPE, m) }
        'specific response' | { JsonMessageHandler<Object> h, ByteBodyFactory f, Argument<Object> t, Object m -> h.createSpecific(t).write(f, HttpRequest.GET('/'), HttpResponse.ok(), t, MediaType.APPLICATION_JSON_TYPE, m) }
    }

    static class Views {
        static class Public {}
        static class Internal extends Public {}
    }

    static class Model {
        @JsonView(Views.Public)
        String name
        @JsonView(Views.Internal)
        String password
    }
}
