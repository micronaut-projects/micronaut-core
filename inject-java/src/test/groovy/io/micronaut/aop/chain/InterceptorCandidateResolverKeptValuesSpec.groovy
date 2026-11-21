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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.beans.BeanConstructor
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition

/**
 * The interceptor bindings the candidate resolver reads from a definition are kept on it.
 */
class InterceptorCandidateResolverKeptValuesSpec extends AbstractTypeElementSpec {

    void 'the lifecycle binding of a prototype is computed once for all its creations'() {
        given:
        ApplicationContext context = buildContext('''
package keptlifecycle;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements Interceptor<Object, Object> {
    int calls;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Prototype
@Tracked
class Bean {
    @PostConstruct
    void init() {
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('keptlifecycle.Bean')
        BeanDefinition<?> definition = context.getBeanDefinition(beanType)
        def interceptor = context.getBean(context.classLoader.loadClass('keptlifecycle.TrackingInterceptor'))

        when:
        context.getBean(beanType)
        def first = definition.getCachedUnevaluatedValue(InterceptorCandidateResolver.LifecycleBinding, d -> null)
        context.getBean(beanType)

        then:
        interceptor.calls == 2
        first != null
        definition.getCachedUnevaluatedValue(InterceptorCandidateResolver.LifecycleBinding, d -> null).is(first)

        cleanup:
        context.close()
    }

    void 'the constructor binding of a definition is computed once for all its creations'() {
        given:
        ApplicationContext context = buildContext('''
package keptconstructor;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@interface Built {
}

@Singleton
@InterceptorBean(Built.class)
class BuildingInterceptor implements ConstructorInterceptor<Object> {
    int calls;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        calls++;
        return context.proceed();
    }
}

@Prototype
@Built
class Bean {
}
''')
        Class<?> beanType = context.classLoader.loadClass('keptconstructor.Bean')
        BeanDefinition<?> definition = context.getBeanDefinition(beanType)
        def interceptor = context.getBean(context.classLoader.loadClass('keptconstructor.BuildingInterceptor'))
        def resolver = new InterceptorCandidateResolver(context.getBean(io.micronaut.aop.InterceptorRegistry))

        when:
        def resolutionContext = new io.micronaut.context.DefaultBeanResolutionContext(context, definition)
        BeanConstructor constructor = [
            getAnnotationMetadata: { -> AnnotationMetadata.EMPTY_METADATA },
            getDeclaringBeanType : { -> beanType },
            getArguments         : { -> Argument.ZERO_ARGUMENTS }
        ] as BeanConstructor
        def candidates = resolver.resolveConstructorCandidates(resolutionContext, definition, constructor)
        def first = definition.getCachedUnevaluatedValue(InterceptorCandidateResolver.ConstructorBinding, d -> null)
        resolver.resolveConstructorCandidates(resolutionContext, definition, constructor)
        context.getBean(beanType)

        then:
        candidates.size() == 1
        first != null
        definition.getCachedUnevaluatedValue(InterceptorCandidateResolver.ConstructorBinding, d -> null).is(first)
        interceptor.calls == 1

        cleanup:
        resolutionContext?.close()
        context.close()
    }
}
