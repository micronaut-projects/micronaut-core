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
package io.micronaut.aop.cachedvalues

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * The values kept by the methods of the proxies of non-singleton beans.
 */
class ProxyCachedValuesSpec extends AbstractBeanDefinitionSpec {

    @Shared
    @AutoCleanup
    ApplicationContext context = buildContext('''
package test

import io.micronaut.aop.Around
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Type
import jakarta.inject.Singleton
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Around
@Type(KeepInterceptor)
@Retention(RetentionPolicy.RUNTIME)
@interface Kept {
    String name() default ""
    String label() default ""
}

@Around(proxyTarget = true)
@Type(KeepInterceptor)
@Retention(RetentionPolicy.RUNTIME)
@interface KeptTarget {
}

@Singleton
class KeepInterceptor implements MethodInterceptor<Object, Object> {
    final List<Object> methods = []
    final List<Object> values = []
    final List<Object> compiledValues = []

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        methods.add(context.getExecutableMethod())
        values.add(context.getCachedValue(Probe, { m -> new Probe(m.stringValue(Kept, "name").orElse(null)) }))
        compiledValues.add(context.getCachedCompiledValue(Probe, { m -> new Probe(m.stringValue(Kept, "name").orElse(null)) }))
        return context.proceed()
    }
}

class Probe {
    final String name

    Probe(String name) {
        this.name = name
    }
}

@Prototype
class Proto {
    @Kept(name = "proto")
    String work() {
        return "w"
    }
}

@Prototype
class Evaluated {
    @Kept(name = "evaluated", label = "#{ 'x' }")
    String work() {
        return "w"
    }
}

@Prototype
@KeptTarget
class Target {
    String work() {
        return "w"
    }
}
''')

    def setup() {
        interceptor().methods.clear()
        interceptor().values.clear()
        interceptor().compiledValues.clear()
    }

    void "the proxies of a #type bean share the methods and the values they keep"() {
        when:
        def first = getBean(context, type)
        def second = getBean(context, type)
        first.work()
        second.work()

        then:
        !first.is(second)
        interceptor().methods.size() == 2
        interceptor().methods[0].is(interceptor().methods[1])
        interceptor().values[0].is(interceptor().values[1])
        interceptor().compiledValues[0].is(interceptor().compiledValues[1])

        where:
        type << ['test.Proto', 'test.Target']
    }

    void "a value derived from the compiled metadata is kept by a method with evaluated expressions"() {
        when:
        def first = getBean(context, 'test.Evaluated')
        def second = getBean(context, 'test.Evaluated')
        first.work()
        second.work()

        then:
        interceptor().methods[0].is(interceptor().methods[1])
        // the expressions can depend on the invocation: the value is computed for each one
        !interceptor().values[0].is(interceptor().values[1])
        interceptor().compiledValues[0].is(interceptor().compiledValues[1])
        interceptor().compiledValues[0].name == 'evaluated'
    }

    private Object interceptor() {
        getBean(context, 'test.KeepInterceptor')
    }
}
