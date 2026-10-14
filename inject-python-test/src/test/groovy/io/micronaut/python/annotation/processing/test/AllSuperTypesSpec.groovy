package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.WildcardElement

class AllSuperTypesSpec extends AbstractPythonTypeElementSpec {

    private static final String TYPES = '''
from abc import ABC, abstractmethod
from typing import Generic, TypeVar

T = TypeVar('T')
X = TypeVar('X')

class View(ABC, Generic[T]):
    @abstractmethod
    def view(self) -> T: ...

class Named(ABC):
    @abstractmethod
    def name(self) -> str: ...

class Titled(Named):
    @abstractmethod
    def title(self) -> str: ...

class Box(View[T], Named):
    def __init__(self):
        self.value = None
    def view(self) -> T:
        return self.value
    def name(self) -> str:
        return "box"

class Mid(Box[X], Titled):
    def title(self) -> str:
        return "mid"

class Leaf(Mid[str]):
    pass

class Diamond(Box[str], Titled, Named):
    def title(self) -> str:
        return "diamond"

class Other:
    def __init__(self):
        self.other = 1

class Pair(Leaf, Other):
    pass

class Unparameterized(Box):
    pass

class Ordered(Box[T], Generic[T]):
    pass

class Swapped(dict[V, T], Generic[T, V]):
    pass
'''

    void "the closure of a class binds the variables of its bases along the path"() {
        expect:
        closure('Leaf') == ['python.Leaf', 'python.Mid<java.lang.String>', 'python.Box<java.lang.String>', 'python.View<java.lang.String>', 'python.Named', 'python.Titled', 'java.lang.Object']
    }

    void "a base reached by both sides of a diamond is listed once, where it is reached first"() {
        expect:
        closure('Diamond') == ['python.Diamond', 'python.Box<java.lang.String>', 'python.View<java.lang.String>', 'python.Named', 'python.Titled', 'java.lang.Object']
    }

    void "every concrete base of a class is walked, in declaration order"() {
        expect:
        closure('Pair') == ['python.Pair', 'python.Leaf', 'python.Mid<java.lang.String>', 'python.Box<java.lang.String>', 'python.View<java.lang.String>', 'python.Named', 'python.Titled', 'python.Other', 'java.lang.Object']
    }

    void "a generic base written without arguments is bound to the bounds of its variables, Python having no raw types"() {
        expect:
        closure('Unparameterized') == ['python.Unparameterized', 'python.Box<java.lang.Object>', 'python.View<java.lang.Object>', 'python.Named', 'java.lang.Object']
    }

    void "the variables of an open generic class reach its bases as placeholders"() {
        given:
        List<String> lines = []
        buildClassElement(TYPES, 'Ordered') { ClassElement element ->
            lines.addAll(closure(element))
            lines << (element.getAllSuperTypes()[2].typeArguments['T'] instanceof GenericPlaceholderElement ? 'placeholder' : 'bound')
        }

        expect:
        lines == ['python.Ordered<java.lang.Object>', 'python.Box<T>', 'python.View<T>', 'python.Named', 'java.lang.Object', 'placeholder']
    }

    void "a Java base of an open generic class binds the variables of its own super types by position"() {
        given:
        List<String> lines = []
        buildClassElement(TYPES, 'Swapped') { ClassElement element ->
            lines.addAll(closure(element))
        }

        expect:
        // dict is java.util.Map: Map<V, T> in the variables of Swapped, not Map<K, V> in its own
        lines == ['python.Swapped<java.lang.Object, java.lang.Object>', 'java.util.Map<V, T>', 'java.lang.Object']
    }

    void "a Java base is walked as the Java element declares it"() {
        expect:
        closure('''
from java.util.function import Function

class Echo(Function[str, str]):
    def apply(self, value: str) -> str:
        return value
''', 'Echo') == ['python.Echo', 'java.util.function.Function<java.lang.String, java.lang.String>', 'java.lang.Object']
    }

    private List<String> closure(String simpleName) {
        closure(TYPES, simpleName)
    }

    private List<String> closure(String code, String simpleName) {
        List<String> result = []
        buildClassElement(code, simpleName) { ClassElement element ->
            result.addAll(closure(element))
        }
        return result
    }

    private static List<String> closure(ClassElement type) {
        type.getAllSuperTypes().collect { render(it) }
    }

    /**
     * A type as its name and arguments, an unbound variable as its name, so the closure reads as it is written.
     */
    private static String render(ClassElement type) {
        if (type instanceof GenericPlaceholderElement placeholder && placeholder.resolved.isEmpty()) {
            return placeholder.variableName + "[]" * type.arrayDimensions
        }
        if (type instanceof WildcardElement wildcard) {
            return "?" + wildcard.upperBounds.findAll { it.name != "java.lang.Object" }.collect { " extends " + render(it) }.join("") +
                wildcard.lowerBounds.collect { " super " + render(it) }.join("")
        }
        String dimensions = "[]" * type.arrayDimensions
        if (type.rawType || type.typeArguments.isEmpty()) {
            return type.name + dimensions
        }
        return type.name + "<" + type.typeArguments.values().collect { render(it) }.join(", ") + ">" + dimensions
    }
}
