package io.micronaut.inject.annotation.repeatable

import groovy.transform.NotYetImplemented
import io.micronaut.context.annotation.Requirements
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.annotation.AnnotationValue
import io.micronaut.inject.annotation.TestCachePuts


class RepeatableAnnotationSpec extends AbstractTypeElementSpec {

    @NotYetImplemented
    void "test repeatable annotation resolve all values with single @Requires"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@SomeRequires
@Requires(property="bar")
@javax.inject.Singleton
class Test {

}
''')
        when:
        AnnotationValue[] requirements = definition.getValue(Requirements, AnnotationValue[].class).orElse(null)

        then:
        requirements != null
        requirements.size() == 2
    }


    @NotYetImplemented
    void "test repeatable annotation resolve all values with multiple @Requires"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@SomeRequires
@Requires(property="bar")
@Requires(property="another")
@javax.inject.Singleton
class Test {

}
''')
        when:
        AnnotationValue[] requirements = definition.getValue(Requirements, AnnotationValue[].class).orElse(null)

        then:
        requirements != null
        requirements.size() == 3
    }
}
