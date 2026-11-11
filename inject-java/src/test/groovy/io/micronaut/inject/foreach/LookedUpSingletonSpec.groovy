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
package io.micronaut.inject.foreach

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.inject.qualifiers.Qualifiers

class LookedUpSingletonSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

interface Service {
}

@Singleton
class DefaultService implements Service {
}

interface Greeter {
    String greet();
}

@Singleton
@Named("english")
class English implements Greeter {
    public String greet() { return "hello"; }
}

@Singleton
@Named("french")
class French implements Greeter {
    public String greet() { return "bonjour"; }
}
'''

    void "a singleton looked up by its class and by an interface is the same instance on every lookup"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class service = context.classLoader.loadClass('test.Service')
        Class impl = context.classLoader.loadClass('test.DefaultService')

        when:
        def byInterface = context.getBean(service)

        then:
        context.getBean(service).is(byInterface)
        context.getBean(impl).is(byInterface)

        cleanup:
        context.close()
    }

    void "a destroyed singleton is created again by the next lookup"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class service = context.classLoader.loadClass('test.Service')
        def first = context.getBean(service)
        context.getBean(service)

        when:
        context.destroyBean(first)
        def second = context.getBean(service)

        then:
        !second.is(first)
        context.getBean(service).is(second)

        cleanup:
        context.close()
    }

    void "a singleton registered at run time changes what a looked up type resolves to"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class service = context.classLoader.loadClass('test.Service')
        def existing = context.getBean(service)
        context.getBean(service)

        when: "a second implementation is registered"
        def another = [toString: { 'another' }].asType(service)
        context.registerSingleton(service, another)

        then: "the lookup no longer returns the one it resolved before, a registered singleton taking the type"
        context.getBean(service).is(another)

        and: "every implementation is still found"
        context.getBeansOfType(service).toSet() == [existing, another] as Set

        cleanup:
        context.close()
    }

    void "lookups of one type by different names resolve their own beans"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class greeter = context.classLoader.loadClass('test.Greeter')

        expect:
        2.times {
            assert context.getBean(greeter, Qualifiers.byName('english')).greet() == 'hello'
            assert context.getBean(greeter, Qualifiers.byName('french')).greet() == 'bonjour'
        }

        cleanup:
        context.close()
    }

    void "a non-unique lookup keeps failing once its answer is cached"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Class greeter = context.classLoader.loadClass('test.Greeter')

        when:
        context.getBean(greeter)

        then:
        thrown(NonUniqueBeanException)

        when:
        context.getBean(greeter)

        then:
        thrown(NonUniqueBeanException)

        cleanup:
        context.close()
    }
}
