"""
Unit tests for micronaut_processor.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import unittest

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


if __name__ == "__main__":
    unittest.main()
