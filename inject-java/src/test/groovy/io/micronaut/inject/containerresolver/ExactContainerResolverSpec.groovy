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
import io.micronaut.context.exceptions.DependencyInjectionException
import spock.lang.Unroll

class ExactContainerResolverSpec extends AbstractTypeElementSpec {
    void 'annotation selects a context managed provider for fields constructors and methods'() {
        given:
        def context = buildContext('test.exactcontainers.Consumer', '''
            package test.exactcontainers;
            import jakarta.inject.*;
            import io.micronaut.context.annotation.*;
            import java.util.*;
            import io.micronaut.context.*;
            import io.micronaut.core.type.Argument;
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            @java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
            @ResolveWith(ExactProvider.class)
            @interface Exact {}
            @Singleton class ExactProvider implements BeanInjectionProvider {
                public <T> T get(BeanResolutionContext context, Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier, boolean nullable) {
                    if (context.getPath().peek() == null) throw new AssertionError("No requesting path");
                    return nullable ? context.findBean(argument, qualifier).orElse(null) : context.getBean(argument, qualifier);
                }
            }
            @Singleton
            class Consumer {
                @Inject @ResolveWith(ExactProvider.class) @Named("exact") Optional<String> fieldOptional;
                @Inject @ResolveWith(ExactProvider.class) @Named("exact") List<String> fieldList;
                @Inject @ResolveWith(ExactProvider.class) @Named("exact") Set<String> fieldSet;
                @Inject @ResolveWith(ExactProvider.class) @Named("exact") Map<String, String> fieldMap;
                @Inject @ResolveWith(ExactProvider.class) @Named("exact") Iterable<String> fieldIterable;
                @Inject @ResolveWith(ExactProvider.class) @Named("exact") java.util.stream.Stream<String> fieldStream;
                @Inject @ResolveWith(ExactProvider.class) @Named("exact") String[] fieldArray;
                @Inject @Exact @Named("exact") List<String> metaList;
                @Inject List<String> ordinaryList;
                @Inject Optional<Integer> ordinaryAbsent;
                @ResolveWith(ExactProvider.class) @Value("${answer:property}") Optional<String> property;
                final Optional<String> ctorOptional;
                final List<String> ctorList;
                final Set<String> ctorSet;
                Optional<String> methodOptional;
                List<String> methodList;
                Set<String> methodSet;
                Consumer(@ResolveWith(ExactProvider.class) @Named("exact") Optional<String> o, @ResolveWith(ExactProvider.class) @Named("exact") List<String> l, @ResolveWith(ExactProvider.class) @Named("exact") Set<String> s) {
                    ctorOptional = o; ctorList = l; ctorSet = s;
                }
                @Inject void init(@ResolveWith(ExactProvider.class) @Named("exact") Optional<String> o, @ResolveWith(ExactProvider.class) @Named("exact") List<String> l, @ResolveWith(ExactProvider.class) @Named("exact") Set<String> s) {
                    methodOptional = o; methodList = l; methodSet = s;
                }
            }
            @Factory class Producers {
                @Singleton @Named("exact") Optional<String> optional() { return Optional.of("optional"); }
                @Singleton @Named("exact") List<String> list() { return List.of("list"); }
                @Singleton @Named("exact") Set<String> set() { return Set.of("set"); }
                @Singleton @Named("exact") Map<String, String> map() { return Map.of("key", "map"); }
                @Singleton @Named("exact") Iterable<String> iterable() { return () -> List.of("iterable").iterator(); }
                @Singleton @Named("exact") java.util.stream.Stream<String> stream() { return java.util.stream.Stream.of("stream"); }
                @Singleton @Named("exact") String[] array() { return new String[]{"array"}; }
                @Singleton @Named("exact") String element() { return "element"; }
            }
        ''')

        when:
        def bean = getBean(context, 'test.exactcontainers.Consumer')

        then:
        bean.fieldOptional == Optional.of('optional')
        bean.ctorOptional == bean.fieldOptional
        bean.methodOptional == bean.fieldOptional
        bean.fieldList == ['list']
        bean.ctorList == bean.fieldList
        bean.methodList == bean.fieldList
        bean.fieldSet == (['set'] as Set)
        bean.ctorSet == bean.fieldSet
        bean.methodSet == bean.fieldSet
        bean.fieldMap == [key: 'map']
        bean.fieldIterable.toList() == ['iterable']
        bean.fieldStream.toList() == ['stream']
        bean.fieldArray.toList() == ['array']
        bean.metaList == bean.fieldList
        bean.ordinaryList.toSet() == (['list', 'set', 'array', 'element'] as Set)
        bean.ordinaryAbsent.empty
        bean.property == Optional.of('property')

        cleanup:
        context.close()
    }

