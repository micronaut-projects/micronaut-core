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
package io.micronaut.inject.factory.scopedproxy

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Around
import io.micronaut.aop.Intercepted
import io.micronaut.aop.InterceptorBinding
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * A factory that produces a type annotated with a scoped proxy scope must not inherit the client proxy of that
 * scope when the producer declares a scope or a qualifier of its own, whether the scope is written on the type
 * directly or comes from an annotation mapper.
 */
class ScopedProxyTypeWithNamedProducerSpec extends AbstractTypeElementSpec {

    void "a named producer of a type with a #description scoped proxy scope produces no proxy"() {
        given:
        ApplicationContext context = buildContext(source(scopeAnnotation, ''))
        Class<?> type = context.classLoader.loadClass('scopedproxyproducer.Conn')

        when:
        def produced = context.getBean(type, Qualifiers.byName('second'))
        def classBean = context.getBean(classBeanDefinition(context, type))

        then:
        !(produced instanceof Intercepted)
        !context.getBeanDefinition(type, Qualifiers.byName('second')).hasStereotype(Around)
        produced.value() == 'second'
        classBean instanceof Intercepted
        classBean.value() == 'class'

        cleanup:
        context.close()

        where:
        description   | scopeAnnotation
        'direct'      | '@io.micronaut.inject.factory.scopedproxy.ProducerTestScope'
        'mapped'      | '@io.micronaut.inject.factory.scopedproxy.MappedProducerTestScope'
        'meta-mapped' | '@io.micronaut.inject.factory.scopedproxy.MetaMappedProducerTestScope'
    }

    void "a named producer of a type with a #description scoped proxy scope and a binding written on it produces no proxy"() {
        given:
        String binding = '@io.micronaut.aop.InterceptorBinding(kind = io.micronaut.aop.InterceptorKind.AROUND_CONSTRUCT, value = Logged.class)'
        ApplicationContext context = buildContext(source(scopeAnnotation + ' ' + binding, ''))
        Class<?> type = context.classLoader.loadClass('scopedproxyproducer.Conn')

        when:
        def produced = context.getBean(type, Qualifiers.byName('second'))
        def classBean = context.getBean(classBeanDefinition(context, type))

        then:
        !(produced instanceof Intercepted)
        interceptorBindings(context, type, 'second') == ['scopedproxyproducer.Logged']
        produced.value() == 'second'
        classBean.value() == 'class'

        cleanup:
        context.close()

        where:
        description   | scopeAnnotation
        'direct'      | '@io.micronaut.inject.factory.scopedproxy.ProducerTestScope'
        'meta-mapped' | '@io.micronaut.inject.factory.scopedproxy.MetaMappedProducerTestScope'
    }

    void "a producer keeps the scoped proxy scope it declares itself with a #description scope on the type"() {
        given:
        ApplicationContext context = buildContext(source(scopeAnnotation, '@io.micronaut.inject.factory.scopedproxy.ProducerTestScope'))
        Class<?> type = context.classLoader.loadClass('scopedproxyproducer.Conn')

        when:
        def produced = context.getBean(type, Qualifiers.byName('second'))

        then:
        produced instanceof Intercepted
        aroundMembers(context, type, 'second') == [proxyTarget: true, lazy: true]
        produced.value() == 'second'

        cleanup:
        context.close()

        where:
        description   | scopeAnnotation
        'direct'      | '@io.micronaut.inject.factory.scopedproxy.ProducerTestScope'
        'mapped'      | '@io.micronaut.inject.factory.scopedproxy.MappedProducerTestScope'
        'meta-mapped' | '@io.micronaut.inject.factory.scopedproxy.MetaMappedProducerTestScope'
    }

    void "a named producer keeps around advice of a type with a #description scoped proxy scope"() {
        given:
        ApplicationContext context = buildContext(source(scopeAnnotation + ' @Logged', ''))
        Class<?> type = context.classLoader.loadClass('scopedproxyproducer.Conn')
        def interceptor = context.getBean(context.classLoader.loadClass('scopedproxyproducer.LoggedInterceptor'))

        when:
        def produced = context.getBean(type, Qualifiers.byName('second'))
        def value = produced.value()

        then:
        produced instanceof Intercepted
        aroundMembers(context, type, 'second').isEmpty()
        interceptorBindings(context, type, 'second') == ['scopedproxyproducer.Logged']
        value == 'second'
        interceptor.invocations == ['second']

        cleanup:
        context.close()

        where:
        description   | scopeAnnotation
        'direct'      | '@io.micronaut.inject.factory.scopedproxy.ProducerTestScope'
        'mapped'      | '@io.micronaut.inject.factory.scopedproxy.MappedProducerTestScope'
        'meta-mapped' | '@io.micronaut.inject.factory.scopedproxy.MetaMappedProducerTestScope'
    }

