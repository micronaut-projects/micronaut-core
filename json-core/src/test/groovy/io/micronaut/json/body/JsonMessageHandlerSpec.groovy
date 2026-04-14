package io.micronaut.json.body

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Produces
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

class JsonMessageHandlerSpec extends Specification {

    void "JsonMessageHandler annotation #classSimpleName value matches JsonMediaTypeCode constant"(Class annotationClass, String classSimpleName) {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()
        when:
        BeanDefinition<JsonMessageHandler> nettyJsonHandlerBeanDefinition = applicationContext.getBeanDefinition(JsonMessageHandler.class)
        AnnotationValue annotation = nettyJsonHandlerBeanDefinition.getAnnotation(annotationClass)

        then:
        assertAnnotationValueMatchesExpected(annotation, annotationClass)

        cleanup:
        applicationContext.close()

        where:
        annotationClass << [Produces.class, Consumes.class]
        classSimpleName = annotationClass.simpleName
    }

    private void assertAnnotationValueMatchesExpected(AnnotationValue annotation, Class annotationClass) {
        String[] expectedValues
        if (annotationClass == Produces.class) {
            expectedValues = JsonMessageHandler.ProducesJson.class.getAnnotation(Produces).value()
        } else if (annotationClass == Consumes.class) {
            expectedValues = JsonMessageHandler.ConsumesJson.class.getAnnotation(Consumes).value()
        } else {
            throw new IllegalArgumentException("Unsupported annotation " + annotationClass)
        }
        assert Arrays.asList(annotation.stringValues()).sort() == Arrays.asList(expectedValues).sort()
    }
}
