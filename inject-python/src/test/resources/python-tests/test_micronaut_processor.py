"""
Unit tests for micronaut_processor.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import unittest

import java

import micronaut_processor as processor
from micronaut_processor import MicronautAstVisitor


class CollectingCallback:
    def __init__(self):
        self.items = []

    def apply(self, item):
        self.items.append(item)
        return item


def visit(source, package_name="pkg", file_name="module.py"):
    callback = CollectingCallback()
    visitor = MicronautAstVisitor(callback, package_name, file_name, None, None)
    visitor.visit(ast.parse(source))
    return visitor, callback.items


class KeywordAliasTest(unittest.TestCase):
    def test_trailing_underscore_shields_keywords_only(self):
        self.assertEqual("class", processor.normalize_python_keyword_alias("class_"))
        self.assertEqual("import", processor.normalize_python_keyword_alias("import_"))
        self.assertEqual("value_", processor.normalize_python_keyword_alias("value_"))
        self.assertEqual("plain", processor.normalize_python_keyword_alias("plain"))
        self.assertIsNone(processor.normalize_python_keyword_alias(None))


class DecoratorNameTest(unittest.TestCase):
    def decorator(self, source):
        return ast.parse(source).body[0].decorator_list[0]

    def test_simple_qualified_and_called_decorators(self):
        self.assertEqual("Singleton", processor.extract_decorator_name(self.decorator("@Singleton\ndef f(): pass")))
        self.assertEqual("mod.Decorator", processor.extract_decorator_name(self.decorator("@mod.Decorator\ndef f(): pass")))
        self.assertEqual("Get", processor.extract_decorator_name(self.decorator("@Get('/x')\ndef f(): pass")))
        self.assertIsNone(processor.extract_decorator_name(ast.Constant(1)))


class LiteralAttributeValueTest(unittest.TestCase):
    def value(self, source):
        return processor.literal_attribute_value(ast.parse(source).body[0].value)

    def test_literals_resolve_and_code_does_not_run(self):
        self.assertEqual(3, self.value("x = 3"))
        self.assertEqual("", self.value("x = ''"))
        self.assertEqual([1, "a"], self.value("x = [1, 'a']"))
        self.assertIsNone(self.value("x = compute()"))
        self.assertIsNone(self.value("x = __import__('os').getcwd()"))


class FunctionShapeTest(unittest.TestCase):
    def func(self, source):
        return ast.parse(source).body[0]

    def test_return_value_detection(self):
        self.assertTrue(processor.has_return_value(self.func("def f():\n    return 1")))
        self.assertFalse(processor.has_return_value(self.func("def f():\n    return")))
        self.assertFalse(processor.has_return_value(self.func("def f():\n    pass")))
        self.assertFalse(processor.has_return_value(self.func("def f():\n    def g():\n        return 1")))

    def test_yield_detection(self):
        self.assertTrue(processor.has_yield(self.func("async def f():\n    yield 1")))
        self.assertTrue(processor.has_yield(self.func("def f():\n    yield from g()")))
        self.assertTrue(processor.has_yield(self.func("async def f():\n    async with x:\n        for i in y:\n            yield i")))
        self.assertFalse(processor.has_yield(self.func("async def f():\n    return 1")))
        self.assertFalse(processor.has_yield(self.func("async def f():\n    def g():\n        yield 1")))
        self.assertFalse(processor.has_yield(self.func("async def f():\n    async def g():\n        yield 1\n    return g")))

    def test_abstract_and_placeholder_methods(self):
        self.assertTrue(processor.is_abstract_method(self.func("@abstractmethod\ndef f(self): pass")))
        self.assertTrue(processor.is_placeholder_method(self.func("def f(self):\n    ...")))
        self.assertFalse(processor.is_placeholder_method(self.func("def f(self):\n    return 1")))

    def test_argument_defaults_keep_literal_values(self):
        defaults = processor.extract_arg_defaults(self.func("def dec(prefix='x', count=1, flag=True, ref=Other, empty=''): pass"))
        self.assertEqual({"prefix": "x", "count": 1, "flag": True, "ref": "Other", "empty": ""}, defaults)

    def test_argument_defaults_convert_non_literal_expressions(self):
        visitor, _ = visit("""
