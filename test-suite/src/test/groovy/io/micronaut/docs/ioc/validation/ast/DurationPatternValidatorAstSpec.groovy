package io.micronaut.docs.ioc.validation.ast

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class DurationPatternValidatorAstSpec extends AbstractTypeElementSpec {


    void "test validate annotation values on field"() {
        when:
        buildBeanIntrospection('test.Test', '''
package test;

@io.micronaut.core.annotation.Introspected
class Test {
    @io.micronaut.docs.ioc.validation.custom.TimeOff(duration="junk") // blank not allowed
    private String name;
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message == '''test/Test.java:7: error: @TimeOff.duration: invalid duration (junk)
    private String name;
                   ^'''

    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @NonNull
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor()]
        }
    }
}
