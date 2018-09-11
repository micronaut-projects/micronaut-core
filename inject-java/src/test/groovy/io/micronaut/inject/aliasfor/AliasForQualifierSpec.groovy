package io.micronaut.inject.aliasfor

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

import javax.inject.Named
import javax.inject.Qualifier
import java.util.function.Function

class AliasForQualifierSpec extends AbstractTypeElementSpec {


    void "test that when an alias is created for a named qualifier the stereotypes are correct"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$MyFunc','''\
package test;

import io.micronaut.inject.aliasfor.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @TestAnnotation("foo")
    java.util.function.Function<String, Integer> myFunc() {
        return (str) -> 10;
    }
}

''')
        expect:
        definition != null
        definition.getAnnotationTypeByStereotype(Qualifier).isPresent()
        definition.getAnnotationTypeByStereotype(Qualifier).get() == TestAnnotation
        definition.getValue(Named, String).get() == 'foo'
    }
}
