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
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorBindingDefinitions
import io.micronaut.aop.InterceptorCandidateSelector
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.InvocationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanRegistration
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.beans.BeanConstructor
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
