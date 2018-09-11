package io.micronaut.inject.aliasfor

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

import javax.inject.Named
import javax.inject.Qualifier

class AliasForQualifierSpec extends AbstractBeanDefinitionSpec {


    void "test that when an alias is created for a named qualifier the stereotypes are correct"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.AliasForQualifierTest$MyFunc','''\
package test;

import io.micronaut.inject.aliasfor.*;
import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class AliasForQualifierTest {

    @TestAnnotation("foo")
    java.util.function.Function<String, Integer> myFunc() {
        return { String str -> 10 };
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
