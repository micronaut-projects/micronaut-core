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
package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.annotation.MutableAnnotationMetadata

class ResolvedGenericTypeAnnotationsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.*;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Usage {
    String value() default "occurrence";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Binding {
    String value() default "binding";
}

interface Container<E, F> {}
class Middle<T> implements Container<List<@Usage("occurrence") T>, List<T>> {}
@Introspected
class Concrete extends Middle<String> {}
@Introspected
class AnnotatedBinding extends Middle<@Binding("binding") String> {}
@Introspected
class SameAnnotationBinding extends Middle<@Usage("binding") String> {}
@Introspected
class NestedBinding extends Middle<List<@Binding("binding") String>> {}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent<T> {
    public List<@Usage("occurrence") T> items;
    public List<T> plain;
}
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Child extends Parent<String> {}
'''

    void "resolved supertype arguments preserve each occurrence's annotations"() {
        given:
        def introspection = buildBeanIntrospection('test.Concrete', SOURCE)
        def arguments = introspection.getTypeArguments(introspection.beanType.classLoader.loadClass('test.Container'))

        expect:
        arguments*.type == [List, List]
        arguments*.name == ['E', 'F']
        arguments[0].typeParameters[0].type == String
        arguments[0].typeParameters[0].annotationMetadata.stringValue('test.Usage').get() == 'occurrence'
        arguments[1].typeParameters[0].type == String
        arguments[1].typeParameters[0].annotationMetadata.empty
        introspection.getTypeArguments('test.Middle')[0].annotationMetadata.empty
    }

    void "inherited field arguments preserve annotations without changing a plain occurrence"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', SOURCE)
        def items = introspection.getRequiredProperty('items', List).asArgument().typeParameters[0]
        def plain = introspection.getRequiredProperty('plain', List).asArgument().typeParameters[0]

        expect:
        items.type == String
        items.annotationMetadata.stringValue('test.Usage').get() == 'occurrence'
        plain.type == String
        plain.annotationMetadata.empty
    }

    void "occurrence metadata is merged with metadata on the bound type"() {
        given:
        def introspection = buildBeanIntrospection('test.AnnotatedBinding', SOURCE)
        def arguments = introspection.getTypeArguments('test.Container')
        def annotated = arguments[0].typeParameters[0]
        def plain = arguments[1].typeParameters[0]

        expect:
        annotated.type == String
        annotated.annotationMetadata.stringValue('test.Usage').get() == 'occurrence'
        annotated.annotationMetadata.stringValue('test.Binding').get() == 'binding'
        plain.type == String
        !plain.annotationMetadata.hasAnnotation('test.Usage')
        plain.annotationMetadata.stringValue('test.Binding').get() == 'binding'
    }

    void "annotating a resolved placeholder preserves nested arguments of the bound type"() {
        given:
        def introspection = buildBeanIntrospection('test.NestedBinding', SOURCE)
        def arguments = introspection.getTypeArguments('test.Container')
        def annotated = arguments[0].typeParameters[0]
        def plain = arguments[1].typeParameters[0]

        expect:
        annotated.type == List
        annotated.annotationMetadata.hasAnnotation('test.Usage')
        annotated.typeParameters[0].type == String
        annotated.typeParameters[0].annotationMetadata.hasAnnotation('test.Binding')
        plain.type == List
        !plain.annotationMetadata.hasAnnotation('test.Usage')
        plain.typeParameters[0].type == String
        plain.typeParameters[0].annotationMetadata.hasAnnotation('test.Binding')
    }

    void "the occurrence takes precedence over the same annotation on the bound type"() {
        given:
        def introspection = buildBeanIntrospection('test.SameAnnotationBinding', SOURCE)
        def arguments = introspection.getTypeArguments('test.Container')

        expect:
        arguments[0].typeParameters[0].annotationMetadata.stringValue('test.Usage').get() == 'occurrence'
        arguments[1].typeParameters[0].annotationMetadata.stringValue('test.Usage').get() == 'binding'
    }

    void "the type model keeps occurrence values ahead of binding values"() {
        expect:
        buildClassElement('''
package test;
import java.lang.annotation.*;
import java.util.List;
class Test extends Middle<@Usage("binding") String> {}
class Middle<T> implements Container<List<@Usage("occurrence") T>, List<T>> {}
interface Container<E, F> {}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Usage { String value(); }
''') { element ->
            def annotated = element.getAllTypeArguments()['test.Container'].E.typeArguments.values().first()
            assert annotated.genericTypeAnnotationMetadata.stringValue('test.Usage').get() == 'occurrence'
            assert annotated.typeAnnotationMetadata.stringValue('test.Usage').get() == 'occurrence'
            assert MutableAnnotationMetadata.of(annotated.typeAnnotationMetadata).stringValue('test.Usage').get() == 'occurrence'
            true
        }
    }
}