    @Unroll
    void 'invalid provider result #result is rejected with the requesting path'(String result, String message) {
        given:
        def context = buildContext('test.providerresult.Consumer', '''
            package test.providerresult;
            import jakarta.inject.*;
            import io.micronaut.context.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.type.Argument;
            import java.util.List;
            @Singleton class Consumer {
                @Inject @ResolveWith(BadProvider.class) List<String> values;
            }
            @Singleton class BadProvider implements BeanInjectionProvider {
                public <T> T get(BeanResolutionContext context, Argument<T> argument,
                    io.micronaut.context.Qualifier<T> qualifier, boolean nullable) {
                    return (T) RESULT;
                }
            }
        '''.replace('RESULT', result))

        when:
        getBean(context, 'test.providerresult.Consumer')

        then:
        def failure = thrown(DependencyInjectionException)
        failure.message.contains(message)
        failure.message.contains('values')

        cleanup:
        context.close()

        where:
        result        | message
        'null'        | 'returned null'
        '"wrong"'     | 'returned java.lang.String'
    }

    void 'missing provider does not fall back to aggregation'() {
        given:
        def context = buildContext('test.missingprovider.Consumer', '''
            package test.missingprovider;
            import jakarta.inject.*;
            import io.micronaut.context.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.type.Argument;
            import java.util.List;
            @Singleton class Consumer {
                @Inject @ResolveWith(MissingProvider.class) List<String> values;
            }
            class MissingProvider implements BeanInjectionProvider {
                public <T> T get(BeanResolutionContext context, Argument<T> argument,
                    io.micronaut.context.Qualifier<T> qualifier, boolean nullable) { return null; }
            }
            @Factory class Elements { @Singleton String element() { return "element"; } }
        ''')

        when:
        getBean(context, 'test.missingprovider.Consumer')

        then:
        def failure = thrown(DependencyInjectionException)
        failure.message.contains('MissingProvider')
        failure.message.contains('values')

        cleanup:
        context.close()
    }

    void 'nullable provider value is accepted and context managed providers and values belong to the owner'() {
        given:
        def context = buildContext('test.providerownership.Consumer', '''
            package test.providerownership;
            import jakarta.inject.*;
            import jakarta.annotation.PreDestroy;
            import io.micronaut.context.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.type.Argument;
            import org.jspecify.annotations.Nullable;
            @Prototype class Consumer {
                @Inject @ResolveWith(OwnedProvider.class) Value value;
                @Inject @ResolveWith(NullProvider.class) @Nullable String absent;
            }
            @Prototype class OwnedProvider implements BeanInjectionProvider {
                static int destroyed;
                public <T> T get(BeanResolutionContext context, Argument<T> argument,
                    io.micronaut.context.Qualifier<T> qualifier, boolean nullable) {
                    return context.getBean(argument, qualifier);
                }
                @PreDestroy void close() { destroyed++; }
            }
            @Singleton class NullProvider implements BeanInjectionProvider {
                public <T> T get(BeanResolutionContext context, Argument<T> argument,
                    io.micronaut.context.Qualifier<T> qualifier, boolean nullable) {
                    if (!nullable) throw new AssertionError("Expected nullable request");
                    return null;
                }
            }
            @Prototype class Value { static int destroyed; @PreDestroy void close() { destroyed++; } }
        ''')
        def consumerType = context.classLoader.loadClass('test.providerownership.Consumer')
        def providerType = context.classLoader.loadClass('test.providerownership.OwnedProvider')
        def valueType = context.classLoader.loadClass('test.providerownership.Value')

        when:
        def registration = context.getBeanRegistration(consumerType, null)

        then:
        registration.bean.value != null
        registration.bean.absent == null
        providerType.destroyed == 0
        valueType.destroyed == 0

        when:
        registration.close()

        then:
        providerType.destroyed == 1
        valueType.destroyed == 1

        cleanup:
        context.close()
    }
}
