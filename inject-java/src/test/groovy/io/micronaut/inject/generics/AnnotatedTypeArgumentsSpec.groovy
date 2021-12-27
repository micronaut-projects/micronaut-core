package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class AnnotatedTypeArgumentsSpec extends AbstractTypeElementSpec {

    // Fails with Java 8, passes with Java 11.
    void "test that annotated type arguments are available for inspection"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Demo', '''\
package test;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import org.jetbrains.annotations.Nullable;

@Controller
public class Demo {
    @Post
    public boolean check(Arg<@Nullable String> arg) {
        return true;
    }
}

interface Arg<T> { }
''')

        expect:
        definition

        when:
        def method = definition.findPossibleMethods('check').findFirst()

        then:
        method.isPresent()

        when:
        def typeVariable = method.get().arguments[0].firstTypeVariable

        then:
        typeVariable.isPresent()
        typeVariable.get().isNullable()
    }
}
