package io.micronaut.json.body

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Produces
import io.micronaut.inject.BeanDefinition
import io.micronaut.json.codec.JsonMediaTypeCodec
import spock.lang.Specification

class JsonMessageHandlerSpec extends Specification {

    void "JsonMessageHandler annotation #classSimpleName value matches JsonMediaTypeCode constant"(Class annotationClass, String classSimpleName) {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()
        JsonMediaTypeCodec jsonMediaTypeCodec = applicationContext.getBean(JsonMediaTypeCodec)
        when:
        BeanDefinition<JsonMessageHandler> nettyJsonHandlerBeanDefinition = applicationContext.getBeanDefinition(JsonMessageHandler.class)
        AnnotationValue annotation = nettyJsonHandlerBeanDefinition.getAnnotation(annotationClass)

        then:
        assertAnnotationValueMatchesJsonMediaTypeCodec(annotation, jsonMediaTypeCodec)

        cleanup:
        applicationContext.close()

        where:
        annotationClass << [Produces.class, Consumes.class]
        classSimpleName = annotationClass.simpleName
    }

    private void assertAnnotationValueMatchesJsonMediaTypeCodec(AnnotationValue annotation, JsonMediaTypeCodec jsonMediaTypeCodec) {
        assert Arrays.asList(annotation.stringValues()).sort() == jsonMediaTypeCodec.getMediaTypes().stream().map(it -> it.toString()).sorted().toList()
    }
}
