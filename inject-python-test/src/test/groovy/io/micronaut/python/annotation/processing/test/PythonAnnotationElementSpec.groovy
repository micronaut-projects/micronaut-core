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
