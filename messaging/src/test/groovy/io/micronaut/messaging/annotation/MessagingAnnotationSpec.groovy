package io.micronaut.messaging.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.inject.ExecutableMethod

class MessagingAnnotationSpec extends AbstractTypeElementSpec {

    void "test deprecated annotations"() {
        given:
        def definition = buildBeanDefinition('test.TestListener','''
package test;

import io.micronaut.messaging.annotation.*;

@MessageListener
@MessageHeader(name="Foo", value="Bar")
@MessageHeader(name="Baz", value="Stuff")
class TestListener {
    @MessageMapping("test")
    void receive(@MessageBody String body, @MessageHeader("CONTENT_TYPE") String contentType) {
    
    }   
}
''')
        def method = definition.getRequiredMethod("receive", String, String)

        expect:"for backwards compatibility we transform the new annotation to the old"
        def typeHeaders = definition.getAnnotationValuesByType(Header)
        typeHeaders.size() == 2
        typeHeaders[0].annotationName == Header.name
        typeHeaders[0].stringValue().get() == "Bar"
        typeHeaders[0].stringValue("name").get() == "Foo"
        !method.arguments[0].annotationMetadata.hasAnnotation(MessageBody)
        method.arguments[0].annotationMetadata.hasAnnotation(Body)
        method.arguments[0].annotationMetadata.getAnnotationNameByStereotype(Bindable).get() == Body.name
        !method.arguments[1].annotationMetadata.hasAnnotation(MessageHeader)
        method.arguments[1].annotationMetadata.getAnnotationTypeByStereotype(Bindable).get() == Header
        def headers = method.arguments[1].annotationMetadata.getAnnotationValuesByType(Header)
        headers.size() == 1
        headers[0].annotationName == Header.name
        headers[0].stringValue().get() == "CONTENT_TYPE"
    }
}