    void "a named producer keeps the around advice it declares itself with a #description scope on the type"() {
        given:
        ApplicationContext context = buildContext(source(scopeAnnotation, '@Logged'))
        Class<?> type = context.classLoader.loadClass('scopedproxyproducer.Conn')
        def interceptor = context.getBean(context.classLoader.loadClass('scopedproxyproducer.LoggedInterceptor'))

        when:
        def produced = context.getBean(type, Qualifiers.byName('second'))
        def value = produced.value()

        then:
        produced instanceof Intercepted
        aroundMembers(context, type, 'second').isEmpty()
        interceptorBindings(context, type, 'second') == ['scopedproxyproducer.Logged']
        value == 'second'
        interceptor.invocations == ['second']

        cleanup:
        context.close()

        where:
        description   | scopeAnnotation
        'direct'      | '@io.micronaut.inject.factory.scopedproxy.ProducerTestScope'
        'mapped'      | '@io.micronaut.inject.factory.scopedproxy.MappedProducerTestScope'
        'meta-mapped' | '@io.micronaut.inject.factory.scopedproxy.MetaMappedProducerTestScope'
    }

    void "a named producer keeps the around member the first advice #advice gives on a type with a #description scoped proxy scope"() {
        given:
        ApplicationContext context = buildContext(source(scopeAnnotation + ' ' + advice, ''))
        Class<?> type = context.classLoader.loadClass('scopedproxyproducer.Conn')

        expect:
        aroundMembers(context, type, 'second') == [lazy: firstLazy]

        cleanup:
        context.close()

        where:
        description   | scopeAnnotation                                                          | advice          | firstLazy
        'direct'      | '@io.micronaut.inject.factory.scopedproxy.ProducerTestScope'           | '@Lazy @Eager'  | true
        'direct'      | '@io.micronaut.inject.factory.scopedproxy.ProducerTestScope'           | '@Eager @Lazy'  | false
        'meta-mapped' | '@io.micronaut.inject.factory.scopedproxy.MetaMappedProducerTestScope' | '@Lazy @Eager'  | true
        'meta-mapped' | '@io.micronaut.inject.factory.scopedproxy.MetaMappedProducerTestScope' | '@Eager @Lazy'  | false
    }

    private static Map<String, Object> aroundMembers(ApplicationContext context, Class<?> type, String name) {
        return context.getBeanDefinition(type, Qualifiers.byName(name)).getValues(Around.name)
            .collectEntries { member, value -> [(member.toString()): value] }
    }

    private static List<String> interceptorBindings(ApplicationContext context, Class<?> type, String name) {
        return context.getBeanDefinition(type, Qualifiers.byName(name)).getAnnotationValuesByType(InterceptorBinding)
            .collect { it.stringValue().orElse(null) }
    }

    private static BeanDefinition<?> classBeanDefinition(ApplicationContext context, Class<?> type) {
        return context.getBeanDefinitions(type).find { !it.stringValue(AnnotationUtil.NAMED).isPresent() }
    }

    private static String source(String typeAnnotations, String producerAnnotations) {
        return """
package scopedproxyproducer;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.factory.scopedproxy.ProducerTestScope;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

$typeAnnotations
class Conn {
    private final String value;

    Conn() {
        this("class");
    }

    Conn(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}

@Factory
class ConnFactory {
    @Singleton
    @Named("second")
    $producerAnnotations
    Conn second() {
        return new Conn("second");
    }
}

@Around
@Retention(RetentionPolicy.RUNTIME)
@interface Logged {
}

@Around(lazy = true)
@Retention(RetentionPolicy.RUNTIME)
@interface Lazy {
}

@Around(lazy = false)
@Retention(RetentionPolicy.RUNTIME)
@interface Eager {
}

@InterceptorBean(Logged.class)
class LoggedInterceptor implements MethodInterceptor<Object, Object> {
    public final List<Object> invocations = new CopyOnWriteArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Object result = context.proceed();
        invocations.add(result);
        return result;
    }
}

@Singleton
class ProducerTestScopeImpl extends AbstractConcurrentCustomScope<ProducerTestScope> {
    private final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();

    ProducerTestScopeImpl() {
        super(ProducerTestScope.class);
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
        return beans;
    }

    @Override
    public void close() {
        destroyScope(beans);
    }
}
"""
    }
}
