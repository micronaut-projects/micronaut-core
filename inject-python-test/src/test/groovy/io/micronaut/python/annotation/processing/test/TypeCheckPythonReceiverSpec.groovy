package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.diagnostic.PythonDiagnostic
import io.micronaut.python.processing.typecheck.TypeCheckMode
import spock.lang.Specification

class TypeCheckPythonReceiverSpec extends Specification {

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

    void "a member that does not exist on a Python class of the compilation is reported"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton

@Singleton
class OrderService:
    def __init__(self):
        self.count = 0

    def place(self, order: str) -> str:
        self.count += 1
        return "placed " + order

    @property
    def total(self) -> int:
        return self.count

@Singleton
class Checkout:
    def __init__(self, orders: OrderService):
        self.orders = orders

    def run(self) -> str:
        self.orders.place("book")
        self.orders.plce("book")
        print(self.orders.total, self.orders.count, self.orders.totals)
        return self.orders.place("pen")
''')

        then:
        diagnostics*.rule() == ['unknown-python-member', 'unknown-python-member']
        diagnostics[0].message() == 'class [OrderService] has no member [plce]; did you mean [place]?'
        diagnostics[0].span().line() == 24
        diagnostics[1].message() == 'class [OrderService] has no member [totals]; did you mean [total]?'
    }

    void "arguments are checked against the Python signature"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton

@Singleton
class OrderService:
    def place(self, order: str, quantity: int = 1, *, note: str = "") -> str:
        return order

@Singleton
class Checkout:
    def __init__(self, orders: OrderService):
        self.orders = orders

    def run(self) -> str:
        self.orders.place("book")
        self.orders.place("book", 2, note="gift")
        self.orders.place("book", 2, 3)
        self.orders.place()
        self.orders.place("book", notes="gift")
        self.orders.place(1)
        self.orders.place("book", "two")
        return "x"
''')

        then:
        diagnostics*.rule() == ['python-arity', 'python-arity', 'python-arity', 'argument-type', 'argument-type']
        diagnostics[0].message() == '[OrderService.place] takes 2 positional arguments (order, quantity, *, note); got 3'
        diagnostics[1].message() == '[OrderService.place] is missing the arguments [order]'
        diagnostics[2].message() == '[OrderService.place] has no parameter [notes]; did you mean [note]?'
        diagnostics[3].message() == 'argument [order] of [OrderService.place] expects [str]; got int'
        diagnostics[4].message() == 'argument [quantity] of [OrderService.place] expects [int]; got str'
    }

    void "constructors of Python classes are checked and the instance type flows on"() {
        when:
        def diagnostics = check('''
from dataclasses import dataclass
from jakarta.inject import Singleton

@dataclass
class Book:
    title: str
    pages: int = 0

class Shelf:
    def __init__(self, name: str):
        self.name = name

    def add(self, book: Book) -> None:
        pass

@Singleton
class Library:
    def run(self) -> str:
        book = Book("x", 10)
        Book("x", 10, 3)
        shelf = Shelf("fiction")
        Shelf()
        shelf.add(book)
        shelf.add("not a book")
        shelf.ad(book)
        return book.title
''')

        then:
        diagnostics*.rule() == ['python-arity', 'python-arity', 'argument-type', 'unknown-python-member']
        diagnostics[0].message() == '[Book.__init__] takes 2 positional arguments (title, pages); got 3'
        diagnostics[1].message() == '[Shelf.__init__] is missing the arguments [name]'
        diagnostics[2].message() == 'argument [book] of [Shelf.add] expects [Book]; got str'
        diagnostics[3].message() == 'class [Shelf] has no member [ad]; did you mean [add]?'
    }

    void "positional-only, keyword-only and class-level calls follow the Python calling rules"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton

class Parser:
    def parse(self, text: str, /, *, strict: bool, limit: int = 0) -> str:
        return text

    @staticmethod
    def default(name: str) -> str:
        return name

    @classmethod
    def create(cls, name: str) -> "Parser":
        return cls()

    def loose(self, text, /, **options) -> str:
        return text

@Singleton
class Service:
    def run(self) -> str:
        parser = Parser()
        parser.parse("x", strict=True)
        parser.parse(text="x", strict=True)
        parser.parse("x")
        parser.parse("x", strict=1)
        parser.loose("x", text="again")
        Parser.default("x", "extra")
        Parser.create()
        Parser.parse(parser, "x", strict=True)
        Parser.parse("x", strict=True)
        Parser.default(1)
        return "x"
''')

        then:
        diagnostics*.rule() == ['python-arity', 'python-arity', 'argument-type', 'python-arity', 'python-arity', 'python-arity', 'argument-type']
        diagnostics[0].message() == '[Parser.parse] parameter [text] is positional-only (text, /, *, strict, limit)'
        diagnostics[1].message() == '[Parser.parse] is missing the arguments [strict]'
        diagnostics[2].message() == 'argument [strict] of [Parser.parse] expects [bool]; got int'
        diagnostics[3].message() == '[Parser.default] takes 1 positional arguments (name); got 2'
        diagnostics[4].message() == '[Parser.create] is missing the arguments [name]'
        diagnostics[5].message() == '[Parser.parse] is missing the arguments [text]'
        diagnostics[6].message() == 'argument [name] of [Parser.default] expects [str]; got int'
    }

    void "constructions use the inherited or implicit constructor, and keyword values are checked once"() {
        when:
        def diagnostics = check('''
