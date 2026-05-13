/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.EnumConstantElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementQuery
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.PendingFeature

class TypeElementQuerySpec extends AbstractPythonTypeElementSpec {

    void "test default query visits class and methods"() {
        when:
        TypeElementQueryVisitor.ENABLED = true
        def definition = buildBeanDefinition("python", "QueryTarget", queryTargetSource())

        then:
        definition
        visitedPythonClassNames().contains("python.QueryTarget")
        visitedPythonQueryTargetMethodNames().containsAll(queryTargetSourceMethodNames())
        visitedPythonConstructors().isEmpty()
        visitedPythonFields().isEmpty()
        visitedPythonEnumConstants().isEmpty()

        cleanup:
        TypeElementQueryVisitor.cleanup()
    }

    void "test class only query excludes methods"() {
        when:
        TypeElementQueryVisitor.ENABLED = true
        TypeElementQueryVisitor.QUERY = TypeElementQuery.onlyClass()
        def definition = buildBeanDefinition("python", "QueryTarget", queryTargetSource())

        then:
        definition
        visitedPythonClassNames().contains("python.QueryTarget")
        visitedPythonMethods().isEmpty()
        visitedPythonConstructors().isEmpty()
        visitedPythonFields().isEmpty()
        visitedPythonEnumConstants().isEmpty()

        cleanup:
        TypeElementQueryVisitor.cleanup()
    }

    void "test method query visits methods"() {
        when:
        TypeElementQueryVisitor.ENABLED = true
        TypeElementQueryVisitor.QUERY = TypeElementQuery.onlyMethods()
        def definition = buildBeanDefinition("python", "QueryTarget", queryTargetSource())

        then:
        definition
        visitedPythonClassNames().contains("python.QueryTarget")
        visitedPythonQueryTargetMethodNames().containsAll(queryTargetSourceMethodNames())
        visitedPythonConstructors().isEmpty()
        visitedPythonFields().isEmpty()
        visitedPythonEnumConstants().isEmpty()

        cleanup:
        TypeElementQueryVisitor.cleanup()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0033")
    void "test constructor query visits python constructor"() {
        when:
        TypeElementQueryVisitor.ENABLED = true
        TypeElementQueryVisitor.QUERY = TypeElementQuery.DEFAULT.excludeAll().includeConstructors()
        def definition = buildBeanDefinition("python", "QueryTarget", queryTargetSource())

        then:
        definition
        visitedPythonClassNames().contains("python.QueryTarget")
        visitedPythonConstructors().size() == 1
        visitedPythonConstructors().first().parameters*.name == ["name"]
        visitedPythonMethods().isEmpty()
        visitedPythonFields().isEmpty()
        visitedPythonEnumConstants().isEmpty()

        cleanup:
        TypeElementQueryVisitor.cleanup()
    }

    private static String queryTargetSource() {
        '''
from micronaut.context.annotation import Prototype

@Prototype
class QueryTarget:
    name: str
    description: str

    def __init__(self, name: str):
        self.name = name

    def method_one(self) -> str:
        return self.name

    def method_two(self) -> str:
        return self.name

    def method_three(self) -> str:
        return self.name
'''
    }

    private static Set<String> visitedPythonClassNames() {
        TypeElementQueryVisitor.VISITED_CLASSES
            .findAll { isPythonElement(it) }
            *.name as Set
    }

    private static Set<String> visitedPythonQueryTargetMethodNames() {
        visitedPythonMethods()
            .findAll { it.declaringType.name == "python.QueryTarget" }
            *.name as Set
    }

    private static Set<String> queryTargetSourceMethodNames() {
        ["method_one", "method_two", "method_three"] as Set
    }

    private static List<MethodElement> visitedPythonMethods() {
        TypeElementQueryVisitor.VISITED_METHODS.findAll { isPythonElement(it) }
    }

    private static List<ConstructorElement> visitedPythonConstructors() {
        TypeElementQueryVisitor.VISITED_CONSTRUCTORS.findAll { isPythonElement(it) }
    }

    private static List<FieldElement> visitedPythonFields() {
        TypeElementQueryVisitor.VISITED_FIELDS.findAll { isPythonElement(it) }
    }

    private static List<EnumConstantElement> visitedPythonEnumConstants() {
        TypeElementQueryVisitor.VISITED_ENUM_CONSTANTS.findAll { isPythonElement(it) }
    }

    private static boolean isPythonElement(Object element) {
        element.class.name.startsWith("io.micronaut.python.processing.visitor.")
    }

    static class TypeElementQueryVisitor implements TypeElementVisitor<Object, Object> {
        static boolean ENABLED = false
        static List<ClassElement> VISITED_CLASSES = new ArrayList<>()
        static List<MethodElement> VISITED_METHODS = new ArrayList<>()
        static List<ConstructorElement> VISITED_CONSTRUCTORS = new ArrayList<>()
        static List<FieldElement> VISITED_FIELDS = new ArrayList<>()
        static List<EnumConstantElement> VISITED_ENUM_CONSTANTS = new ArrayList<>()
        static TypeElementQuery QUERY = TypeElementQuery.DEFAULT

        static void cleanup() {
            VISITED_CLASSES.clear()
            VISITED_METHODS.clear()
            VISITED_CONSTRUCTORS.clear()
            VISITED_FIELDS.clear()
            VISITED_ENUM_CONSTANTS.clear()
            QUERY = TypeElementQuery.DEFAULT
            ENABLED = false
        }

        @Override
        void start(VisitorContext visitorContext) {
            VISITED_CLASSES.clear()
            VISITED_METHODS.clear()
            VISITED_CONSTRUCTORS.clear()
            VISITED_FIELDS.clear()
            VISITED_ENUM_CONSTANTS.clear()
        }

        @Override
        TypeElementQuery query() {
            return QUERY
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_CLASSES.add(element)
            }
        }

        @Override
        void visitConstructor(ConstructorElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_CONSTRUCTORS.add(element)
            }
        }

        @Override
        void visitEnumConstant(EnumConstantElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_ENUM_CONSTANTS.add(element)
            }
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_FIELDS.add(element)
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_METHODS.add(element)
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
