/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.generics

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.DelegatingBeanDefinition

/**
 * What a declaration keeps where the recorded type arguments alone cannot say it: the rawness of a bean type through
 * a definition that wraps another, a producer whose type is a variable, and a bound that is itself a variable or
 * raw.
 */
class DeclaredTypeRootSpec extends AbstractBeanDefinitionSpec {

    void "a definition that delegates to another says the bean type was declared raw, as the target does"() {
        given:
        ApplicationContext context = buildContext('''
package test
@io.micronaut.context.annotation.Factory
class Factory {
    @io.micronaut.context.annotation.Bean List raw() { null }
}
''')
        BeanDefinition<?> target = context.getBeanDefinition(List)
        DelegatingBeanDefinition<?> wrapper = [getTarget: { target }] as DelegatingBeanDefinition

        expect:
        target.declaredBeanType.rawType
        wrapper.declaredBeanType.rawType

        cleanup:
        context.close()
    }

    void "a bean of each bean produced by a factory says its bean type was declared raw"() {
        given:
        ApplicationContext context = buildContext('''
package test
@jakarta.inject.Singleton class Seed {}
@io.micronaut.context.annotation.Factory
class Factory {
    @io.micronaut.context.annotation.EachBean(Seed)
    List raw(Seed seed) { null }
}
''')

        expect:
        context.getBeanDefinition(List).declaredBeanType.rawType

        cleanup:
        context.close()
    }

    void "a single raw bound stays raw"() {
        given:
        ApplicationContext context = buildContext('''
package test
@jakarta.inject.Singleton
class Bean<T extends List> { Bean(T value) {} }
''')
        GenericPlaceholder<?> value = (GenericPlaceholder<?>) context.getBeanDefinition(context.classLoader.loadClass('test.Bean')).constructor.arguments[0]

        expect:
        value.bounds[0].rawType

        cleanup:
        context.close()
    }

    void "a single bound that is itself a variable is the variable"() {
        given:
        ApplicationContext context = buildContext('''
package test
@jakarta.inject.Singleton
class Bean<U extends Number, T extends U> { Bean(T value) {} }
''')
        GenericPlaceholder<?> value = (GenericPlaceholder<?>) context.getBeanDefinition(context.classLoader.loadClass('test.Bean')).constructor.arguments[0]

        expect:
        value.variableName == 'T'
        value.bounds[0] instanceof GenericPlaceholder
        ((GenericPlaceholder<?>) value.bounds[0]).variableName == 'U'
        value.bounds[0].type == Number

        cleanup:
        context.close()
    }

    void "a producer of a type variable declares the variable"() {
        given:
        ApplicationContext context = buildContext('''
package test
@io.micronaut.context.annotation.Factory
class Factory {
    @io.micronaut.context.annotation.Bean public <T extends Number> T number() { null }
}
''')
        Argument<?> declared = context.getBeanDefinition(Number).declaredBeanType

        expect:
        declared instanceof GenericPlaceholder
        !((GenericPlaceholder<?>) declared).resolved
        ((GenericPlaceholder<?>) declared).variableName == 'T'
        declared.type == Number

        and: 'while the bean type as an argument is what it always was'
        !(context.getBeanDefinition(Number).asArgument() instanceof GenericPlaceholder)

        cleanup:
        context.close()
    }
}
