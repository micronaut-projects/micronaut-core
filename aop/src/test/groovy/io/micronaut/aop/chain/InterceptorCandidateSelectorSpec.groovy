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
package io.micronaut.aop.chain

import io.micronaut.aop.ConstructorInterceptor
import io.micronaut.aop.ConstructorInvocationContext
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorBindingDefinitions
import io.micronaut.aop.InterceptorCandidateSelector
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.InvocationContext
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanRegistration
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.beans.BeanConstructor
import io.micronaut.core.type.Executable
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanIdentifier
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

class InterceptorCandidateSelectorSpec extends Specification {

    void 'candidate selection preserves default order and supports filtering and stable sorting: #construct, #mode'() {
        given:
        def kind = construct ? InterceptorKind.AROUND_CONSTRUCT : InterceptorKind.AROUND
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredRepeatable(InterceptorBindingDefinitions.name,
            AnnotationValue.builder(InterceptorBinding).value('test.Binding').member('kind', kind).build())
        def method = Stub(ExecutableMethod) {
            getDeclaringType() >> Object
            getAnnotationMetadata() >> metadata
        }
        def constructor = Stub(BeanConstructor) {
            getDeclaringBeanType() >> Object
            getAnnotationMetadata() >> metadata
        }
        def a = new AAdvice()
        def b = new BAdvice()
        def early = new EarlyAdvice()
        def registrations = [b, a, early].collect { advice ->
            def definition = Stub(BeanDefinition) {
                getBeanType() >> advice.class
                getAnnotationMetadata() >> metadata
                getTypeArguments(ConstructorInterceptor) >> []
            }
            new BeanRegistration(BeanIdentifier.of(advice.class.name), definition, advice)
        }
        def selector = Mock(InterceptorCandidateSelector)
        def context = Stub(BeanContext)
        def registry = mode == 'none' ? new DefaultInterceptorRegistry(context) :
            new DefaultInterceptorRegistry(context, [selector])

        when:
        def result = construct ? registry.resolveConstructorInterceptors(constructor, registrations) :
            registry.resolveInterceptors(method, registrations, kind)

        then:
        if (mode != 'none') {
            1 * selector._(_, kind, _) >> { point, actualKind, List candidates ->
                assert point.is(construct ? constructor : method)
                assert candidates*.bean == [early, b, a]
                if (mode == 'drop') {
                    candidates.removeIf { it.bean.is(b) }
                } else {
                    candidates.sort({ left, right ->
                        (left.order <=> right.order) ?: (mode == 'ties' ?
                            left.beanDefinition.beanType.name <=> right.beanDefinition.beanType.name : 0)
                    } as Comparator)
                }
            }
        }
        result.toList() == (mode == 'drop' ? [early, a] : mode == 'ties' ? [early, a, b] : [early, b, a])
        registrations*.bean == [b, a, early]
        0 * selector._

        where:
        [construct, mode] << [[false, true], ['none', 'drop', 'ties', 'stable']].combinations()
    }

    void 'selectors only see the candidates whose interceptor kind can run at the interception point: #construct'() {
        given:
        def kind = construct ? InterceptorKind.AROUND_CONSTRUCT : InterceptorKind.AROUND
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredRepeatable(InterceptorBindingDefinitions.name,
            AnnotationValue.builder(InterceptorBinding).value('test.Binding').member('kind', kind).build())
        def method = Stub(ExecutableMethod) {
            getDeclaringType() >> Object
            getAnnotationMetadata() >> metadata
        }
        def constructor = Stub(BeanConstructor) {
            getDeclaringBeanType() >> Object
            getAnnotationMetadata() >> metadata
        }
        def plain = new AAdvice()
        def methodOnly = new MethodOnlyAdvice()
        def constructorOnly = new ConstructorOnlyAdvice()
        def registrations = [plain, methodOnly, constructorOnly].collect { advice ->
            def definition = Stub(BeanDefinition) {
                getBeanType() >> advice.class
                getAnnotationMetadata() >> metadata
                getTypeArguments(ConstructorInterceptor) >> []
            }
            new BeanRegistration(BeanIdentifier.of(advice.class.name), definition, advice)
        }
        List<Object> seen = null
        def selector = new InterceptorCandidateSelector() {
            @Override
            <T> void selectInterceptors(Executable<T, ?> executable, InterceptorKind actualKind, List<BeanRegistration<Interceptor<T, ?>>> candidates) {
                seen = candidates*.bean
            }

            @Override
            <T> void selectConstructorInterceptors(BeanConstructor<T> beanConstructor, InterceptorKind actualKind, List<BeanRegistration<Interceptor<T, T>>> candidates) {
                seen = candidates*.bean
            }
        }
        def expected = construct ? [plain, constructorOnly] : [plain, methodOnly]

        when:
        def withSelector = resolve(new DefaultInterceptorRegistry(Stub(BeanContext), [selector]), construct, method, constructor, registrations, kind)
        def withoutSelector = resolve(new DefaultInterceptorRegistry(Stub(BeanContext)), construct, method, constructor, registrations, kind)

        then:
        seen == expected
        withSelector.toList() == expected
        withoutSelector.toList() == expected

        where:
        construct << [false, true]
    }