from enum import Enum
class Colour(Enum):
    RED = "RED"
""")
        defaults = processor.extract_arg_defaults(
            self.func("def dec(enumValue=Colour.RED, names=['a', 'b'], ref=Colour): pass"),
            visitor,
        )
        # the Java side takes the constant name from the last segment, however the reference was resolved
        self.assertEqual("RED", defaults["enumValue"].split(".")[-1])
        self.assertEqual(["a", "b"], defaults["names"])
        self.assertEqual("pkg.Colour", defaults["ref"])

    def test_argument_defaults_drop_unconvertible_expressions(self):
        defaults = processor.extract_arg_defaults(
            self.func("def dec(total=1 + 2, names=['a', 1 + 2], text='Call(foo)', mapping={'k': 'v'}): pass")
        )
        # an expression the converter cannot read is no default at all, rather than an AST dump
        self.assertIsNone(defaults["total"])
        self.assertIsNone(defaults["names"])
        # a literal string that merely reads like an AST dump is an ordinary default
        self.assertEqual("Call(foo)", defaults["text"])
        self.assertEqual({"k": "v"}, defaults["mapping"])


class TypeAnnotationTest(unittest.TestCase):
    def test_simple_generic_forward_and_union_types(self):
        visitor, _ = visit("""
from typing import Optional
class Engine:
    pass
""")
        self.assertEqual("str", visitor._extract_type_name(ast.parse("str", mode="eval").body))
        self.assertEqual("pkg.Engine", visitor._extract_type_name(ast.parse("Engine", mode="eval").body))
        self.assertEqual("pkg.Engine", visitor._extract_type_name(ast.parse("'Engine'", mode="eval").body))
        self.assertEqual("list[pkg.Engine]", visitor._extract_type_name(ast.parse("list[Engine]", mode="eval").body))
        self.assertEqual("dict[str, int]", visitor._extract_type_name(ast.parse("dict[str, int]", mode="eval").body))
        self.assertEqual("str | int", visitor._extract_type_name(ast.parse("str | int | None", mode="eval").body))

    def test_parse_type_builds_structured_refs(self):
        visitor, _ = visit("class Engine:\n    pass\n")
        type_ref = visitor._parse_type(ast.parse("dict[str, list[Engine]]", mode="eval").body)
        self.assertEqual("dict", type_ref.name())
        self.assertEqual(2, len(type_ref.typeArguments()))
        self.assertEqual("str", type_ref.typeArguments()[0].name())
        self.assertEqual("list", type_ref.typeArguments()[1].name())
        self.assertEqual("pkg.Engine", type_ref.typeArguments()[1].typeArguments()[0].name())


class ClassParsingTest(unittest.TestCase):
    def test_dataclass_attributes_functions_and_decorators(self):
        visitor, items = visit("""
from dataclasses import dataclass
from jakarta.inject import Singleton

@dataclass
class Book:
    '''A book.'''
    title: str
    pages: int = 100

@Singleton
class Library:
    def add(self, book: Book) -> bool:
        return True
    async def fetch(self) -> Book:
        return None
""")
        classes = {item.name(): item for item in items if item.getClass().getSimpleName() == "ClassDef"}
        book = classes["Book"]
        self.assertEqual(["title", "pages"], [attribute.name() for attribute in book.attributes()])
        self.assertEqual("A book.", book.documentation())
        library = classes["Library"]
        self.assertEqual("jakarta.inject.Singleton", library.decorators()[0].annotationName())
        functions = {function.name(): function for function in library.functions()}
        self.assertEqual("pkg.Book", functions["add"].arguments().arguments()[0].typeAnnotation().name())
        self.assertEqual("bool", functions["add"].returnType().typeAnnotation().name())
        self.assertTrue(functions["fetch"].isAsync())
        self.assertFalse(functions["add"].isAsync())

    def test_placeholder_bodies_are_abstract_in_abstract_base_classes_only(self):
        visitor, items = visit("""
