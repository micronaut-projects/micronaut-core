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
package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMap
import io.micronaut.core.annotation.AnnotationValue

class GeneratedAnnotationMapSpec extends AbstractTypeElementSpec {
    private static final String SCHEMA = """
        @io.micronaut.core.annotation.GenerateAnnotationMap
        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
        @interface Rule {
            String name() default "default";
            int count() default 7;
            int size() default 13;
        }
    """

    void "direct metadata preserves the generated interface and method hierarchies adapt merged values"() {
        given:
        def context = buildContext('example.Test', '''
            package example;
            @jakarta.inject.Singleton
            @io.micronaut.core.annotation.Introspected
            @Rule(name = "bean", count = 42)
            class Test {
                @io.micronaut.context.annotation.Executable
                @Rule(name = "method")
                public String greet(@Rule(name = "argument") String value) { return value; }
            }
        ''' + SCHEMA)
        def loader = context.classLoader
        def type = loader.loadClass('example.Test')
        def view = loader.loadClass('example.Rule$AnnotationMap')
        def definition = context.getBeanDefinition(type)
        def method = definition.getRequiredMethod('greet', String)
        def introspection = loader.loadClass('example.$Test$Introspection').getDeclaredConstructor().newInstance()

        expect:
        context.getBean(type) != null
        [definition.annotationMetadata, method.annotationMetadata.declaredMetadata,
         method.arguments[0].annotationMetadata, introspection.annotationMetadata].withIndex().every { metadata, i ->
            assert !metadata.hasStereotype('io.micronaut.core.annotation.GenerateAnnotationMap')
            def map = metadata.getValues('example.Rule')
            assert view.isInstance(map)
            assert view.getMethod('of', Map).invoke(null, map).is(map)
            assert map.name() == ['bean', 'method', 'argument', 'bean'][i]
            assert map.count() == (i in [0, 3] ? 42 : 7)
            assert map.member_size() == 13
            assert map.size() == (i in [0, 3] ? 2 : 1)
            assert !map.containsKey('size')
            assert metadata.stringValue('example.Rule', 'name').get() == map.name()
            assert metadata.intValue('example.Rule', 'count').orElse(7) == map.count()
            assert metadata.findAnnotation('example.Rule').get().values.is(map)
            true
        }
        def merged = view.getMethod('of', Map).invoke(null, method.annotationMetadata.getValues('example.Rule'))
        merged.name() == 'method'
        merged.count() == 42

        cleanup:
        context.close()
    }

    void "of preserves identity or creates a live converting adapter and immutable snapshots retain presence"() {
        given:
        def loader = buildClassLoader('example.Rule', 'package example; ' + SCHEMA)
        def view = loader.loadClass('example.Rule$AnnotationMap')
        def source = [name: 'one', count: '11', extra: 'unknown']
        def adapter = view.getMethod('of', Map).invoke(null, source)

        expect:
        adapter.name() == 'one'
        adapter.count() == 11
        view.getMethod('of', Map).invoke(null, adapter).is(adapter)
        adapter == source
        adapter.extra == 'unknown'
        !new AnnotationValue('example.Rule', adapter).values.is(adapter)
        view.getMethod('create', Map).invoke(null, source).is(source)

        when:
        source.name = 'two'
        adapter.compute('count') { k, v -> 19 }
        def frozen = view.getMethod('immutable', Map).invoke(null, source)
        source.name = 'three'

        then:
        adapter.name() == 'three'
        source.count == 19
        frozen.name() == 'two'
        frozen.count() == 19
        frozen.member_size() == 13
        !frozen.containsKey('size')
        frozen.extra == 'unknown'
        new AnnotationValue('example.Rule', frozen).values.is(frozen)

        when:
        frozen.compute('name') { k, v -> 'bad' }

        then:
        thrown(UnsupportedOperationException)
    }

    void "default only and stereotype annotations get typed storage"() {
        given:
        def context = buildContext('example.Test', '''
            package example;
            @jakarta.inject.Singleton @Alias class Test {}
            @Rule
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            @interface Alias {}
        ''' + SCHEMA)
        def metadata = context.getBeanDefinition(context.classLoader.loadClass('example.Test')).annotationMetadata
        def map = metadata.getValues('example.Rule')

        expect:
        map instanceof AnnotationMap
        map.isEmpty()
        map.name() == 'default'
        map.count() == 7
        metadata.findAnnotation('example.Rule').get().values.is(map)

        cleanup:
        context.close()
    }

    void "dynamic expressions use normal metadata resolution"() {
        given:
        def context = buildContext('example.Test', """
            package example;
            @jakarta.inject.Singleton @Rule(name = "$expression") class Test {}
        """ + SCHEMA, false, ['rule.name': 'resolved'])
        def metadata = context.getBeanDefinition(context.classLoader.loadClass('example.Test')).annotationMetadata

        expect:
        !(metadata.getValues('example.Rule') instanceof AnnotationMap)
        metadata.stringValue('example.Rule', 'name').get() == 'resolved'

        cleanup:
        context.close()

        where:
        expression << ['${rule.name}', "#{'resolved'}"]
    }

    void "inherited annotations retain typed storage through metadata copies"() {
        given:
        def context = buildContext('example.Test', '''
            package example;
            @jakarta.inject.Singleton class Test extends Parent {}
            @Rule(name = "parent", count = 21) class Parent {}
        ''' + SCHEMA.replace('@interface Rule', '@java.lang.annotation.Inherited @interface Rule'))
        def metadata = context.getBeanDefinition(context.classLoader.loadClass('example.Test')).annotationMetadata

        expect:
        metadata.getValues('example.Rule') instanceof AnnotationMap
        metadata.getValues('example.Rule').name() == 'parent'
        metadata.getValues('example.Rule').count() == 21

        cleanup:
        context.close()
    }

    void "unsupported annotation schemas fail at compile time"() {
        when:
        buildClassLoader('example.Rule', """
            package example;
            @io.micronaut.core.annotation.GenerateAnnotationMap
            @interface Rule { $member }
        """)

        then:
        def error = thrown(RuntimeException)
        error.message.contains(message)

        where:
        member                                      | message
        'long count() default 1;'                   | 'supports only String and int'
        'String name();'                            | 'requires a default'
        'String name() default "${dynamic}";'       | 'requires literal defaults'
        'int size() default 1; int member_size() default 2;' | 'accessor name collision'
    }
}
