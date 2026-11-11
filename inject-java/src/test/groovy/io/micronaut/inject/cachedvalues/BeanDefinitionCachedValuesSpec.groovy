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
package io.micronaut.inject.cachedvalues

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.order.Ordered
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.DelegatingBeanDefinition

/**
 * The values kept by {@link BeanDefinition#getCachedValue(Class, java.util.function.Function)}.
 */
class BeanDefinitionCachedValuesSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface Label {
    String value();
}

@Singleton
@Label("plain")
class Plain {
}

@Singleton
@Label("${cached.label}")
class Placeholder {
}

@Singleton
@Label("#{ 'evaluated' }")
class Evaluated {
}
'''

    void "a value is computed once and kept per key"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        BeanDefinition<?> definition = definition(context, 'test.Plain')
        int calls = 0

        when:
        def first = definition.getCachedValue(Name, d -> { calls++; new Name(d.stringValue('test.Label').orElseThrow()) })
        def second = definition.getCachedValue(Name, d -> { calls++; new Name('other') })

        then:
        first.value() == 'plain'
        second.is(first)
        calls == 1

        when: 'another key'
        def other = definition.getCachedValue(Other, d -> { calls++; new Other() })

        then:
        definition.getCachedValue(Other, d -> { calls++; new Other() }).is(other)
        definition.getCachedValue(Name, d -> { calls++; new Name('other') }).is(first)
        calls == 2

        cleanup:
        context.close()
    }

    void "a null value is kept"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        BeanDefinition<?> definition = definition(context, 'test.Plain')
        int calls = 0

        expect:
        definition.getCachedValue(Name, d -> { calls++; null }) == null
        definition.getCachedValue(Name, d -> { calls++; new Name('x') }) == null
        calls == 1

        cleanup:
        context.close()
    }

    void "a value derived from property placeholders is computed again when the properties change"() {
        given:
        ApplicationContext context = buildContext('test.Source', SOURCE, false, ['cached.label': 'first'])
        BeanDefinition<?> definition = definition(context, 'test.Placeholder')
        def factory = { BeanDefinition<?> d -> new Name(d.stringValue('test.Label').orElseThrow()) }

        when:
        def first = definition.getCachedValue(Name, factory)

        then:
        first.value() == 'first'
        definition.getCachedValue(Name, factory).is(first)

        when:
        context.getEnvironment().addPropertySource(PropertySource.of('override', ['cached.label': 'second'], Ordered.HIGHEST_PRECEDENCE))
        def second = definition.getCachedValue(Name, factory)

        then:
        second.value() == 'second'
        definition.getCachedValue(Name, factory).is(second)

        cleanup:
        context.close()
    }

    void "a value of a definition with evaluated expressions is not kept"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        BeanDefinition<?> definition = definition(context, 'test.Evaluated')
        int calls = 0

        when:
        definition.getCachedValue(Other, d -> { calls++; new Other() })
        definition.getCachedValue(Other, d -> { calls++; new Other() })

        then:
        calls == 2

        cleanup:
        context.close()
    }

    void "an unevaluated value is kept despite evaluated expressions, apart from the value"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        BeanDefinition<?> definition = definition(context, 'test.Evaluated')
        int calls = 0

        when:
        def first = definition.getCachedUnevaluatedValue(Other, d -> { calls++; new Other() })

        then:
        definition.getCachedUnevaluatedValue(Other, d -> { calls++; new Other() }).is(first)
        calls == 1

        when: 'the value under the same key'
        def value = definition.getCachedValue(Other, d -> { calls++; new Other() })

        then:
        !value.is(first)
        calls == 2

        cleanup:
        context.close()
    }

    void "an unevaluated value and a value are kept apart under the same key"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        BeanDefinition<?> definition = definition(context, 'test.Plain')

        when:
        def value = definition.getCachedValue(Other, d -> new Other())
        def unevaluated = definition.getCachedUnevaluatedValue(Other, d -> new Other())

        then:
        !value.is(unevaluated)
        definition.getCachedValue(Other, d -> new Other()).is(value)
        definition.getCachedUnevaluatedValue(Other, d -> new Other()).is(unevaluated)

        cleanup:
        context.close()
    }

    void "a wrapping definition hands the call to the definition it wraps"() {
        given:
        ApplicationContext context = buildContext('test.Source', '''
package test;

import io.micronaut.context.annotation.EachProperty;

@EachProperty("cached.each")
class Each {
    String value;

    public void setValue(String value) {
        this.value = value;
    }
}
''', false, ['cached.each.a.value': 'a', 'cached.each.b.value': 'b'])
        List<BeanDefinition<?>> definitions = context.getBeanDefinitions(context.getClassLoader().loadClass('test.Each')).toList()
        List<BeanDefinition<?>> seen = []

        when:
        def first = definitions[0].getCachedValue(Other, d -> { seen << d; new Other() })
        def second = definitions[1].getCachedValue(Other, d -> { seen << d; new Other() })
        def unevaluated = definitions[1].getCachedUnevaluatedValue(Other, d -> { seen << d; new Other() })

        then:
        definitions.size() == 2
        definitions.every { it instanceof DelegatingBeanDefinition }
        definitions[0].getTarget().is(definitions[1].getTarget())
        second.is(first)
        !unevaluated.is(first)
        seen.size() == 2
        seen.every { it.is(definitions[0].getTarget()) }

        cleanup:
        context.close()
    }

    private static BeanDefinition<?> definition(ApplicationContext context, String className) {
        return context.getBeanDefinition(context.getClassLoader().loadClass(className))
    }

    static final class Name {
        private final String value

        Name(String value) {
            this.value = value
        }

        String value() {
            return value
        }
    }

    static final class Other {
    }
}
