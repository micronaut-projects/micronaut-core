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

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class CustomVisitorSpec extends AbstractPythonTypeElementSpec {

    void setup() {
        clearVisitors()
    }

    void cleanup() {
        clearVisitors()
    }

    void "test class is visited by custom visitor"() {
        when:
        def definition = buildBeanDefinition('python', 'TestController', '''
from jakarta.inject import Inject
from micronaut.http.annotation import Controller, Get, Post

@Controller("/test")
class TestController:
    name: str | None

    def __init__(self, constructor_arg: str):
        self.name = constructor_arg

    @Inject
    def setter_method(self, method: str | None) -> None:
        self.name = method

    @Get("/getMethod")
    def get_method(self, argument: str) -> str:
        return ""

    @Post("/postMethod")
    def post_method(self) -> str:
        return ""
''')

        then:
        definition != null
        ControllerGetVisitor.visited() == ["python.TestController", "get_method"]
        ControllerAllElementsVisitor.visited().toSet() == [
            "python.TestController",
            "setter_method",
            "get_method",
            "post_method"
        ].toSet()
        GetMethodVisitor.visited().contains("python.TestController")
        GetMethodVisitor.visited().contains("get_method")
        !GetMethodVisitor.visited().contains("setter_method")
        !GetMethodVisitor.visited().contains("post_method")
    }

    void "test non controller class is not visited by controller filtered visitors"() {
        when:
        def definition = buildBeanDefinition('python', 'TestController', '''
from jakarta.inject import Inject, Singleton
from micronaut.http.annotation import Get, Post

@Singleton
class TestController:
    name: str | None

    def __init__(self):
        self.name = None

    @Inject
    def setter_method(self, method: str | None) -> None:
        self.name = method

    @Get("/getMethod")
    def get_method(self, argument: str) -> str:
        return ""

    @Post("/postMethod")
    def post_method(self) -> str:
        return ""
''')

        then:
        definition != null
        ControllerGetVisitor.visited().isEmpty()
        ControllerAllElementsVisitor.visited().isEmpty()
        GetMethodVisitor.visited().contains("python.TestController")
        GetMethodVisitor.visited().contains("get_method")
        !GetMethodVisitor.visited().contains("setter_method")
        !GetMethodVisitor.visited().contains("post_method")
        InjectElementVisitor.visited().containsAll(["python.TestController", "setter_method"])
    }

    void "test Generated class is not visited by custom visitors"() {
        when:
        buildBeanDefinition('python', 'TestGenerated', '''
from jakarta.inject import Inject
from micronaut.core.annotation import Generated
from micronaut.http.annotation import Controller, Get

@Generated
@Controller("/generated")
class TestGenerated:
    @Inject
    def setter_method(self, method: str | None) -> None:
        pass

    @Get("/getMethod")
    def get_method(self) -> str:
        return ""
''')

        then:
        ControllerGetVisitor.visited().isEmpty()
        ControllerAllElementsVisitor.visited().isEmpty()
        GetMethodVisitor.visited().isEmpty()
        InjectElementVisitor.visited().isEmpty()
    }

    private static void clearVisitors() {
        ControllerGetVisitor.clearVisited()
        ControllerAllElementsVisitor.clearVisited()
        GetMethodVisitor.clearVisited()
        InjectElementVisitor.clearVisited()
    }

    static class ControllerGetVisitor implements TypeElementVisitor<Controller, Get> {
        private static final List<String> VISITED_ELEMENTS = []

        static List<String> visited() {
            return new ArrayList<>(VISITED_ELEMENTS)
        }

        static void clearVisited() {
            VISITED_ELEMENTS.clear()
        }

        @Override
        void start(VisitorContext visitorContext) {
            clearVisited()
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        private static void visit(Element element) {
            VISITED_ELEMENTS.add(element.name)
        }
    }

    static class ControllerAllElementsVisitor implements TypeElementVisitor<Controller, Object> {
        private static final List<String> VISITED_ELEMENTS = []

        static List<String> visited() {
            return new ArrayList<>(VISITED_ELEMENTS)
        }

        static void clearVisited() {
            VISITED_ELEMENTS.clear()
        }

        @Override
        void start(VisitorContext visitorContext) {
            clearVisited()
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        private static void visit(Element element) {
            VISITED_ELEMENTS.add(element.name)
        }
    }

    static class GetMethodVisitor implements TypeElementVisitor<Object, Get> {
        private static final List<String> VISITED_ELEMENTS = []

        static List<String> visited() {
            return new ArrayList<>(VISITED_ELEMENTS)
        }

        static void clearVisited() {
            VISITED_ELEMENTS.clear()
        }

        @Override
        void start(VisitorContext visitorContext) {
            clearVisited()
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        private static void visit(Element element) {
            VISITED_ELEMENTS.add(element.name)
        }
    }

    static class InjectElementVisitor implements TypeElementVisitor<Object, Object> {
        private static final List<String> VISITED_ELEMENTS = []

        static List<String> visited() {
            return new ArrayList<>(VISITED_ELEMENTS)
        }

        static void clearVisited() {
            VISITED_ELEMENTS.clear()
        }

        @Override
        void start(VisitorContext visitorContext) {
            clearVisited()
        }

        @Override
        String getElementType() {
            return AnnotationUtil.INJECT
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            visit(element)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        private static void visit(Element element) {
            VISITED_ELEMENTS.add(element.name)
        }
    }
}