from abc import ABC, abstractmethod
from typing import Protocol

class Listener:
    def on_message(self, message: str) -> None:
        ...
    @abstractmethod
    def explicit(self) -> None:
        ...

class Operations(ABC):
    def run(self) -> None:
        ...
    def concrete(self) -> None:
        pass

class Contract(Protocol):
    def run(self) -> None:
        ...
""")
        classes = {item.name(): item for item in items if item.getClass().getSimpleName() == "ClassDef"}
        listener = {function.name(): function for function in classes["Listener"].functions()}
        self.assertFalse(listener["on_message"].isAbstract())
        self.assertTrue(listener["on_message"].hasPlaceholderBody())
        self.assertTrue(listener["explicit"].isAbstract())
        operations = {function.name(): function for function in classes["Operations"].functions()}
        self.assertTrue(operations["run"].isAbstract())
        self.assertFalse(operations["concrete"].isAbstract())
        self.assertFalse(operations["concrete"].hasPlaceholderBody())
        self.assertTrue(classes["Contract"].functions()[0].isAbstract())

    def test_docstrings_are_cleaned_like_inspect_getdoc(self):
        visitor, items = visit("""
class Llama:
    \"\"\"
    A llama.

    Llamas are domesticated.
        Indented line.
    \"\"\"

    name: str
    \"\"\"
        The name.
    \"\"\"

    def speak(self) -> str:
        \"\"\"
        Says hello.
        \"\"\"
        return "hello"
""")
        llama = [item for item in items if item.getClass().getSimpleName() == "ClassDef"][0]
        self.assertEqual("A llama.\n\nLlamas are domesticated.\n    Indented line.", llama.documentation())
        self.assertEqual("The name.", llama.attributes()[0].documentation())
        self.assertEqual("Says hello.", llama.functions()[0].documentation())

    def test_cleandoc_matches_inspect_cleandoc_for_whitespace_other_than_spaces(self):
        # Python 3.13 strips only spaces in inspect.cleandoc (as the compiler strips docstrings): a line of
        # a form feed or a no-break space, or a line indented by one, is content that keeps its margin
        import inspect
        docs = [
            "First.\n    \f\n    Body.\n",
            "First.\n    \xa0\n    Body.\n",
            "\f First.\n  Body.\n   \xa0Indented.\n",
            "First.\n\xa0   Body.\n    Second.\n",
            "\tFirst.\n\tBody.\n\t\tIndented.\n",
            "  \n\n  Only.\n  \n",
        ]
        for doc in docs:
            with self.subTest(doc=doc):
                self.assertEqual(inspect.cleandoc(doc), processor._cleandoc(doc))

    def test_python_defined_annotation_becomes_decorator_def(self):
        visitor, items = visit("""
def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("pkg.Tagged")
def tagged(prefix="x", count=1):
    def decorator(target):
        return target
    return decorator

@tagged(prefix="pre", count=2)
class Service:
    pass