    void 'selectors run in their own order, each receiving the previous result'() {
        given:
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredRepeatable(InterceptorBindingDefinitions.name,
            AnnotationValue.builder(InterceptorBinding).value('test.Binding').member('kind', InterceptorKind.AROUND).build())
        def method = Stub(ExecutableMethod) {
            getDeclaringType() >> Object
            getAnnotationMetadata() >> metadata
        }
        def a = new AAdvice()
        def b = new BAdvice()
        def registrations = [a, b].collect { advice ->
            def definition = Stub(BeanDefinition) {
                getBeanType() >> advice.class
                getAnnotationMetadata() >> metadata
                getTypeArguments(ConstructorInterceptor) >> []
            }
            new BeanRegistration(BeanIdentifier.of(advice.class.name), definition, advice)
        }
        def first = Mock(InterceptorCandidateSelector)
        def second = Mock(InterceptorCandidateSelector)

        when:
        def result = new DefaultInterceptorRegistry(Stub(BeanContext), [first, second])
            .resolveInterceptors(method, registrations, InterceptorKind.AROUND)

        then:
        1 * first.selectInterceptors(method, InterceptorKind.AROUND, _) >> { args -> args[2].reverse(true) }

        then:
        1 * second.selectInterceptors(method, InterceptorKind.AROUND, _) >> { args ->
            assert args[2]*.bean == [b, a]
            args[2].remove(1)
        }
        result.toList() == [b]
    }

    void 'an empty selection returns no interceptors'() {
        given:
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredRepeatable(InterceptorBindingDefinitions.name,
            AnnotationValue.builder(InterceptorBinding).value('test.Binding').member('kind', InterceptorKind.AROUND).build())
        def method = Stub(ExecutableMethod) {
            getDeclaringType() >> Object
            getAnnotationMetadata() >> metadata
        }
        def advice = new AAdvice()
        def definition = Stub(BeanDefinition) {
            getBeanType() >> AAdvice
            getAnnotationMetadata() >> metadata
            getTypeArguments(ConstructorInterceptor) >> []
        }
        def registration = new BeanRegistration(BeanIdentifier.of(AAdvice.name), definition, advice)
        def selector = new InterceptorCandidateSelector() {
            @Override
            <T> void selectInterceptors(Executable<T, ?> executable, InterceptorKind kind, List<BeanRegistration<Interceptor<T, ?>>> candidates) {
                candidates.clear()
            }
        }

        expect:
        new DefaultInterceptorRegistry(Stub(BeanContext), [selector])
            .resolveInterceptors(method, [registration], InterceptorKind.AROUND).length == 0
    }

    private static Interceptor[] resolve(DefaultInterceptorRegistry registry, boolean construct, ExecutableMethod method,
                                         BeanConstructor constructor, List<BeanRegistration> registrations, InterceptorKind kind) {
        construct ? registry.resolveConstructorInterceptors(constructor, registrations) :
            registry.resolveInterceptors(method, registrations, kind)
    }

    static class MethodOnlyAdvice implements MethodInterceptor<Object, Object> {
        @Override
        Object intercept(MethodInvocationContext<Object, Object> context) {
            context.proceed()
        }
    }

    static class ConstructorOnlyAdvice implements ConstructorInterceptor<Object> {
        @Override
        Object intercept(ConstructorInvocationContext<Object> context) {
            context.proceed()
        }
    }

    static class AAdvice implements Interceptor<Object, Object> {
        @Override
        Object intercept(InvocationContext<Object, Object> context) {
            context.proceed()
        }
    }

    static class BAdvice extends AAdvice {}

    static class EarlyAdvice extends AAdvice {
        @Override
        int getOrder() { -10 }
    }
}
