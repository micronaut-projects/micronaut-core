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
package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A lifecycle interceptor reaches the definition of the bean whose event it intercepts, and keeps values on it.
 */
class LifecycleInterceptorDefinitionSpec extends AbstractTypeElementSpec {

    void 'a lifecycle interceptor keeps what it derives for the event on the intercepted definition'() {
        given:
        ApplicationContext context = buildContext('''
package lifecycledefinition;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionMethodReference;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements Interceptor<Object, Object> {
    final List<BeanDefinition<?>> definitions = new ArrayList<>();
    final List<Object> plans = new ArrayList<>();
    int computed;

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        BeanDefinition<?> definition = ((BeanDefinitionMethodReference<?, ?>) ((MethodInvocationContext<?, ?>) context).getExecutableMethod()).getBeanDefinition();
        definitions.add(definition);
        plans.add(definition.getCachedValue(Plan.class, d -> {
            computed++;
            return new Plan(d.getBeanType().getSimpleName());
        }));
        return context.proceed();
    }

    record Plan(String name) {
    }
}

@Prototype
@Tracked
class Bean {
    @PostConstruct
    void init() {
    }

    @PreDestroy
    void destroy() {
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('lifecycledefinition.Bean')
        def interceptor = context.getBean(context.classLoader.loadClass('lifecycledefinition.TrackingInterceptor'))

        when:
        def first = context.getBean(beanType)
        def second = context.getBean(beanType)
        context.destroyBean(first)

        then:
        interceptor.definitions.size() == 3
        interceptor.definitions.every { it.is(context.getBeanDefinition(beanType)) }
        interceptor.computed == 1
        interceptor.plans.every { it.is(interceptor.plans[0]) }
        interceptor.plans[0].name() == 'Bean'

        cleanup:
        context.close()
    }
}
