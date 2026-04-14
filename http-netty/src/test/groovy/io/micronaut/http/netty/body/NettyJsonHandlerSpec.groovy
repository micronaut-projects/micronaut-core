package io.micronaut.http.netty.body

import io.micronaut.context.BeanContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Produces
import io.micronaut.inject.BeanDefinition
import io.micronaut.json.body.JsonMessageHandler
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class NettyJsonHandlerSpec extends Specification {

    @Inject
    BeanContext beanContext

    void "NettyJsonHandler @Produces value matches JsonMediaTypeCode constant"() {
        when:
        BeanDefinition<NettyJsonHandler> nettyJsonHandlerBeanDefinition = beanContext.getBeanDefinition(NettyJsonHandler.class)
        AnnotationValue<Produces> annotation = nettyJsonHandlerBeanDefinition.getAnnotation(Produces.class)

        then:
        assertAnnotationValueMatchesJsonHandler(annotation, Produces)
    }

    void "NettyJsonHandler @Consumes value matches JsonMediaTypeCode constant"() {
        when:
        BeanDefinition<NettyJsonHandler> nettyJsonHandlerBeanDefinition = beanContext.getBeanDefinition(NettyJsonHandler.class)
        AnnotationValue<Consumes> annotation = nettyJsonHandlerBeanDefinition.getAnnotation(Consumes.class)

        then:
        assertAnnotationValueMatchesJsonHandler(annotation, Consumes)
    }

    private void assertAnnotationValueMatchesJsonHandler(AnnotationValue annotation, Class annotationClass) {
        String[] expectedValues
        if (annotationClass == Produces) {
            expectedValues = JsonMessageHandler.ProducesJson.class.getAnnotation(Produces).value()
        } else if (annotationClass == Consumes) {
            expectedValues = JsonMessageHandler.ConsumesJson.class.getAnnotation(Consumes).value()
        } else {
            throw new IllegalArgumentException("Unsupported annotation " + annotationClass)
        }
        assert Arrays.asList(annotation.stringValues()).sort() == Arrays.asList(expectedValues).sort()
    }
}
