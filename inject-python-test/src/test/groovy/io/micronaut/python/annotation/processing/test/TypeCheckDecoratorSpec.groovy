package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.diagnostic.PythonDiagnostic
import io.micronaut.python.processing.typecheck.TypeCheckMode
import spock.lang.Specification

class TypeCheckDecoratorSpec extends Specification {

    private static List<PythonDiagnostic> check(String source, TypeCheckMode mode = TypeCheckMode.ERROR) {
        List<PythonDiagnostic> diagnostics = []
        try {
            PyronautCompiler.builder()
                .pythonCode(source)
                .typeCheck(mode)
                .pythonDiagnosticCallback { diagnostics.add(it) }
                .build()
                .buildClassLoader()
        } catch (RuntimeException ignored) {
            // the diagnostics of a failing compilation are what the tests read
        }
        return diagnostics
    }

    void "a misspelt annotation member is reported with the member it most likely meant"() {
        when:
        def diagnostics = check('''
from micronaut.http.annotation import Controller, Post

@Controller("/books")
class BookController:
    @Post(value="/save", consume="application/json")
    def save(self, title: str) -> str:
        return title
''')

        then:
        diagnostics.size() == 1
        with(diagnostics[0]) {
            rule() == 'unknown-decorator-member'
            message() == '@Post has no member [consume]; did you mean [consumes]?'
            suggestions() == ['consumes']
            span().line() == 6
            span().column() == 26
            kind() == PythonDiagnostic.Kind.ERROR
        }
    }

    void "a literal that cannot be the member's value is reported"() {
        when:
        def diagnostics = check('''
from micronaut.http.annotation import Controller, Get
from micronaut.context.annotation import Requires

@Controller("/books")
@Requires(beans=[1])
class BookController:
    @Get(1)
    def index(self) -> str:
        return "x"
''')

        then:
        diagnostics*.rule() == ['decorator-member-type', 'decorator-member-type']
        diagnostics*.message() == [
            'member [beans] of @Requires is java.lang.Class[]; got int',
            'member [value] of @Get is java.lang.String; got int'
        ]
        diagnostics*.span()*.line() == [6, 8]
    }

    void "a list is only accepted by an array member, and a Python-defined annotation is checked by member names only"() {
        when:
        def diagnostics = check('''
from micronaut.http.annotation import Controller, Get
from micronaut.context.annotation import Requires

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("test.Tagged")
def Tagged(name, count="1"):
    def decorator(target):
        return target
    return decorator

@Controller("/books")
@Requires(env=["test"])
class BookController:
    @Get(value=["/a", "/b"])
    @Tagged("x", 2)
    def index(self) -> str:
        return "x"

    @Get("/c")
    @Tagged("x", 2, 3)
    def other(self) -> str:
        return "y"
''')

        then:
        diagnostics*.rule() == ['decorator-member-type', 'decorator-positional']
        diagnostics[0].message() == 'member [value] of @Get is java.lang.String; got list'
        diagnostics[1].message() == '@Tagged takes 2 positional arguments (name, count); got 3'
    }

    void "positional arguments need a value member"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean

@Singleton("named")
@Bean("x")
class Service:
    pass
''')

        then:
        diagnostics*.rule() == ['decorator-positional', 'decorator-positional']
        diagnostics[0].message() == '@Singleton takes no arguments'
        diagnostics[1].message().startsWith('@Bean has no [value] member for a positional argument; name the member, one of [')
        diagnostics*.span()*.line() == [5, 6]
    }

    void "an annotation applied where it cannot go and a class used as a decorator are reported"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton
from micronaut.http.annotation import Controller, Get
from micronaut.python.aop import TestAround
from java.util import ArrayList

@Get("/")
@TestAround
class Service:
    @Controller("/x")
    def index(self) -> str:
        return "x"

@ArrayList
class Other:
    pass
''')

        then: "a method-targeted around binding on a class advises every method and is not reported"
        diagnostics*.rule() == ['decorator-target', 'decorator-target', 'not-an-annotation']
        diagnostics[0].message() == '@Get cannot be applied to class [Service]; its targets are [METHOD]'
        diagnostics[1].message().startsWith('@Controller cannot be applied to function [Service.index]; its targets are [')
        diagnostics[2].message() == '[java.util.ArrayList] is not an annotation and cannot decorate class [Other]'
    }

    void "an enum member is checked against the enum's constants"() {
        when:
        def diagnostics = check('''
from micronaut.http.annotation import Controller, Get
from micronaut.http import HttpStatus
from micronaut.http.annotation import Status

@Controller("/books")
class BookController:
    @Get("/")
    @Status("CREATE")
    def index(self) -> str:
        return "x"
''')

        then:
        diagnostics*.rule() == ['decorator-member-type']
        diagnostics[0].message() == 'member [value] of @Status is io.micronaut.http.HttpStatus, which has no constant [CREATE]; did you mean [CREATED]?'
    }

    void "warn mode reports without failing and plain Python decorators are ignored"() {
        when:
        List<PythonDiagnostic> diagnostics = []
        def classLoader = PyronautCompiler.builder()
            .pythonCode('''
from dataclasses import dataclass
from micronaut.http.annotation import Controller, Get

def traced(function):
    return function

@dataclass
class Book:
    title: str

@Controller("/books")
class BookController:
    @Get(uri="/", produce="text/plain")
    @traced
    def index(self) -> str:
        return "x"
''')
            .typeCheck(TypeCheckMode.WARN)
            .pythonDiagnosticCallback { diagnostics.add(it) }
            .build()
            .buildClassLoader()

        then:
        classLoader != null
        diagnostics*.rule() == ['unknown-decorator-member']
        diagnostics[0].kind() == PythonDiagnostic.Kind.WARNING
        diagnostics[0].message() == '@Get has no member [produce]; did you mean [produces]?'
    }

    void "nothing is checked unless switched on"() {
        expect:
        check('''
from micronaut.http.annotation import Controller, Get

@Controller("/books")
class BookController:
    @Get(produce="text/plain")
    def index(self) -> str:
        return "x"
''', TypeCheckMode.OFF).isEmpty()
    }
}
