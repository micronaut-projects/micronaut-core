package io.micronaut.inject.beans

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanMethod

class IntrospectionIndexLookupSpec extends AbstractBeanDefinitionSpec {

    void 'properties, constructor arguments and method arguments are found by name'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('lookup.Person', '''
package lookup

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

@Introspected
class Person {
    final String name
    final int age
    String city

    Person(String name, int age) {
        this.name = name
        this.age = age
    }

    @Executable
    String greet(String greeting, String suffix) {
        greeting + name + suffix
    }
}
''')
        List<String> propertyNames = introspection.beanProperties*.name
        List<String> argumentNames = introspection.constructorArguments*.name
        BeanMethod<?, ?> greet = introspection.beanMethods.find { it.name == 'greet' }

        expect:
        argumentNames == ['name', 'age']
        propertyNames.containsAll(['name', 'age', 'city'])
        propertyNames.every { introspection.propertyIndexOf(it) == propertyNames.indexOf(it) }
        argumentNames.every { introspection.constructorArgumentIndexOf(it) == argumentNames.indexOf(it) }
        introspection.getConstructorArgument('age').get().is(introspection.constructorArguments[1])
        greet.argumentIndexOf('greeting') == 0
        greet.argumentIndexOf('suffix') == 1
        greet.getArgument('suffix').get().is(greet.arguments[1])
        introspection.propertyIndexOf('missing') == -1
        greet.argumentIndexOf('missing') == -1
    }
}
