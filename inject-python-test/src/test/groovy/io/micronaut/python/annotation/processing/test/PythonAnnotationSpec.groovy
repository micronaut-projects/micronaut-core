package io.micronaut.python.annotation.processing.test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement

class PythonAnnotationSpec extends AbstractPythonTypeElementSpec {

    void "test import module with micronaut decorators"() {
        expect: "import alias of module works correctly"
        buildClassElement("""
import jakarta.inject
import micronaut.context.annotation
from typing import Optional

@inject.Singleton
class ImportTest:
    @annotation.Executable
    def get_value(self) -> str:
        return "ok"
""") { ClassElement classElement ->
            assert classElement.hasAnnotation(AnnotationUtil.SINGLETON)
            assert classElement.getMethods()[0].hasAnnotation(Executable)
            return classElement
        }
    }

    void "test uses an import alias with micronaut decorators"() {
        expect: "import alias of module works correctly"
        buildClassElement("""
import jakarta.inject as i
import micronaut.context.annotation as a
from typing import Optional

@i.Singleton
class ImportTest2:
    @a.Executable
    def get_value(self) -> str:
        return "ok"
""") { ClassElement classElement ->
            assert classElement.hasAnnotation(AnnotationUtil.SINGLETON)
            assert classElement.getMethods()[0].hasAnnotation(Executable)
            return classElement
        }
    }

    void "test from style import with alias"() {
        expect: "from style import alias works correctly"
        buildClassElement("""
from jakarta import inject as i
from micronaut.context import annotation as a
from typing import Optional

@i.Singleton
class ImportTest3:
    @a.Executable
    def get_value(self) -> str:
        return "ok"
""") { ClassElement classElement ->
            assert classElement.hasAnnotation(AnnotationUtil.SINGLETON)
            assert classElement.getMethods()[0].hasAnnotation(Executable)
            return classElement
        }
    }

    void "test from style import "() {
        expect: "from style importworks correctly"
        buildClassElement("""
from jakarta import inject
from micronaut.context import annotation
from typing import Optional

@inject.Singleton
class ImportTest4:
    @annotation.Executable
    def get_value(self) -> str:
        return "ok"
""") { ClassElement classElement ->
            assert classElement.hasAnnotation(AnnotationUtil.SINGLETON)
            assert classElement.getMethods()[0].hasAnnotation(Executable)
            return classElement
        }
    }
}