from dataclasses import dataclass
from jakarta.inject import Singleton
from java.util import ArrayList
from attrs import define

class Base:
    def __init__(self, name: str):
        self.name = name

class Child(Base):
    pass

class Plain:
    pass

class Allocating:
    def __new__(cls, *args):
        return super().__new__(cls)

class Stack(ArrayList):
    pass

@define
class Generated:
    name: str

@Singleton
class Service:
    def run(self) -> str:
        Child("x")
        Child()
        Plain()
        Plain("x")
        Allocating(1, 2)
        Stack(10)
        Generated("x")
        Child(name=Plain().missing)
        return "x"
''')

        then:
        diagnostics*.rule() == ['python-arity', 'python-arity', 'unknown-python-member']
        diagnostics[0].message() == '[Child.__init__] is missing the arguments [name]'
        diagnostics[1].message() == '[Plain] takes no arguments; got 1'
        diagnostics[2].message() == 'class [Plain] has no member [missing]'
    }

    void "inherited members, Java bases, dynamic classes and unknown bases are handled"() {
        when:
        def diagnostics = check('''
from dataclasses import dataclass
from jakarta.inject import Singleton
from java.util import ArrayList
from pydantic import BaseModel

class Base:
    def greet(self) -> str:
        return "hi"

class Child(Base):
    def run(self) -> str:
        return self.greet() + self.greets()

class Stack(ArrayList):
    def push(self, value: str) -> None:
        self.add(value)
        self.ad(value)
        self.removeRange(0, 1)

class Dynamic:
    def __getattr__(self, name):
        return name

class Intercepting:
    def __getattribute__(self, name):
        return lambda *args: name

    def declared(self, value: int) -> int:
        return value

class Registry(type):
    pass

class Registered(metaclass=Registry):
    pass

def installing(cls):
    cls.extra = lambda self: 1
    return cls

@installing
class Installed:
    pass

class Model(BaseModel):
    pass

class Impl(Base):
    def __init__(self):
        self.extra = 1

class Holder:
    class Inner:
        def describe(self) -> str:
            return "inner"

@dataclass
class Item:
    name: str

@Singleton
class Service:
    def run(self, base: Base, items: list[Item]) -> str:
        Dynamic().anything()
        Intercepting().declared("not an int", 2)
        Intercepting().undeclared()
        Registered().installed_by_the_metaclass()
        Installed().extra()
        Model().whatever()
        Child().run()
        Stack().push("x")
        base.extra
        base.nothing
        Holder.Inner().describe()
        Holder.Inner().describ()
        Holder.Other()
        Item("x").getName()
        Item("x").getNam()
        return "x"
''')

        then: "a value may be a subclass, a nested class is a member, and a generated accessor is one too"
        diagnostics*.rule() == ['unknown-python-member'] * 6
        diagnostics[0].message() == 'class [Child] has no member [greets]; did you mean [greet]?'
        diagnostics[1].message() == 'class [Stack] has no member [ad]; did you mean [add]?'
        diagnostics[2].message() == 'class [Base] has no member [nothing]'
        diagnostics[3].message() == 'class [Holder.Inner] has no member [describ]; did you mean [describe]?'
        diagnostics[4].message() == 'class [Holder] has no member [Other]'
        diagnostics[5].message() == 'class [Item] has no member [getNam]'
    }
    void "an override of a Java method is typed as the generated class adopts it"() {
        when:
        // the stub adopts the inherited Optional<E> findById(ID): the value is an Optional, whatever
        // the Python hint says, so orElse is not reported on it
        def diagnostics = check('''
import java
from dataclasses import dataclass
from typing import Protocol
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected

MinimalCrudRepository = java.type("io.micronaut.python.annotation.processing.test.repository.MinimalCrudRepository")


@Introspected
@dataclass
class Product:
    name: str


class ProductRepository(MinimalCrudRepository[Product, int], Protocol):
    def findById(self, id: int) -> Product | None: ...


@Singleton
class Catalog:
    def __init__(self, products: ProductRepository):
        self.products = products

    def find(self, id: int) -> Product | None:
        return self.products.findById(id).orElse(None)
''')

        then:
        diagnostics.isEmpty()
    }

    void "an override of a generic Java method is typed with the type arguments the class gives its base"() {
        when:
        // findById returns an Optional of Product (E bound to Product), so orElse(None) is a Product,
        // and save(E) returns a Product: a member it lacks is reported on the Python class
        def diagnostics = check('''
import java
from dataclasses import dataclass
from typing import Protocol
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected

MinimalCrudRepository = java.type("io.micronaut.python.annotation.processing.test.repository.MinimalCrudRepository")


@Introspected
@dataclass
class Product:
    name: str


class ProductRepository(MinimalCrudRepository[Product, int], Protocol):
    def findById(self, id: int) -> Product | None: ...


@Singleton
class Catalog:
    def __init__(self, products: ProductRepository):
        self.products = products

    def find(self, id: int) -> str:
        return self.products.findById(id).orElse(None).nme

    def rename(self, product: Product) -> str:
        return self.products.save(product).nam
''')

        then:
        diagnostics*.rule() == ['unknown-python-member', 'unknown-python-member']
        diagnostics[0].message() == 'class [Product] has no member [nme]; did you mean [name]?'
        diagnostics[0].span().line() == 27
        diagnostics[1].message() == 'class [Product] has no member [nam]; did you mean [name]?'
        diagnostics[1].span().line() == 30
    }

}
