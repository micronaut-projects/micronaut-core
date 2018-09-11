package io.micronaut.inject.generics

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

import java.util.function.Function

class GenericTypeArgumentsSpec extends AbstractTypeElementSpec {


    void "test type arguments for interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class Test implements java.util.function.Function<String, Integer>{

    public Integer apply(String str) {
        return 10;
    }
}

class Foo {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for inherited interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class Test implements Foo {

    public Integer apply(String str) {
        return 10;
    }
}

interface Foo extends java.util.function.Function<String, Integer> {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }



    void "test type arguments for superclass that implements interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class Test extends Foo {

    public Integer apply(String str) {
        return 10;
    }
}

abstract class Foo implements java.util.function.Function<String, Integer> {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for superclass"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class Test extends Foo<String, Integer> {

    public Integer apply(String str) {
        return 10;
    }
}

abstract class Foo<T, R> {

    abstract R apply(T t);
}
''')
        expect:
        definition != null
        definition.getTypeArguments('test.Foo').size() == 2
        definition.getTypeArguments('test.Foo')[0].name == 'T'
        definition.getTypeArguments('test.Foo')[1].name == 'R'
        definition.getTypeArguments('test.Foo')[0].type == String
        definition.getTypeArguments('test.Foo')[1].type == Integer
    }

    void "test type arguments for factory"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$MyFunc','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @Bean
    java.util.function.Function<String, Integer> myFunc() {
        return (str) -> 10;
    }
}

''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for factory with inheritance"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$MyFunc','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @Bean
    Foo myFunc() {
        return (str) -> 10;
    }
}

interface Foo extends java.util.function.Function<String, Integer> {}

''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }
}
