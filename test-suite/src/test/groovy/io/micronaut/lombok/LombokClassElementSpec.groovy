package io.micronaut.lombok

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext

import javax.annotation.processing.Processor

class LombokClassElementSpec extends AbstractTypeElementSpec {

    void 'test lombok compile'() {
        given:
        ApplicationContext context = buildContext('test.FooBean', '''
package test;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants
@Singleton
@Requires(property = "spec.name", value = FooBean.Fields.lombokFieldsTest)
class FooBean {

    private String lombokFieldsTest;

}

''', true, ["spec.name": "lombokFieldsTest"])
        expect:
        context.containsBean(context.classLoader.loadClass('test.FooBean'))
    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {

            @Override
            protected List<Processor> getAnnotationProcessors() {
                def processors = super.getAnnotationProcessors()
                processors.add(0, getClass().getClassLoader().loadClass('lombok.launch.AnnotationProcessorHider$ClaimingProcessor').newInstance())
                processors.add(0, getClass().getClassLoader().loadClass('lombok.launch.AnnotationProcessorHider$AnnotationProcessor').newInstance())
                return processors
            }
        }
    }
}
