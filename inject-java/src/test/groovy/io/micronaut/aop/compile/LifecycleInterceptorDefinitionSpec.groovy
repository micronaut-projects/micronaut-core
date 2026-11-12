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
        BeanDefinition<?> definition = ((MethodInvocationContext<?, ?>) context).findBeanDefinition().orElseThrow();
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

    void 'an around advice invocation does not know the definition of the proxied bean'() {
        given:
        ApplicationContext context = buildContext('''
package arounddefinition;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Watched {
}

@Singleton
@InterceptorBinding(value = Watched.class, kind = InterceptorKind.AROUND)
class WatchingInterceptor implements MethodInterceptor<Object, Object> {
    final List<Boolean> known = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        known.add(context.findBeanDefinition().isPresent());
        return context.proceed();
    }
}

@Singleton
@Watched
class Bean {
    public String work() {
        return "w";
    }
}
''')
        def bean = context.getBean(context.classLoader.loadClass('arounddefinition.Bean'))
        def interceptor = context.getBean(context.classLoader.loadClass('arounddefinition.WatchingInterceptor'))

        when:
        def result = bean.work()

        then:
        result == 'w'
        interceptor.known == [false]

        cleanup:
        context.close()
    }

    void 'a constructor interceptor reaches the definition of the bean it constructs'() {
        given:
        ApplicationContext context = buildContext('''
package constructordefinition;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@interface Built {
}

@Singleton
@InterceptorBean(Built.class)
class BuildingInterceptor implements ConstructorInterceptor<Object> {
    final List<BeanDefinition<?>> definitions = new ArrayList<>();
    int computed;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        BeanDefinition<?> definition = context.findBeanDefinition().orElseThrow();
        definitions.add(definition);
        definition.getCachedValue(Plan.class, d -> {
            computed++;
            return new Plan();
        });
        return context.proceed();
    }

    static final class Plan {
    }
}

@Prototype
@Built
class Bean {
}
''')
        Class<?> beanType = context.classLoader.loadClass('constructordefinition.Bean')
        def interceptor = context.getBean(context.classLoader.loadClass('constructordefinition.BuildingInterceptor'))

        when:
        context.getBean(beanType)
        context.getBean(beanType)

        then:
        interceptor.definitions.size() == 2
        interceptor.definitions.every { it.is(context.getBeanDefinition(beanType)) }
        interceptor.computed == 1

        cleanup:
        context.close()
    }

    void 'a lifecycle interceptor of a bean of each property reaches the definition the wrappers share'() {
        given:
        ApplicationContext context = buildContext('lifecycleeach.Source', '''
package lifecycleeach;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.inject.BeanDefinition;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements Interceptor<Object, Object> {
    final List<BeanDefinition<?>> definitions = new ArrayList<>();

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        definitions.add(((MethodInvocationContext<?, ?>) context).findBeanDefinition().orElseThrow());
        return context.proceed();
    }
}

@EachProperty("tracked")
@Tracked
class Each {
    String value;

    public void setValue(String value) {
        this.value = value;
    }

    @PostConstruct
    void init() {
    }
}
''', false, ['tracked.a.value': 'a', 'tracked.b.value': 'b'])
        Class<?> beanType = context.classLoader.loadClass('lifecycleeach.Each')
        def interceptor = context.getBean(context.classLoader.loadClass('lifecycleeach.TrackingInterceptor'))

        when:
        context.getBeansOfType(beanType)

        then:
        interceptor.definitions.size() == 2
        interceptor.definitions[0].is(interceptor.definitions[1])
        context.getBeanDefinitions(beanType).every { it.getTarget().is(interceptor.definitions[0]) }

        cleanup:
        context.close()
    }
}
