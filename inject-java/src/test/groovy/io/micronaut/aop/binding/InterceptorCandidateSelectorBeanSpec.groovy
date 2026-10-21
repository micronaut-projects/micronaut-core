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
package io.micronaut.aop.binding

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class InterceptorCandidateSelectorBeanSpec extends AbstractTypeElementSpec {

    void 'injected selectors filter and order method and constructor candidates: #mode'() {
        given:
        def context = buildContext("""
package candidateselector;

import io.micronaut.aop.*;
import io.micronaut.context.*;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Executable;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@interface Tracked {}

class Events {
    static final List<String> calls = new ArrayList<>();
    static final List<InterceptorKind> selected = new ArrayList<>();
}

@Singleton @InterceptorBean(Tracked.class)
class AAdvice implements Interceptor<Object, Object> {
    public Object intercept(InvocationContext<Object, Object> context) {
        Events.calls.add("A:" + context.getKind());
        return context.proceed();
    }
}

@Singleton @InterceptorBean(Tracked.class)
class BAdvice implements Interceptor<Object, Object> {
    public Object intercept(InvocationContext<Object, Object> context) {
        Events.calls.add("B:" + context.getKind());
        return context.proceed();
    }
}

${selectorSource(mode)}

@Singleton @Tracked
class Subject {
    public String work() { return "done"; }
}
""")
        def events = context.classLoader.loadClass('candidateselector.Events')

        when:
        context.getBean(context.classLoader.loadClass('candidateselector.Subject')).work()

        then:
        events.calls.findAll { it.endsWith(':AROUND_CONSTRUCT') } == expected.collect { "$it:AROUND_CONSTRUCT".toString() }
        events.calls.findAll { it.endsWith(':AROUND') } == expected.collect { "$it:AROUND".toString() }
        events.selected*.name().contains('AROUND_CONSTRUCT')
        events.selected*.name().contains('AROUND')

        cleanup:
        context.close()

        where:
        mode   | expected
        'drop' | ['A']
        'ties' | ['A', 'B']
    }

    private static String selectorSource(String mode) {
        return """
@Singleton
class Selector implements InterceptorCandidateSelector {
    @Override
    public <T> void selectInterceptors(Executable<T, ?> executable, InterceptorKind kind,
            List<BeanRegistration<Interceptor<T, ?>>> candidates) {
        if (!executable.getDeclaringType().getName().contains("Subject")) {
            throw new AssertionError("Wrong method");
        }
        select(kind, candidates);
    }

    @Override
    public <T> void selectConstructorInterceptors(BeanConstructor<T> constructor, InterceptorKind kind,
            List<BeanRegistration<Interceptor<T, T>>> candidates) {
        if (!constructor.getDeclaringBeanType().getName().contains("Subject")) {
            throw new AssertionError("Wrong constructor");
        }
        select(kind, candidates);
    }

    private <I extends Interceptor<?, ?>> void select(InterceptorKind kind, List<BeanRegistration<I>> candidates) {
        Events.selected.add(kind);
        ${mode == 'drop' ? 'candidates.removeIf(candidate -> candidate.getBeanDefinition().getBeanType().equals(BAdvice.class));' :
            'candidates.sort(Comparator.comparingInt((BeanRegistration<I> candidate) -> candidate.getOrder()).thenComparing(candidate -> candidate.getBeanDefinition().getBeanType().getName()));'}
    }
}
"""
    }
}
