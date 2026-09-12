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
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.PropertyElementQuery

/**
 * The type a variable is bound to is read through the use that annotates it, so everything but the annotations
 * has to be answered by the type itself.
 */
class TypeAnnotatedClassElementSpec extends AbstractTypeElementSpec {

    private static final List<Closure<?>> ANSWERS = [
            { it.name }, { it.simpleName }, { it.packageName }, { it.canonicalName }, { it.description },
            { it.getDescription(false) }, { it.nativeType }, { it.package.name }, { it.getDocumentation(false) },
            { it.isPublic() }, { it.isProtected() }, { it.isPrivate() }, { it.isPackagePrivate() },
            { it.isStatic() }, { it.isFinal() }, { it.isAbstract() }, { it.isSynthetic() }, { it.modifiers },
            { it.isAssignable(Object) }, { it.isAssignable('java.lang.Object') }, { it.isAssignable(ClassElement.of(Object)) },
            { it.isTypeVariable() }, { it.isGenericPlaceholder() }, { it.isWildcard() }, { it.isRawType() },
            { it.isOptional() }, { it.optionalValueType }, { it.isContainerType() }, { it.isRecord() },
            { it.isSealed() }, { it.permittedSubclasses }, { it.isInner() }, { it.isEnum() }, { it.isProxy() },
            { it.isPrimitive() }, { it.isVoid() }, { it.isArray() }, { it.arrayDimensions }, { it.isInterface() },
            { it.hasUnresolvedTypes() }, { it.superType*.name }, { it.interfaces*.name }, { it.enclosingType*.name },
            { it.beanProperties*.name }, { it.syntheticBeanProperties*.name },
            { it.getBeanProperties(PropertyElementQuery.of(AnnotationMetadata.EMPTY_METADATA))*.name },
            { it.fields*.name }, { it.methods*.name }, { it.getEnclosedElements(ElementQuery.ALL_METHODS)*.name },
            { it.accessibleConstructors*.name }, { it.accessibleStaticCreators*.name },
            { it.boundGenericTypes*.name }, { it.declaredGenericPlaceholders*.name },
            { it.typeArguments }, { it.getTypeArguments('java.lang.Object') }, { it.allTypeArguments.keySet() },
            { it.type.name }, { it.genericType.name }, { it.rawClassElement.name }, { it.toString() }
    ]

    private static final String SOURCE = '''
package test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

class Test<X> implements Middle<X> {
}

class Strings implements Middle<String> {
}

interface Container<E, F> {
}

interface Middle<T> extends Container<@Marker T, T> {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE})
@interface Marker {
}
'''

    void "a bound variable answers for the variable it is bound to"() {
        expect:
        buildClassElement(SOURCE) { ClassElement leaf ->
            def bound = leaf.getAllTypeArguments().get('test.Container').E
            def variable = leaf.typeArguments.X

            assert bound instanceof GenericPlaceholderElement
            assert ANSWERS.collect { it.call(bound) } == ANSWERS.collect { it.call(variable) }
            assert bound.variableName == variable.variableName
            assert bound.bounds*.name == variable.bounds*.name
            assert bound.declaringElement*.name == variable.declaringElement*.name
            assert bound.genericNativeType == variable.genericNativeType
            assert bound.resolved*.name == variable.resolved*.name
            return true
        }
    }

    void "a bound concrete type answers for the type it is bound to"() {
        expect:
        buildClassElement(SOURCE.replace('class Test<X> implements Middle<X>', 'class Test implements Middle<String>')) { ClassElement leaf ->
            def bound = leaf.getAllTypeArguments().get('test.Container').E
            def string = leaf.getInterfaces()[0].typeArguments.T

            assert !(bound instanceof GenericPlaceholderElement)
            assert ANSWERS.collect { it.call(bound) } == ANSWERS.collect { it.call(string) }
            assert bound.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Marker')
            return true
        }
    }

    void "a copy of a bound type keeps the annotations of the use"() {
        expect:
        buildClassElement(SOURCE) { ClassElement leaf ->
            def bound = leaf.getAllTypeArguments().get('test.Container').E

            [bound.toArray(), bound.toArray().fromArray(), bound.withTypeArguments([:]),
             bound.withAnnotationMetadata(AnnotationMetadata.EMPTY_METADATA), bound.rawClassElement,
             bound.foldBoundGenericTypes({ type -> type })].each { copy ->
                assert copy.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Marker')
            }

            and: "the resolution of the variable is read the same way"
            assert bound.getResolved().map { it.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Marker') }.orElse(true)
            return true
        }
    }

    void "the annotations of a bound type can be changed"() {
        expect:
        buildClassElement(SOURCE) { ClassElement leaf ->
            def bound = leaf.getAllTypeArguments().get('test.Container').E

            bound.annotate('test.Added')
            assert bound.annotationMetadata.hasAnnotation('test.Added')

            bound.annotate(AnnotationValue.builder('test.Second').build())
            assert bound.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Second')

            bound.removeAnnotation('test.Added')
            assert !bound.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Added')

            bound.removeAnnotationIf({ value -> value.annotationName == 'test.Second' })
            assert !bound.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Second')

            and: "what the use annotates is read, not written, so removing it there leaves it"
            bound.removeStereotype('test.Marker')
            bound.removeAnnotation('test.Marker')
            assert bound.typeAnnotationMetadata.annotationMetadata.hasAnnotation('test.Marker')
            return true
        }
    }
}
