package io.micronaut.kotlin.processing.beans

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import kotlin.coroutines.Continuation

class IntrospectionIndexLookupSpec extends AbstractKotlinCompilerSpec {

    void 'data class properties and constructor arguments are found by name'() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('lookup.Person', '''
package lookup

import io.micronaut.core.annotation.Introspected

@Introspected
data class Person(val name: String, val age: Int, val city: String?)
''')
        List<String> propertyNames = introspection.beanProperties*.name
        List<String> argumentNames = introspection.constructorArguments*.name

        expect:
        argumentNames == ['name', 'age', 'city']
        propertyNames.every { introspection.propertyIndexOf(it) == propertyNames.indexOf(it) }
        argumentNames.every { introspection.constructorArgumentIndexOf(it) == argumentNames.indexOf(it) }
        introspection.getConstructorArgument('age').get().is(introspection.constructorArguments[1])
        introspection.propertyIndexOf('missing') == -1
        !introspection.getConstructorArgument('missing').isPresent()
    }

    void 'a suspend method can look up its arguments and its continuation by name'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('lookup.Repository', '''
package lookup

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
open class Repository {
    @Executable
    open suspend fun find(id: Long, name: String): String {
        TODO()
    }
}
''')
        ExecutableMethod<?, ?> find = definition.executableMethods.find { it.name == 'find' }

        expect: 'the compiler appends the continuation as a third argument'
        find.arguments*.name == ['id', 'name', 'continuation']
        find.arguments[2].type == Continuation
        find.argumentIndexOf('id') == 0
        find.argumentIndexOf('name') == 1
        find.argumentIndexOf('continuation') == 2
        find.getArgument('name').get().is(find.arguments[1])
        find.argumentIndexOf('missing') == -1
    }

    void 'a suspend method with a parameter named continuation'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('lookup.Clash', '''
package lookup

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
open class Clash {
    @Executable
    open suspend fun find(continuation: String): String {
        TODO()
    }
}
''')
        ExecutableMethod<?, ?> find = definition.executableMethods.find { it.name == 'find' }

        expect: 'the user parameter and the synthetic continuation share a name'
        find.arguments*.name == ['continuation', 'continuation']

        when:
        int index = find.argumentIndexOf('continuation')

        then:
        index == 0
    }
}
