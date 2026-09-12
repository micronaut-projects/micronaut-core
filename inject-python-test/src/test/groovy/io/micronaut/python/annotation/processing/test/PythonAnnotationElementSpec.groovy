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
package io.micronaut.python.annotation.processing.test

import io.micronaut.core.annotation.Introspected
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.annotation.Generated
import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Singleton

class PythonAnnotationElementSpec extends AbstractPythonTypeElementSpec {

    void "test isInherited"() {
        given:
        InheritedVisitor.RESULTS.clear()
        buildBeanDefinition('python', 'Test', '''
from jakarta.inject import Singleton
import java

Inherited = java.type("java.lang.annotation.Inherited")

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

@Inherited()
@micronaut_annotation("test.MyInheritedAnn")
def my_inherited_ann():
    def decorator(target):
        return target
    return decorator

@micronaut_annotation("test.MyPlainAnn")
def my_plain_ann():
    def decorator(target):
        return target
    return decorator

@Singleton
class Test:
    pass
''')

        expect: "a Python annotation declared with @Inherited is resolvable through the element model"
        InheritedVisitor.RESULTS['test.MyInheritedAnn']
        InheritedVisitor.RESULTS['test.MyPlainAnn'] == false

        and: "annotations resolved from the classpath answer too"
        InheritedVisitor.RESULTS[Introspected.name]
        InheritedVisitor.RESULTS[Singleton.name] == false
    }

    void "test getTargets, getRepeatableContainer and getRetentionPolicy"() {
        expect:
        buildClassElement('''
from jakarta.inject import Singleton
import java

Target = java.type("java.lang.annotation.Target")
ElementType = java.type("java.lang.annotation.ElementType")

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

@Target([ElementType.TYPE, ElementType.METHOD])
@micronaut_annotation("test.MyTargetedAnn")
def my_targeted_ann():
    def decorator(target):
        return target
    return decorator

@micronaut_annotation("test.MyRepeatableAnns")
def my_repeatable_anns(value):
    def decorator(target):
        return target
    return decorator

@micronaut_annotation("test.MyRepeatableAnn", repeated="test.MyRepeatableAnns")
def my_repeatable_ann(value):
    def decorator(target):
        return target
    return decorator

@micronaut_annotation("test.MyPlainAnn")
def my_plain_ann():
    def decorator(target):
        return target
    return decorator

@Singleton
class Test:
    pass
''') { ClassElement element ->
            // the Python visitor context resolves a Python declaration to its Python annotation element
            def context = element.environment.visitorContext()
            def targeted = (AnnotationElement) context.getClassElement('test.MyTargetedAnn').get()
            def repeatable = (AnnotationElement) context.getClassElement('test.MyRepeatableAnn').get()
            def plain = (AnnotationElement) context.getClassElement('test.MyPlainAnn').get()

            // a Python declaration answers with what was written on it
            assert targeted.getTargets() == EnumSet.of(ElementType.TYPE, ElementType.METHOD)
            assert targeted.getRetentionPolicy() == RetentionPolicy.RUNTIME
            assert repeatable.getRepeatableContainer() == Optional.of('test.MyRepeatableAnns')
            assert plain.getTargets() == AnnotationElement.DEFAULT_TARGETS
            assert plain.getRepeatableContainer() == Optional.empty()
            assert plain.getRetentionPolicy() == RetentionPolicy.RUNTIME

            // a Java annotation on the classpath answers with its own declaration
            def introspected = (AnnotationElement) context.getClassElement(Introspected.name).get()
            def singleton = (AnnotationElement) context.getClassElement(Singleton.name).get()
            def property = (AnnotationElement) context.getClassElement(Property.name).get()
            def generated = (AnnotationElement) context.getClassElement(Generated.name).get()
            assert introspected.getTargets() == EnumSet.of(ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE)
            assert singleton.getTargets() == AnnotationElement.DEFAULT_TARGETS
            assert property.getRepeatableContainer() == Optional.of(PropertySource.name)
            assert generated.getRetentionPolicy() == RetentionPolicy.CLASS
            assert singleton.getRetentionPolicy() == RetentionPolicy.RUNTIME
            return element
        }
    }

    static class InheritedVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Boolean> RESULTS = [:]

        @Override
        void visitClass(ClassElement element, VisitorContext visitorContext) {
            for (String name : ['test.MyInheritedAnn', 'test.MyPlainAnn', Introspected.name, Singleton.name]) {
                visitorContext.getClassElement(name).ifPresent { ClassElement ce ->
                    RESULTS.put(name, ((AnnotationElement) ce).isInherited())
                }
            }
        }
    }
}
