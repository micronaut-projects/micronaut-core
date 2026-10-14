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
package io.micronaut.inject.containerresolver

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanResolutionTraceMode

/**
 * Counts the resolutions of the {@code BeanInjectionProvider} a {@code @ResolveWith} point names through the
 * resolution trace: every bean the trace reports resolved is one resolution of it.
 */
class ResolveWithProviderCachingSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
        package test.providercache;
        import jakarta.inject.*;
        import jakarta.annotation.PreDestroy;
        import io.micronaut.context.*;
        import io.micronaut.context.annotation.*;
        import io.micronaut.core.type.Argument;
        @Prototype class Consumer {
            @Inject @ResolveWith(SharedProvider.class) Value one;
            @Inject @ResolveWith(SharedProvider.class) Value two;
            @Inject @ResolveWith(SharedProvider.class) Value three;
            @Inject @ResolveWith(OwnedProvider.class) Value ownedOne;
            @Inject @ResolveWith(OwnedProvider.class) Value ownedTwo;
            final Value constructorValue;
            Consumer(@ResolveWith(SharedProvider.class) Value constructorValue) {
                this.constructorValue = constructorValue;
            }
        }
        abstract class ResolvingProvider implements BeanInjectionProvider {
            public <T> T get(BeanResolutionContext context, Argument<T> argument,
                io.micronaut.context.Qualifier<T> qualifier, boolean nullable) {
                return context.getBean(argument, qualifier);
            }
        }
        @Singleton class SharedProvider extends ResolvingProvider {
            public String toString() { return "SHARED_PROVIDER_RESOLVED"; }
        }
        @Prototype class OwnedProvider extends ResolvingProvider {
            static int destroyed;
            @PreDestroy void close() { destroyed++; }
            public String toString() { return "OWNED_PROVIDER_RESOLVED"; }
        }
        @Prototype class Value {
            public String toString() { return "VALUE_RESOLVED"; }
        }
    '''

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.beanResolutionTrace(BeanResolutionTraceMode.STANDARD_OUT, 'test\\.providercache\\.Consumer')
    }

    void 'a singleton provider is resolved once for every marked point of every bean created'() {
        given:
        def context = buildContext('test.providercache.Consumer', SOURCE)
        def consumerType = context.classLoader.loadClass('test.providercache.Consumer')
        def ownedProviderType = context.classLoader.loadClass('test.providercache.OwnedProvider')
        def standardOut = System.out
        def captured = new ByteArrayOutputStream()

        when: 'five prototype beans with four points marked for the singleton provider are created'
        System.setOut(new PrintStream(captured, true))
        def registrations
        try {
            registrations = (1..5).collect { context.getBeanRegistration(consumerType, null) }
        } finally {
            System.setOut(standardOut)
        }
        def trace = captured.toString()

        then: 'each point is injected'
        registrations.every { it.bean.one != null && it.bean.two != null && it.bean.three != null && it.bean.constructorValue != null }
        registrations.every { it.bean.ownedOne != null && it.bean.ownedTwo != null }
        trace.count('VALUE_RESOLVED') == 5 * 6

        and: 'the singleton provider is resolved once, not once per point'
        trace.count('SHARED_PROVIDER_RESOLVED') == 1

        and: 'a provider that is not a singleton is still resolved for every point, as a dependent of the bean'
        trace.count('OWNED_PROVIDER_RESOLVED') == 5 * 2
        ownedProviderType.destroyed == 0

        when:
        registrations.each { it.close() }

        then:
        ownedProviderType.destroyed == 5 * 2

        cleanup:
        context.close()
    }
}