""")
        decorators = [item for item in items if item.getClass().getSimpleName() == "DecoratorDef"]
        self.assertEqual(1, len(decorators))
        self.assertEqual("pkg.Tagged", decorators[0].annotationName())
        self.assertEqual({"prefix": "x", "count": 1}, dict(decorators[0].members()))
        service = [item for item in items if item.getClass().getSimpleName() == "ClassDef" and item.name() == "Service"][0]
        applied = service.decorators()[0]
        self.assertEqual("pkg.Tagged", applied.annotationName())
        self.assertEqual("pre", applied.members()["prefix"])
        self.assertEqual(2, applied.members()["count"])


class DecoratorToFunctionTest(unittest.TestCase):
    def test_imported_java_annotation_call_maps_positional_value(self):
        visitor, _ = visit("from micronaut.http.annotation import Get\n")
        node = ast.parse("@Get('/hello', produces='text/plain')\ndef f(): pass").body[0].decorator_list[0]
        decorator = processor.decorator_to_function(visitor, node)
        self.assertEqual("io.micronaut.http.annotation.Get", decorator.annotationName())
        self.assertEqual("/hello", decorator.members()["value"])
        self.assertEqual("text/plain", decorator.members()["produces"])

    def test_unknown_bare_name_is_not_a_decorator(self):
        visitor, _ = visit("x = 1\n")
        node = ast.parse("@plain\ndef f(): pass").body[0].decorator_list[0]
        self.assertIsNone(processor.decorator_to_function(visitor, node))


class _Found:
    """The Optional the visitor context answers with."""

    def __init__(self, value):
        self.value = value

    def orElse(self, default):
        return default if self.value is None else self.value


class _ClassPath:
    """A visitor context knowing a fixed set of classes, recording the names it is asked for."""

    def __init__(self, classes):
        self.classes = classes
        self.asked = []

    def getClassElement(self, name):
        self.asked.append(name)
        return _Found(self.classes.get(name))

    def getClassElements(self, package, pattern):
        return [element for name, element in self.classes.items() if name.rsplit(".", 1)[0] == package]


def _java_class():
    return java.type("io.micronaut.inject.ast.ClassElement").of(java.type("io.micronaut.context.annotation.Executable"))


def _python_class():
    metadata = java.type("io.micronaut.inject.annotation.MutableAnnotationMetadata")()
    metadata.addDeclaredAnnotation("io.micronaut.context.python.annotation.PythonClass", {})
    return java.type("io.micronaut.inject.ast.ClassElement").of(java.type("io.micronaut.context.annotation.Executable"), metadata, {})


class CompiledPythonClassImportTest(unittest.TestCase):
    def resolve(self, classes, module_name, imported_name):
        class_path = _ClassPath(classes)
        visitor = MicronautAstVisitor(CollectingCallback(), "app", "module.py", class_path, None)
        return visitor._resolve_compiled_python_class(module_name, imported_name), class_path.asked

    def test_module_form_and_package_form_of_a_compiled_python_class(self):
        self.assertEqual(
            ("lib.greeting.Greeter", ["lib.greeting.Greeter"]),
            self.resolve({"lib.greeting.Greeter": _python_class()}, "lib.greeting", "Greeter"))
        self.assertEqual(
            ("lib.Greeter", ["lib.greeting.Greeter", "lib.Greeter"]),
            self.resolve({"lib.Greeter": _python_class()}, "lib.greeting", "Greeter"))

    def test_top_level_module_classes_live_in_the_python_package(self):
        resolved, _ = self.resolve({"python.GreetingService": _python_class()}, "greeting_service", "GreetingService")
        self.assertEqual("python.GreetingService", resolved)

    def test_a_java_class_under_the_imported_name_stops_the_search(self):
        # from java.util import ArrayList: a Java import, so the package form (java.ArrayList) is never tried,
        # even when a compiled Python class of that name exists
        resolved, asked = self.resolve(
            {"java.util.ArrayList": _java_class(), "java.ArrayList": _python_class()}, "java.util", "ArrayList")
        self.assertIsNone(resolved)
        self.assertEqual(["java.util.ArrayList"], asked)

    def test_nothing_on_the_class_path(self):
        resolved, asked = self.resolve({}, "lib.greeting", "Greeter")
        self.assertIsNone(resolved)
        self.assertEqual(["lib.greeting.Greeter", "lib.Greeter"], asked)


if __name__ == "__main__":
    unittest.main()
