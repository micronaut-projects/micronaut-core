package io.micronaut.http.netty.body

import io.micronaut.context.BeanContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Produces
import io.micronaut.inject.BeanDefinition
import io.micronaut.json.codec.JsonMediaTypeCodec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class NettyJsonHandlerSpec extends Specification {

    @Inject
    BeanContext beanContext

    @Inject
    JsonMediaTypeCodec jsonMediaTypeCodec

    void "NettyJsonHandler @Produces value matches JsonMediaTypeCode constant"() {
        when:
        BeanDefinition<NettyJsonHandler> nettyJsonHandlerBeanDefinition = beanContext.getBeanDefinition(NettyJsonHandler.class)
        AnnotationValue<Produces> annotation = nettyJsonHandlerBeanDefinition.getAnnotation(Produces.class)

        then:
        assertAnnotationValueMatchesJsonMediaTypeCodec(annotation)
    }

    void "NettyJsonHandler @Consumes value matches JsonMediaTypeCode constant"() {
        when:
        BeanDefinition<NettyJsonHandler> nettyJsonHandlerBeanDefinition = beanContext.getBeanDefinition(NettyJsonHandler.class)
        AnnotationValue<Consumes> annotation = nettyJsonHandlerBeanDefinition.getAnnotation(Consumes.class)

        then:
        assertAnnotationValueMatchesJsonMediaTypeCodec(annotation)
    }

    private void assertAnnotationValueMatchesJsonMediaTypeCodec(AnnotationValue annotation) {
        assert Arrays.asList(annotation.stringValues()).sort() == jsonMediaTypeCodec.getMediaTypes().stream().map(it -> it.toString()).sorted().toList()
    }
}
