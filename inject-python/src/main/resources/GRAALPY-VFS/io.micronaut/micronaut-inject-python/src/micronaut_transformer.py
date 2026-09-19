import ast
import keyword
import os
import re
import warnings
import java
from typing import Optional, Dict, List, Any, Set

PYTHON_KEYWORD_METHOD_ALIASES = {
    f"{name}_": name
    for name in keyword.kwlist
}

META_ANNOTATIONS_TO_SKIP_IN_SOURCE = {
    # ConfigurationReader is compile-time metadata for the configuration binder.
    # Python keeps these aliases in Micronaut annotation metadata; copying the
    # meta-annotation onto the generated decorator source is unnecessary and can
    # make the stub diverge from the Java APT model.
    "io.micronaut.context.annotation.ConfigurationReader",
}

META_ANNOTATION_PACKAGES_TO_SKIP_IN_SOURCE = (
    "java.lang.",
    "java.lang.annotation.",
)

def normalize_python_keyword_alias(name: str) -> str:
    if name.endswith('_') and keyword.iskeyword(name[:-1]):
        return name[:-1]
    return name

from ast import unparse

# ``io`` is Python's built-in module, so a Python import of ``io.<anything>`` can only ever name a Java
# package (io.micronaut, io.swagger, io.kubernetes, ...). The generated runtime packages therefore live
# without the ``io.`` prefix (``micronaut.context.annotation``, ``swagger.v3.oas.annotations``) and the
# runtime transformer rewrites such imports accordingly.
JAVA_IO_PACKAGE_PREFIX = 'io.'


def is_java_io_package(module_name: Optional[str]) -> bool:
    return bool(module_name) and module_name.startswith(JAVA_IO_PACKAGE_PREFIX)


def strip_java_io_prefix(module_name: str) -> str:
    return module_name[len(JAVA_IO_PACKAGE_PREFIX):] if is_java_io_package(module_name) else module_name


def unresolved_java_io_import_error(name: str, kind: str = 'import') -> str:
    return (
        f"Cannot resolve Java {kind} [{name}]: io is Python's built-in module, so io.* imports always refer to "
        f"Java packages, but no such {'class or package' if kind == 'import' else 'package'} was found on the "
        "compile classpath. Add the dependency that provides it or fix the import."
    )

_AnnotationTypes = java.type("io.micronaut.python.processing.util.PythonAnnotationTypes")
_JavaTypes = java.type("io.micronaut.python.processing.util.PythonJavaTypes")


def decorator_name(decorator: ast.AST) -> Optional[str]:
    """
    The name a decorator expression is applied under: ``X`` for ``@X``, ``@X(...)``, ``@m.X`` and ``@m.X(...)``.
    """
    if isinstance(decorator, ast.Call):
        decorator = decorator.func
    if isinstance(decorator, ast.Name):
        return decorator.id
    if isinstance(decorator, ast.Attribute):
        return decorator.attr
    return None


def dotted_name(expression: ast.AST) -> Optional[str]:
    """
    The dotted name an expression spells, ``i`` for ``i`` and ``jakarta.inject`` for ``jakarta.inject``, or
    ``None`` when it is not a name.
    """
    if isinstance(expression, ast.Name):
        return expression.id
    if isinstance(expression, ast.Attribute):
        qualifier = dotted_name(expression.value)
        return None if qualifier is None else f'{qualifier}.{expression.attr}'
    return None


def returns_nested_function(function: ast.FunctionDef) -> bool:
    """
    Whether the function has the decorator factory shape: it returns a function defined in its body.
    """
    nested_function_names = {
        statement.name
        for statement in function.body
        if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef))
    }
    return any(
        isinstance(node, ast.Return)
        and isinstance(node.value, ast.Name)
        and node.value.id in nested_function_names
        for node in ast.walk(function)
    )


def takes_target_directly(function: ast.FunctionDef) -> bool:
    """
    Whether a bare application of the function passes it the decorated target: it has exactly one required
    positional parameter (``def Traced(func): ...``, or a member without a default, ``def Tagged(value)``), or
    only ``*args`` (``def Star(*args): ...``). Such a function is never rewritten to a call, whatever it
    returns: ``@Traced`` stays ``Traced(target)``.
    """
    arguments = function.args
    positional = list(arguments.posonlyargs) + list(arguments.args)
    if not positional:
        return arguments.vararg is not None
    return len(positional) - len(arguments.defaults) == 1


def source_file_for_import(source_root: str, package_name: str, level: int, module_name: Optional[str]) -> Optional[str]:
    """
    The source file of the module an import statement names, when it belongs to the source root.
    """
    if not source_root:
        return None
    if level > 0:
        parts = package_name.split('.') if package_name else []
        module_parts = parts[:max(0, len(parts) - (level - 1))]
        if module_name:
            module_parts.extend(module_name.split('.'))
    elif module_name:
        module_parts = module_name.split('.')
    else:
        return None
    module_path = os.path.join(source_root, *module_parts)
    for candidate in (f"{module_path}.py", os.path.join(module_path, "__init__.py")):
        if os.path.isfile(candidate):
            return candidate
    return None


def package_name_of_source_file(source_root: str, source_file: str) -> str:
    relative = os.path.relpath(os.path.dirname(source_file), source_root)
    return '' if relative in ('', '.') else relative.replace(os.sep, '.')


class AnnotationFunctionScanner:
    """
    Finds the names a module binds to custom annotation functions: top-level functions of the decorator
    factory shape (``def Ann(...): def decorator(target): ...; return decorator``) that are decorated with
    ``micronaut_annotation``, with a Java annotation applicable to annotation types, or with another custom
    annotation function, as the processor recognises them. Each name maps to whether a bare application passes
    the function the decorated target (``takes_target_directly``): such a function, a wrapping decorator
    ``def Traced(func): ...`` or an annotation with a required member, is left bare, the others are rewritten
    to a call. Imported modules of the same source root are scanned in turn, so a bare ``@Ann`` can be told
    from a factory call ``@Ann(...)`` wherever ``Ann`` is used.

    The scan results of imported modules are cached for the duration of one scan (one module transform),
    not across compilations: a module's answer depends on the modules it imports and on the class path.
    """

    def __init__(self, callback_get_class_element, package_name: str = '', source_root: str = '',
                 cache: Optional[Dict[str, Dict[str, bool]]] = None, loading: Optional[Set[str]] = None):
        self.callback_get_class_element = callback_get_class_element
        self.package_name = package_name or ''
        self.source_root = source_root or ''
        # name -> whether a bare application passes the target directly
        self.annotation_functions: Dict[str, bool] = {}
        self.annotation_type_targets: Set[str] = set()
        self._cache: Dict[str, Dict[str, bool]] = {} if cache is None else cache
        self._loading: Set[str] = set() if loading is None else loading

    def scan(self, module: ast.Module) -> Dict[str, bool]:
        for statement in module.body:
            if isinstance(statement, ast.ImportFrom):
                self._scan_import(statement)
            elif isinstance(statement, ast.FunctionDef) and self.declares_annotation(statement):
                self.annotation_functions[statement.name] = takes_target_directly(statement)
        return self.annotation_functions

    def declares_annotation(self, function: ast.FunctionDef) -> bool:
        if not returns_nested_function(function):
            return False
        for decorator in function.decorator_list:
            name = decorator_name(decorator)
            if (name == 'micronaut_annotation'
                    or name in self.annotation_type_targets
                    or name in self.annotation_functions):
                return True
        return False

    def _scan_import(self, statement: ast.ImportFrom):
        if statement.level == 0 and not statement.module:
            return
        for alias in statement.names:
            if alias.name == '*':
                continue
            bound_name = alias.asname or alias.name
            if statement.level == 0:
                class_element = self.callback_get_class_element(f'{statement.module}.{alias.name}')
                if class_element is not None:
                    if (_AnnotationTypes.isAnnotationType(class_element)
                            and _AnnotationTypes.targetsAnnotationType(class_element)):
                        self.annotation_type_targets.add(bound_name)
                    continue
            module_file = source_file_for_import(self.source_root, self.package_name, statement.level, statement.module)
            if module_file is None:
                continue
            functions = self._module_annotation_functions(module_file)
            if alias.name in functions:
                self.annotation_functions[bound_name] = functions[alias.name]

    def _module_annotation_functions(self, module_file: str) -> Dict[str, bool]:
        key = os.path.abspath(module_file)
        cached = self._cache.get(key)
        if cached is not None:
            return cached
        if key in self._loading:
            # a circular import: the module is being scanned higher up the stack
            return {}
        self._loading.add(key)
        try:
            with open(module_file, 'r', encoding='utf-8') as source:
                module = ast.parse(source.read(), filename=module_file)
            scanner = AnnotationFunctionScanner(
                self.callback_get_class_element,
                package_name_of_source_file(self.source_root, module_file),
                self.source_root,
                self._cache,
                self._loading
            )
            names = scanner.scan(module)
        except (OSError, SyntaxError, ValueError):
            names = {}
        finally:
            self._loading.discard(key)
        self._cache[key] = names
        return names

# A single leading underscore: a double-underscore name is mangled when it is referenced inside a class
# body, and the decorator is applied to classes defined inside a class or a method as well
JAVA_INTERFACE_DEFAULTS_DECORATOR = '_micronaut_java_interface_defaults'

# The runtime class of a Python type does not extend the Java interfaces it implements, so the default
# methods of those interfaces are added by the runtime (io.micronaut.context.python.PythonInterfaceDefaults)
JAVA_INTERFACE_DEFAULTS_DECORATOR_CODE = f'''
def {JAVA_INTERFACE_DEFAULTS_DECORATOR}(*interface_names):
    def decorator(cls):
        import java
        return java.type('io.micronaut.context.python.PythonInterfaceDefaults').install(cls, interface_names)
    return decorator
'''


class MicronautTransformer(ast.NodeTransformer):
    """
    AST transformer that converts Java imports into appropriate Python constructs.
    Annotations become decorators, regular Java types become java.type() references.
    """

    def __init__(self, callback_get_class_element, callback_get_class_elements, strip_java_interface_bases=False,
                 package_name='', source_root=''):
        """
        Initialize the transformer.

        Args:
            callback_get_class_element: Function to lookup a single ClassElement by name
            callback_get_class_elements: Function to lookup ClassElements by package
            strip_java_interface_bases: Whether to strip Java interface bases from runtime classes
            package_name: The package of the module being transformed, when it belongs to a source root
            source_root: The source root the module belongs to, when there is one
        """
        self.callback_get_class_element = callback_get_class_element
        self.callback_get_class_elements = callback_get_class_elements
        self.strip_java_interface_bases = strip_java_interface_bases
        self.package_name = package_name or ''
        self.source_root = source_root or ''
        self.transformed_code = []
        self.java_type_assignments = []
        self.imports_to_transform = []
        self.generated_decorators = set()
        # Names bound to custom annotation functions, and whether a bare application passes them the target:
        # like the generated decorators, a bare @Ann is @Ann() unless Ann takes the target directly
        self.annotation_functions: Dict[str, bool] = {}
        self.generated_decorator_code = {}
        self.java_class_imports = {}
        self.java_interface_names = set()
        self.java_class_elements = {}
        self.java_keyword_method_aliases = {}
        self.java_keyword_safe_imports = set()
        self.validation_errors = []
        self.has_java_import = False
        self.exported_types = []
        self.all_class_names = []
        self.class_depth = 0
        self.function_depth = 0
        self.uses_builtin_exception = False
        self.uses_java_interface_defaults = False

    def visit_ImportFrom(self, node: ast.ImportFrom):
        """
        Transform import statements like:
        from jakarta.inject import Singleton
        from jakarta.inject import singleton
        from io.micronaut.core.annotation import *
        """
        if not node.module:
            return node

        if node.module == 'pyronaut.build':
            return None

        java_module = self._to_java_import_module(node.module)

        # The transformed source is only read by the compile-time processor, which resolves Java names,
        # so the module keeps its Java name here (``io.`` included); only the runtime transformer strips it.
        transformed_module = self._to_python_import_module(java_module)
        # A relative import (``from .io.util import helper``) names an application sub-package, never Java
        java_io_package = node.level == 0 and is_java_io_package(java_module)

        # Collect imports to transform - check if JavaVisitorContext.getClassElements returns annotations
        transformed_any = False
        imports_java_package = False
        for alias in node.names:
            if alias.name == '*':
                # Handle star imports - scan the entire package
                if self._handle_star_import(java_module, transformed_module):
                    transformed_any = True
                elif java_io_package and not self.callback_get_class_elements(java_module):
                    self.validation_errors.append(unresolved_java_io_import_error(java_module, 'package'))
            else:
                # Handle specific imports
                if self._handle_specific_import(java_module, transformed_module, alias):
                    transformed_any = True
                elif java_io_package:
                    # ``from io.swagger.v3.oas import annotations as oas`` imports a Java package, not a class:
                    # the processor resolves ``oas.Operation`` through the import, which therefore stays.
                    if self._handle_package_import(f'{java_module}.{alias.name}'):
                        imports_java_package = True
                    elif not self._imports_compiled_python_class(java_module, alias.name):
                        self.validation_errors.append(self._java_io_import_error(node.module, java_module, alias))

        if transformed_any and not imports_java_package:
            # The generated decorators and java.type() assignments replace the import
            return None

        return node

    def visit_Expr(self, node: ast.Expr):
        if isinstance(node.value, ast.Call):
            function = node.value.func
            name = function.id if isinstance(function, ast.Name) else function.attr if isinstance(function, ast.Attribute) else ''
            if name in {'Dependency', 'MavenRepository', 'AppConfig'}:
                return None
        return self.generic_visit(node)

    def visit_Import(self, node: ast.Import):
        """
        Transform plain import statements like:
        import jakarta.inject as i
        import micronaut.context.annotation as a

        For such package-level imports, generate decorators for all annotation types
        found in the imported package so that alias-qualified usage like @i.Singleton
        or @a.Executable can be recognized at runtime without requiring the module to exist.
        If any decorators are generated, remove the import from the AST.
        """
        for alias in node.names:
            java_module_name = self._to_java_import_module(alias.name)
            # Scan the entire package for annotation types
            resolved = self._handle_package_import(java_module_name)
            if is_java_io_package(java_module_name):
                if not resolved:
                    self.validation_errors.append(unresolved_java_io_import_error(java_module_name, 'package'))
                elif not alias.asname:
                    self.validation_errors.append(
                        f"Java package import [import {alias.name}] requires an alias such as "
                        f"[import {alias.name} as {alias.name.split('.')[-1]}]: io is Python's built-in module, "
                        f"so the name io cannot refer to the Java package at runtime."
                    )

        return node

    def _handle_package_import(self, java_package_name: str) -> bool:
        """
        Generate decorators for every annotation type of a Java package imported as a module
        (``import io.micronaut.context.annotation as a``). Returns whether the package exists.
        """
        class_elements = self.callback_get_class_elements(java_package_name)
        if not class_elements:
            return False
        for class_element in class_elements:
            if self._is_annotation_class(class_element) and not self._is_nested_type(class_element):
                import_name = class_element.getSimpleName()
                decorator_code = self._generate_decorator_from_class_element(class_element, import_name)
                if decorator_code:
                    self.transformed_code.append(decorator_code)
        return True

    def _imports_compiled_python_class(self, java_module: str, import_name: str) -> bool:
        """
        Whether ``from <java_module> import <import_name>`` names the class generated for a Python class of
        another compilation (another source root, or a library): the import refers to the Python class at
        run time, so it stays a Python import and is not an unresolved Java import.
        """
        class_element = self._lookup_imported_class_element(java_module, import_name)
        return class_element is not None and _JavaTypes.isPythonClass(class_element)

    def _java_io_import_error(self, python_module: str, java_module: str, alias) -> str:
        """
        The error for ``from io.<x> import <name>`` that resolved to nothing: either the name is unknown on the
        classpath, or it is a Java annotation whose decorator name is already taken by another annotation
        imported earlier (``from micronaut.http.annotation import *`` followed by
        ``from io.swagger.v3.oas.annotations.headers import Header``), which needs an alias.
        """
        full_name = f'{java_module}.{alias.name}'
        variable_name = alias.asname or alias.name
        class_element = self._lookup_imported_class_element(java_module, alias.name)
        if class_element is not None and variable_name in self.generated_decorators:
            suggested_alias = f'{java_module.split(".")[1].capitalize()}{alias.name}'
            return (
                f"Java import [{full_name}] clashes with the decorator [{variable_name}] generated for another "
                f"Java annotation imported earlier in this module. Import it under an alias, such as "
                f"[from {python_module} import {alias.name} as {suggested_alias}]."
            )
        return unresolved_java_io_import_error(full_name)

    def visit_Module(self, node: ast.Module) -> ast.Module:
        """
        Process the entire module and add generated decorators and java.type assignments at the beginning.
        """
        self.scan_annotation_functions(node)
        # First visit all nodes to collect imports
        self.generic_visit(node)

        if self.strip_java_interface_bases:
            self._ensure_future_annotations(node)

        if self.uses_builtin_exception and not any(
            isinstance(statement, ast.Import)
            and any(alias.name == 'builtins' and alias.asname is None for alias in statement.names)
            for statement in node.body
        ):
            node.body.insert(
                self._generated_code_insert_index(node),
                ast.Import(names=[ast.alias(name='builtins', asname=None)])
            )

        # Add generated code at the beginning
        if (self.transformed_code or self.java_type_assignments or self.has_java_import
                or self.uses_builtin_exception or self.uses_java_interface_defaults):
            # Create AST nodes for the generated code
            generated_nodes = []
            generated_nodes.extend(self._java_interface_defaults_nodes())

            # Add import java statement if we have java.type() calls
            if self.has_java_import:
                java_import_code = "import java"
                try:
                    java_import_ast = ast.parse(java_import_code)
                    generated_nodes.extend(java_import_ast.body)
                except SyntaxError as e:
                    raise RuntimeError(f"Error parsing java import (generated code is not valid Python): {e}") from e

            # Add the micronaut_annotation function first
            if self.transformed_code:
                micronaut_annotation_code = '''
def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    """
    Decorator to mark functions as Micronaut annotations.
    """
    def decorator(target):
        return target
    return decorator
'''
                try:
                    micronaut_annotation_ast = ast.parse(micronaut_annotation_code)
                    generated_nodes.extend(micronaut_annotation_ast.body)
                except SyntaxError as e:
                    raise RuntimeError(f"Error parsing micronaut_annotation (generated code is not valid Python): {e}") from e

            # Add java.type() assignments
            for java_type_assignment in self.java_type_assignments:
                try:
                    java_type_ast = ast.parse(java_type_assignment)
                    generated_nodes.extend(java_type_ast.body)
                except SyntaxError as e:
                    raise RuntimeError(f"Error parsing java type assignment (generated code is not valid Python): {e}") from e

            # Add generated decorators. Their standalone snippets each carry
            # the helper prelude, but a combined source needs only one copy.
            micronaut_annotation_emitted = bool(self.transformed_code)
            for decorator_code in self.transformed_code:
                try:
                    # Parse the generated decorator code
                    decorator_ast = ast.parse(decorator_code)
                    for generated_node in decorator_ast.body:
                        if (isinstance(generated_node, ast.FunctionDef)
                                and generated_node.name == 'micronaut_annotation'):
                            if micronaut_annotation_emitted:
                                continue
                            micronaut_annotation_emitted = True
                        generated_nodes.append(generated_node)
                except SyntaxError as e:
                    raise RuntimeError(f"Error parsing generated decorator (generated code is not valid Python): {e}") from e
                    continue

            insert_at = self._generated_code_insert_index(node)
            node.body = node.body[:insert_at] + generated_nodes + node.body[insert_at:]

        return node

    def scan_annotation_functions(self, node: ast.Module) -> None:
        """
        Record the names the module binds to custom annotation functions before its imports are rewritten.
        """
        scanner = AnnotationFunctionScanner(self.callback_get_class_element, self.package_name, self.source_root)
        self.annotation_functions.update(scanner.scan(node))

    def _java_interface_defaults_nodes(self):
        """The decorator that adds the default methods of the implemented Java interfaces to a class."""
        if not self.uses_java_interface_defaults:
            return []
        return ast.parse(JAVA_INTERFACE_DEFAULTS_DECORATOR_CODE).body

    def _generated_code_insert_index(self, node: ast.Module) -> int:
        insert_at = 0
        if node.body and self._is_module_docstring(node.body[0]):
            insert_at = 1
        while insert_at < len(node.body) and self._is_future_import(node.body[insert_at]):
            insert_at += 1
        return insert_at

    def _ensure_future_annotations(self, node: ast.Module) -> None:
        if self._has_future_annotations(node):
            return

        future_import = ast.ImportFrom(
            module='__future__',
            names=[ast.alias(name='annotations', asname=None)],
            level=0
        )
        ast.fix_missing_locations(future_import)

        insert_at = 0
        if node.body and self._is_module_docstring(node.body[0]):
            insert_at = 1
        while insert_at < len(node.body) and self._is_future_import(node.body[insert_at]):
            insert_at += 1
        node.body.insert(insert_at, future_import)

    def _has_future_annotations(self, node: ast.Module) -> bool:
        return any(
            self._is_future_import(statement) and any(alias.name == 'annotations' for alias in statement.names)
            for statement in node.body
        )

    def _is_module_docstring(self, node: ast.AST) -> bool:
        if not isinstance(node, ast.Expr):
            return False
        value = node.value
        return isinstance(value, ast.Constant) and isinstance(value.value, str)

    def _is_future_import(self, node: ast.AST) -> bool:
        return isinstance(node, ast.ImportFrom) and node.module == '__future__'

    def visit_ClassDef(self, node: ast.ClassDef) -> ast.ClassDef:
        """
        Track all class definitions and exported types separately.
        """
        is_module_level_class = self.class_depth == 0 and self.function_depth == 0
        if is_module_level_class:
            self.all_class_names.append(node.name)
        if self.strip_java_interface_bases and node.bases:
            original_base_count = len(node.bases)
            runtime_bases = []
            java_interface_names = []
            replaced_throwable = False
            has_python_exception_base = any(
                isinstance(base, ast.Name)
                and base.id == 'Exception'
                and not self._is_java_throwable_base(base)
                for base in node.bases
            )
            for base in node.bases:
                java_interface_name = self._java_interface_base_name(base)
                if java_interface_name is not None:
                    java_interface_names.append(java_interface_name)
                    continue
                if self._is_java_throwable_base(base):
                    if not replaced_throwable and not has_python_exception_base:
                        runtime_bases.append(ast.copy_location(
                            ast.Attribute(
                                value=ast.Name(id='builtins', ctx=ast.Load()),
                                attr='Exception',
                                ctx=ast.Load()
                            ),
                            base
                        ))
                        replaced_throwable = True
                        self.uses_builtin_exception = True
                    continue
                java_class_name = self._java_class_name(base)
                if java_class_name:
                    node.body.insert(0, ast.Raise(
                        exc=ast.Call(
                            func=ast.Name(id='RuntimeError', ctx=ast.Load()),
                            args=[ast.Constant(
                                f"Native Python mode does not support Python class [{node.name}] "
                                f"extending Java class [{java_class_name}]; "
                                "use composition or a Java interface instead."
                            )],
                            keywords=[]
                        ),
                        cause=None
                    ))
                    continue
                runtime_bases.append(base)
            node.bases = runtime_bases
            if len(node.bases) != original_base_count:
                node.keywords = [
                    keyword
                    for keyword in node.keywords
                    if keyword.arg != "new_style"
                ]
            if java_interface_names:
                # The stripped interfaces leave the instances without the default methods of the Java
                # interfaces; the outermost decorator adds them to the class after every other decorator ran
                self.uses_java_interface_defaults = True
                node.decorator_list.insert(0, ast.copy_location(ast.Call(
                    func=ast.Name(id=JAVA_INTERFACE_DEFAULTS_DECORATOR, ctx=ast.Load()),
                    args=[ast.Constant(name) for name in java_interface_names],
                    keywords=[]
                ), node))
        if is_module_level_class and node.decorator_list:
            for decorator in node.decorator_list:
                decorator_name = self._get_decorator_name(decorator)
                if decorator_name in self.generated_decorators:
                    self.exported_types.append(node.name)
                    break
        node.decorator_list = [
            self._normalize_decorator(decorator)
            for decorator in node.decorator_list
        ]
        self.class_depth += 1
        try:
            self.generic_visit(node)
            return node
        finally:
            self.class_depth -= 1

    def visit_FunctionDef(self, node: ast.FunctionDef) -> ast.FunctionDef:
        node.decorator_list = [
            self._normalize_decorator(decorator)
            for decorator in node.decorator_list
        ]
        self.function_depth += 1
        try:
            self.generic_visit(node)
            return node
        finally:
            self.function_depth -= 1

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> ast.AsyncFunctionDef:
        if self.class_depth > 0 and self.function_depth == 0:
            if node.name == "__init__":
                self.validation_errors.append("Async constructors are not supported")
            if self._is_python_property_decorator(node):
                self.validation_errors.append(f"Async property [{node.name}] is not supported")
        node.decorator_list = [
            self._normalize_decorator(decorator)
            for decorator in node.decorator_list
        ]
        self.function_depth += 1
        try:
            self.generic_visit(node)
            return node
        finally:
            self.function_depth -= 1

    def _is_python_property_decorator(self, node) -> bool:
        for decorator in node.decorator_list:
            if isinstance(decorator, ast.Name) and decorator.id == "property":
                return True
            if (
                isinstance(decorator, ast.Attribute)
                and decorator.attr in ("setter", "deleter")
                and isinstance(decorator.value, ast.Name)
                and decorator.value.id == node.name
            ):
                return True
        return False

    def visit_Assign(self, node: ast.Assign):
        """
        Track direct java.type() aliases so Java interface bases can be stripped
        from runtime Python classes before GraalPy creates host adapters.
        """
        if self._track_java_type_assignment(node):
            return None
        return self.generic_visit(node)

    def visit_If(self, node: ast.If):
        """
        Keep a conditional block valid when every statement in it was a transformed import
        (``if TYPE_CHECKING: from a.b import JavaType``).
        """
        self.generic_visit(node)
        if not node.body:
            node.body = [ast.copy_location(ast.Pass(), node)]
        return node

    def visit_Attribute(self, node: ast.Attribute):
        self.generic_visit(node)
        java_method_name = self._java_keyword_method_name(node)
        if java_method_name is None:
            return node

        owner_name = self._base_name(node.value)
        if owner_name is not None:
            self.java_keyword_safe_imports.add(owner_name)

        return ast.copy_location(
            ast.Call(
                func=ast.Name(id='getattr', ctx=ast.Load()),
                args=[node.value, ast.Constant(java_method_name)],
                keywords=[]
            ),
            node
        )

    def _get_decorator_name(self, decorator) -> Optional[str]:
        """
        Extract the decorator name from an AST decorator node.
        """
        if isinstance(decorator, ast.Name):
            return decorator.id
        elif isinstance(decorator, ast.Call):
            if isinstance(decorator.func, ast.Name):
                return decorator.func.id
        elif isinstance(decorator, ast.Attribute):
            # If attribute corresponds to a generated decorator (e.g. @a.Executable),
            # return the attribute name to match generated decorator function name.
            if hasattr(decorator, 'attr') and decorator.attr in self.generated_decorators:
                return decorator.attr
            # Handle decorated decorators like @micronaut_annotation("...")
            if isinstance(decorator.value, ast.Name) and decorator.value.id in self.generated_decorators:
                return decorator.value.id
        return None

    def _normalize_decorator(self, decorator):
        decorator = self._normalize_bare_annotation_decorator(decorator)
        if isinstance(decorator, ast.Call) and self._is_generated_annotation_call(decorator):
            self._normalize_annotation_keyword_arguments(decorator)
        return decorator

    def _is_generated_annotation_call(self, call: ast.Call) -> bool:
        if isinstance(call.func, ast.Name):
            return call.func.id in self.generated_decorators
        if isinstance(call.func, ast.Attribute):
            return call.func.attr in self.generated_decorators
        return False

    def _normalize_bare_annotation_decorator(self, decorator):
        """
        Convert a bare @Annotation to @Annotation(): the syntactic form of the decorator decides whether it is
        applied bare or called as a factory, so a generated decorator or a custom annotation function is always
        called, and a single positional argument is always an annotation value.
        """
        if isinstance(decorator, ast.Call):
            return decorator
        if self._is_annotation_decorator(decorator):
            call = ast.Call(func=decorator, args=[], keywords=[])
            return ast.copy_location(call, decorator)
        return decorator

    def _is_annotation_decorator(self, decorator) -> bool:
        """
        Whether a bare decorator names a generated annotation decorator or a custom annotation function that
        a bare application does not pass the target to.
        """
        decorator_name = self._get_decorator_name(decorator)
        if decorator_name in self.generated_decorators:
            return True
        return decorator_name in self.annotation_functions and not self.annotation_functions[decorator_name]

    def _normalize_annotation_keyword_arguments(self, call: ast.Call):
        """
        Rewrite decorator keyword aliases such as global_=True to **{"global": True}
        so generated runtime Python sources remain syntactically valid.
        """
        normalized_keywords = []
        for kw in call.keywords:
            if kw.arg is None:
                normalized_keywords.append(kw)
                continue

            normalized_name = normalize_python_keyword_alias(kw.arg)
            if normalized_name == kw.arg:
                normalized_keywords.append(kw)
                continue

            expansion = ast.keyword(
                arg=None,
                value=ast.Dict(
                    keys=[ast.Constant(normalized_name)],
                    values=[kw.value]
                )
            )
            normalized_keywords.append(expansion)
        call.keywords = normalized_keywords

    def _handle_specific_import(self, original_module_name: str, transformed_module_name: str, alias) -> bool:
        """
        Handle specific imports like 'from jakarta.inject import Singleton' or 'from jakarta.inject import Singleton as S'.
        The module may also name a Java type: 'from a.b.Outer import Inner' imports a nested type and
        'from a.b.Outer import Outer' the type itself.
        Returns True if the import was transformed.
        """
        import_name = alias.name  # The actual name being imported (e.g., "Singleton")
        variable_name = alias.asname if alias.asname else alias.name  # The name to use for the variable (e.g., "S" or "Singleton")

        class_element = self._resolve_imported_java_type(original_module_name, import_name)
        if class_element is None or _JavaTypes.isPythonClass(class_element):
            # The bridge of a Python class compiled by another source root or into a library:
            # the import refers to the Python class at run time, so it stays a Python import.
            return False
        if self._is_annotation_class(class_element):
            # Generate decorator for annotations
            decorator_code = self._generate_decorator_from_class_element(class_element, variable_name)
            if decorator_code:
                self.transformed_code.append(decorator_code)
                return True
            # The same annotation was already imported under this name
            return class_element.getName() in self.generated_decorator_code
        self._track_java_class(variable_name, class_element)
        # Collect Java class import for VFS generation
        self._collect_java_class_import(
            transformed_module_name,
            import_name,
            variable_name,
            class_element
        )
        # Generate java.type() assignment for regular Java types
        java_type_assignment = f"{variable_name} = java.type('{class_element.getName()}')"
        self.java_type_assignments.append(java_type_assignment)
        self.has_java_import = True
        return True

    def _resolve_imported_java_type(self, module_name: str, import_name: str):
        """
        The Java type a 'from module import name' statement imports, or None when the import is not a Java
        type. A module that itself names a Java type exports the type's nested types and, under its own
        simple name, the type itself; naming anything else there is an error, because no Python module can
        provide it at run time.
        """
        class_element = self._lookup_imported_class_element(module_name, import_name)
        if class_element is not None:
            return class_element
        outer_element = self._java_type_module(module_name)
        if outer_element is None:
            return None
        if import_name == module_name.rsplit('.', 1)[-1]:
            return outer_element
        self.validation_errors.append(
            f"Cannot import [{import_name}] from [{module_name}]: Java type [{outer_element.getName()}] "
            f"has no nested type named [{import_name}]"
        )
        return None

    def _lookup_imported_class_element(self, java_module: str, import_name: str):
        """
        The ClassElement named by ``from <java_module> import <import_name>``, accepting the Python
        snake_case spelling of the Java name (``singleton`` for ``Singleton``) as well.
        """
        class_element = self.callback_get_class_element(f"{java_module}.{import_name}")
        if class_element is None:
            alt_name = self._to_python_case(import_name)
            if alt_name != import_name:
                class_element = self.callback_get_class_element(f"{java_module}.{alt_name}")
        return class_element

    def _java_type_module(self, module_name: str):
        """
        The Java type named by the module of a from-import, or None when the module is a package or a Python
        module. A class generated from a Python source is not a Java type module.
        """
        class_element = self.callback_get_class_element(module_name)
        if class_element is None or _JavaTypes.isPythonClass(class_element):
            return None
        return class_element

    def _is_nested_type(self, class_element) -> bool:
        return '$' in str(class_element.getName())

    def _track_java_type_assignment(self, node: ast.Assign):
        if len(node.targets) != 1 or not isinstance(node.targets[0], ast.Name):
            return False
        class_name = self._java_type_name(node.value)
        if not class_name:
            return False
        variable_name = node.targets[0].id
        self._track_java_keyword_method_aliases(variable_name)
        class_element = self.callback_get_class_element(class_name)
        if class_element:
            if self._is_annotation_class(class_element):
                decorator_code = self._generate_decorator_from_class_element(class_element, variable_name)
                if decorator_code:
                    self.transformed_code.append(decorator_code)
                    return True
                return variable_name in self.generated_decorators
            self._track_java_class(variable_name, class_element)
        return False

    def _track_java_class(self, variable_name: str, class_element):
        self.java_class_elements[variable_name] = class_element
        if class_element.isInterface():
            self.java_interface_names.add(variable_name)
        self._track_java_keyword_method_aliases(variable_name)

    def _track_java_keyword_method_aliases(self, variable_name: str):
        self.java_keyword_method_aliases[variable_name] = PYTHON_KEYWORD_METHOD_ALIASES

    def _java_keyword_method_name(self, node: ast.Attribute) -> Optional[str]:
        if not isinstance(node.ctx, ast.Load):
            return None
        if not node.attr.endswith('_'):
            return None

        method_name = node.attr[:-1]
        if not keyword.iskeyword(method_name):
            return None

        owner_name = self._base_name(node.value)
        if owner_name is None:
            return None

        aliases = self.java_keyword_method_aliases.get(owner_name)
        if aliases is None:
            return None

        return aliases.get(node.attr)

    def _is_java_interface_base(self, base: ast.AST) -> bool:
        return self._java_interface_base_name(base) is not None

    def _java_interface_base_name(self, base: ast.AST) -> Optional[str]:
        """The Java name of a base that is a Java interface, or None for any other base."""
        class_name = self._java_type_name(base)
        if class_name:
            class_element = self.callback_get_class_element(class_name)
            if class_element:
                return class_element.getName() if class_element.isInterface() else None
        base_name = self._base_name(base)
        if base_name in self.java_interface_names:
            return self.java_class_elements[base_name].getName()
        return None

    def _is_java_throwable_base(self, base: ast.AST) -> bool:
        """Strip Java Throwable bases from native runtime bytecode.

        GraalPy native cannot create a Python subclass of a concrete Java
        Throwable. The compile-time model still keeps the Java base so
        Micronaut metadata and handler matching remain unchanged; only the
        executable runtime class is made a regular Python exception.
        """
        class_name = self._java_type_name(base)
        class_element = self.callback_get_class_element(class_name) if class_name else None
        if class_element is None:
            base_name = self._base_name(base)
            if not base_name:
                return False
            class_element = self.java_class_elements.get(base_name)
        return _JavaTypes.isThrowable(class_element)

    def _java_class_name(self, base: ast.AST) -> Optional[str]:
        class_name = self._java_type_name(base)
        class_element = self.callback_get_class_element(class_name) if class_name else None
        if class_element is None:
            base_name = self._base_name(base)
            class_element = self.java_class_elements.get(base_name) if base_name else None
        return class_element.getName() if _JavaTypes.isConcreteClass(class_element) else None

    def _java_type_name(self, node: ast.AST) -> Optional[str]:
        if not isinstance(node, ast.Call):
            return None
        func = node.func
        if not (
            isinstance(func, ast.Attribute)
            and func.attr == "type"
            and isinstance(func.value, ast.Name)
            and func.value.id == "java"
        ):
            return None
        if len(node.args) != 1:
            return None
        arg = node.args[0]
        if isinstance(arg, ast.Constant) and isinstance(arg.value, str):
            return arg.value
        return None

    def _base_name(self, base: ast.AST) -> Optional[str]:
        if isinstance(base, ast.Subscript):
            return self._base_name(base.value)
        if isinstance(base, ast.Name):
            return base.id
        if isinstance(base, ast.Attribute):
            parts = []
            current = base
            while isinstance(current, ast.Attribute):
                parts.insert(0, current.attr)
                current = current.value
            if isinstance(current, ast.Name):
                parts.insert(0, current.id)
            return ".".join(parts)
        return None

    def _handle_star_import(self, original_module_name: str, transformed_module_name: str) -> bool:
        """
        Handle star imports like 'from jakarta.inject import *'
        Returns True if any imports were transformed.
        """
        # Get all ClassElements in the package
        class_elements = self.callback_get_class_elements(original_module_name)
        if class_elements:
            transformed_any = False
            for class_element in class_elements:
                # A package scan also lists nested types; a star import binds top-level names only
                if self._is_annotation_class(class_element) and not self._is_nested_type(class_element):
                    import_name = class_element.getSimpleName()
                    decorator_code = self._generate_decorator_from_class_element(class_element, import_name)
                    if decorator_code:
                        self.transformed_code.append(decorator_code)
                        transformed_any = True
            return transformed_any

        return False

    def _is_annotation_class(self, class_element) -> bool:
        """
        Whether a ClassElement is an annotation type; answered by the Java side.
        """
        return _AnnotationTypes.isAnnotationType(class_element)

    def _targets_annotation_type(self, class_element) -> bool:
        """
        Whether the annotation type may be placed on other annotation types; answered by the Java side.
        """
        return _AnnotationTypes.targetsAnnotationType(class_element)

    def _generate_decorator_from_class_element(self, class_element, import_name: str) -> Optional[str]:
        """
        Generate the Python decorator standing for a Java annotation type, including decorators for its
        meta-annotations, and register it under the annotation's own name.
        """
        annotation_name = class_element.getName()
        return self._generate_decorator(class_element, import_name, annotation_name, with_meta_annotations=True)

    def _generate_decorator_from_class_element_with_name(self, class_element, import_name: str, custom_annotation_name: str) -> Optional[str]:
        """
        Generate the decorator for an annotation type referenced from another annotation's members, registered
        under the name that member uses; meta-annotations are not mirrored on it.
        """
        return self._generate_decorator(class_element, import_name, custom_annotation_name, with_meta_annotations=False)

    def _generate_decorator(self, class_element, decorator_name: str, annotation_name: str, with_meta_annotations: bool) -> Optional[str]:
        if decorator_name in self.generated_decorators:
            return None
        self.generated_decorators.add(decorator_name)

        annotation_metadata = class_element.getAnnotationMetadata()
        repeatable_name = self._get_repeatable_name(annotation_metadata, class_element)
        repeatable_info = f', repeated="{repeatable_name}"' if repeatable_name else ''
        annotation_target_info = ', annotationTypeTarget=True' if self._targets_annotation_type(class_element) else ''
        param_signature = self._get_annotation_parameters(class_element)['signature']

        decorator_lines = [f'@micronaut_annotation("{annotation_name}"{repeatable_info}{annotation_target_info})']
        nested_members_prelude, nested_members_code, nested_member_names = self._generate_nested_members_sections(class_element, decorator_name)
        import_lines = []
        if with_meta_annotations:
            meta_annotations = self._meta_annotations(class_element, annotation_name, repeatable_name, decorator_name, nested_member_names)
            for meta_annotation_name, meta_class_element, meta_decorator_name in meta_annotations:
                if meta_decorator_name not in self.generated_decorators:
                    meta_decorator_code = self._generate_decorator_from_class_element(meta_class_element, meta_decorator_name)
                    if meta_decorator_code:
                        self.transformed_code.append(meta_decorator_code)
                decorator_lines.append(f'@{meta_decorator_name}()')
            import_lines = self._meta_annotation_imports(meta_annotations)

        exported_decorator_name = str(class_element.getSimpleName()).split('$')[-1]
        export_alias = (
            f'\n{exported_decorator_name} = {decorator_name}\n'
            if exported_decorator_name != decorator_name else ''
        )
        decorator_code = self._decorator_source(
            import_lines, nested_members_prelude, decorator_lines, decorator_name, annotation_name,
            param_signature, nested_members_code, export_alias,
            self._bare_target_guard(class_element, decorator_name)
        )
        self.generated_decorator_code[annotation_name] = decorator_code
        self._generate_nested_decorators(class_element, decorator_name)
        return decorator_code

    def _meta_annotations(self, class_element, annotation_name: str, repeatable_name, decorator_name: str, nested_member_names):
        """
        The meta-annotations of an annotation type the processor can resolve, as (name, element, decorator name).
        """
        meta_annotations = []
        for meta_annotation_name in class_element.getAnnotationMetadata().getAnnotationNames():
            if repeatable_name and self._same_annotation_name(meta_annotation_name, repeatable_name):
                continue
            if self._skip_meta_annotation_in_source(meta_annotation_name):
                continue
            meta_class_element = self.callback_get_class_element(meta_annotation_name)
            if not meta_class_element or not self._is_annotation_class(meta_class_element):
                continue
            meta_decorator_name = self._meta_decorator_name(meta_annotation_name, annotation_name, decorator_name, meta_class_element)
            if meta_decorator_name == decorator_name and meta_annotation_name != annotation_name:
                continue
            if '$' in meta_annotation_name and meta_decorator_name not in nested_member_names:
                continue
            meta_annotations.append((meta_annotation_name, meta_class_element, meta_decorator_name))
        return meta_annotations

    def _meta_annotation_imports(self, meta_annotations):
        import_lines = set()
        for meta_annotation_name, _, _ in meta_annotations:
            if '$' in meta_annotation_name:
                continue
            meta_package = '.'.join(meta_annotation_name.split('.')[:-1])
            meta_simple_name = meta_annotation_name.split('.')[-1]
            # Transform io. prefixed packages to avoid conflict with Python's builtin io module
            import_package = self._to_python_import_module(meta_package)
            if import_package.startswith('io.'):
                import_package = import_package[3:]
            # Import from the concrete annotation module so duplicate VFS package
            # roots cannot resolve the package member as a module object.
            import_lines.add(f"from {import_package}.{meta_simple_name} import {meta_simple_name}")
        return sorted(import_lines)

    @staticmethod
    def _bare_target_guard(class_element, decorator_name: str) -> str:
        """
        The check a generated factory makes when its single positional argument is a class or a function: unless
        the annotation's ``value`` member holds a class, the factory was applied bare through a name the
        transformer could not recognise (``Bean = Singleton`` in another module, ``getattr``), and the target
        would silently become the inner decorator. A nested annotation value, the inner ``decorator`` of another
        factory, is not a target.
        """
        value_type = _AnnotationTypes.valueMemberTypeName(class_element)
        if value_type is not None and value_type.startswith('java.lang.Class'):
            return ''
        return f'''
    if len(args) == 1 and not kwargs and (isinstance(args[0], type) or (
            hasattr(args[0], '__code__') and not getattr(args[0], '__qualname__', '').endswith('.decorator'))):
        raise TypeError(
            f"@{decorator_name} was applied bare to {{args[0]!r}} through a name the compiler did not recognise "
            f"as the annotation; write @{decorator_name}() or apply it under the name it is imported as")'''

    @staticmethod
    def _decorator_source(import_lines, nested_members_prelude, decorator_lines, decorator_name, annotation_name,
                          param_signature, nested_members_code, export_alias, bare_target_guard='') -> str:
        """
        The Python source of a generated decorator. The ``micronaut_annotation`` shim is repeated in every
        snippet so each one can be evaluated on its own. The decorator is a factory: every argument is an
        annotation value, a bare ``@Foo`` having been rewritten to ``@Foo()`` by the transformer; a bare
        application that escaped the rewrite is reported by the guard.
        """
        imports_section = '\n'.join(import_lines) + '\n\n' if import_lines else ''
        return f'''
{imports_section}def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    """
    Decorator to mark functions as Micronaut annotations.
    """
    def decorator(func):
        return func
    return decorator

{nested_members_prelude}
{chr(10).join(decorator_lines)}
def {decorator_name}({param_signature}):
    """
    Micronaut annotation decorator for {annotation_name}.
    """{bare_target_guard}
    def decorator(target):
        return target

    return decorator
{nested_members_code}
{export_alias}
'''

    def _skip_meta_annotation_in_source(self, meta_annotation_name: str) -> bool:
        return (
            meta_annotation_name in META_ANNOTATIONS_TO_SKIP_IN_SOURCE
            or meta_annotation_name.startswith(META_ANNOTATION_PACKAGES_TO_SKIP_IN_SOURCE)
        )

    def _same_annotation_name(self, left: str, right: str) -> bool:
        return left == right or left.replace('$', '.') == right.replace('$', '.')

    def _get_annotation_parameters(self, class_element) -> Dict[str, str]:
        """
        Analyze annotation class to determine parameters and generate function signature.
        """
        # For now, provide a flexible signature that can handle most cases
        # In a full implementation, this would analyze the annotation methods
        return {
            'signature': '*args, **kwargs',
            'handling': '''
        annotation_data['args'] = args
        annotation_data['kwargs'] = kwargs
'''
        }

    def _get_repeatable_name(self, annotation_metadata, class_element) -> Optional[str]:
        """
        The container annotation name of a repeatable annotation, or None; answered by the Java side.
        """
        return _AnnotationTypes.repeatableContainerName(class_element)

    def _generate_nested_decorators(self, class_element, parent_name: str):
        """
        Generate decorators for the annotation types returned by the annotation's members.
        """
        own_nested_prefix = str(class_element.getName()) + '$'
        for return_type_name in _AnnotationTypes.memberReturnTypeNames(class_element):
            nested_annotation_element = self.callback_get_class_element(return_type_name)
            if not nested_annotation_element or not self._is_annotation_class(nested_annotation_element):
                continue
            # Skip annotations nested inside the current annotation; they are handled as nested members.
            nested_name = str(nested_annotation_element.getName())
            if nested_name.startswith(own_nested_prefix):
                continue
            if '$' in nested_name:
                annotation_simple_name = nested_name.split('$')[-1]
            else:
                annotation_simple_name = nested_annotation_element.getSimpleName()
            if annotation_simple_name in self.generated_decorators:
                continue
            decorator_code = self._generate_decorator_from_class_element_with_name(
                nested_annotation_element, annotation_simple_name, nested_name)
            if decorator_code:
                self.transformed_code.append(decorator_code)

    def _generate_nested_members_sections(self, class_element, parent_name: str):
        """
        Generate Python definitions and post-definition assignments for Java nested types
        exposed through an annotation.
        """
        prelude_lines = []
        lines = []
        nested_member_names = set()
        needs_java = False
        for nested in self._get_nested_types(class_element):
            nested_name = nested.name()
            simple_name = nested.simpleName()
            nested_member_names.add(simple_name)
            if nested.annotation():
                repeatable_name = nested.repeatableName()
                repeatable_info = f', repeated="{repeatable_name}"' if repeatable_name else ''
                nested_decorator_name = f"_{parent_name}_{simple_name}"
                nested_member_names.add(nested_decorator_name)
                self.generated_decorators.add(nested_decorator_name)
                prelude_lines.append(f'''

@micronaut_annotation("{nested_name}"{repeatable_info})
def {nested_decorator_name}(*args, **kwargs):
    """
    Micronaut annotation decorator for {nested_name}.
    """
    def decorator(target):
        return target

    return decorator
''')
                lines.append(f'''

{simple_name} = {nested_decorator_name}
{parent_name}.{simple_name} = {nested_decorator_name}
''')
            else:
                binary_name = self._to_binary_nested_name(class_element.getName(), nested_name)
                needs_java = True
                lines.append(f'''
try:
    {simple_name} = java.type("{binary_name}")
except Exception:
    {simple_name} = None
{parent_name}.{simple_name} = {simple_name}
''')

        if needs_java:
            lines.insert(0, "\nimport java\n")
        return ''.join(prelude_lines), ''.join(lines), nested_member_names

    def _meta_decorator_name(self, meta_annotation_name: str, annotation_name: str, decorator_name: str, meta_class_element) -> str:
        if meta_annotation_name.startswith(annotation_name + '$'):
            return f"_{decorator_name}_{meta_annotation_name.split('$')[-1]}"
        if '$' in meta_annotation_name:
            return meta_annotation_name.split('$')[-1]
        return meta_class_element.getSimpleName()

    def _get_nested_types(self, class_element):
        """
        The types nested in an annotation, each with the facts the generated members need; one
        Java call instead of a walk over the annotation's members from Python.
        """
        try:
            return list(_AnnotationTypes.nestedTypes(class_element, self.callback_get_class_element))
        except Exception as e:
            warnings.warn(f"Error generating nested members for {class_element.getName()}: {e}")
            return []

    def _to_binary_nested_name(self, parent_name: str, nested_name: str) -> str:
        if '$' in nested_name:
            return nested_name
        prefix = parent_name + '.'
        if nested_name.startswith(prefix):
            return parent_name + '$' + nested_name[len(prefix):]
        return nested_name

    def _collect_java_class_import(self, package_name: str, import_name: str, variable_name: str, class_element):
        """
        Collect Java class import for VFS generation.
        """
        if package_name not in self.java_class_imports:
            self.java_class_imports[package_name] = []

        is_interface = class_element.isInterface()
        self.java_class_imports[package_name].append({
            'variable': import_name,
            'usage_variable': variable_name,
            'class_name': class_element.getName(),
            'interface': str(is_interface).lower()
        })

    def get_java_class_imports(self):
        """
        Get Java imports and mark the types that need Python keyword method aliases.
        """
        for imports in self.java_class_imports.values():
            for import_info in imports:
                variable_name = import_info['usage_variable']
                import_info['keyword_safe'] = str(variable_name in self.java_keyword_safe_imports).lower()
        return self.java_class_imports

    def get_missing_runtime_decorator_code(self, tree: ast.Module):
        """
        Return compatibility stubs for generated decorators used without an import.
        """
        bound_names = set()
        missing_names = set()
        for statement in tree.body:
            for child in ast.walk(statement):
                decorator_lists = []
                if isinstance(child, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
                    decorator_lists.append(child.decorator_list)
                for decorators in decorator_lists:
                    for decorator in decorators:
                        decorator_name = self._get_decorator_name(decorator)
                        if decorator_name in self.generated_decorators and decorator_name not in bound_names:
                            missing_names.add(decorator_name)
            bound_names.update(self._statement_bound_names(statement))

        missing_code = []
        for decorator_name in missing_names:
            definition = f'def {decorator_name}('
            for decorator_code in self.generated_decorator_code.values():
                if definition in decorator_code:
                    missing_code.append(decorator_code)
                    break
        return missing_code

    def _statement_bound_names(self, statement: ast.AST):
        names = set()
        if isinstance(statement, ast.ImportFrom):
            names.update(
                alias.asname if alias.asname else alias.name
                for alias in statement.names
                if alias.name != '*'
            )
        elif isinstance(statement, ast.Import):
            names.update(
                alias.asname if alias.asname else alias.name.split('.')[0]
                for alias in statement.names
            )
        elif isinstance(statement, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            names.add(statement.name)
        elif isinstance(statement, (ast.Assign, ast.AnnAssign)):
            targets = statement.targets if isinstance(statement, ast.Assign) else [statement.target]
            for target in targets:
                if isinstance(target, ast.Name):
                    names.add(target.id)
        return names

    def _to_java_import_module(self, module_name: str) -> str:
        """
        Convert Python-safe package segments such as async_ back to Java package segments.
        """
        return '.'.join(
            part[:-1] if part.endswith('_') and keyword.iskeyword(part[:-1]) else part
            for part in module_name.split('.')
        )

    def _to_python_import_module(self, module_name: str) -> str:
        """
        Convert Java package segments that are Python keywords to importable Python segments.
        """
        return '.'.join(
            f'{part}_' if keyword.iskeyword(part) else part
            for part in module_name.split('.')
        )

    def _to_python_case(self, java_name: str) -> str:
        """
        Convert Java PascalCase to Python snake_case.
        """
        # General conversion: PascalCase to snake_case
        s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', java_name)
        return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()



    def get_generated_decorator_code(self) -> Dict[str, str]:
        """
        Get the generated decorator code as a dictionary mapping decorator name to code.
        """
        return self.generated_decorator_code

    def get_exported_types(self) -> List[str]:
        """
        Get the list of types (classes/functions) that have Micronaut decorators.
        """
        return self.exported_types


def ast_equal(left, right) -> bool:
    """
    Structural equality of two trees, ignoring positions, with early exit.

    Equivalent to comparing ``ast.dump(tree, include_attributes=False)`` output, but a difference
    near the top of the module (the usual case: a rewritten import) is found immediately instead
    of after serialising both trees.
    """
    if type(left) is not type(right):
        return False
    if isinstance(left, ast.AST):
        for field in left._fields:
            if not ast_equal(getattr(left, field, None), getattr(right, field, None)):
                return False
        return True
    if isinstance(left, list):
        if len(left) != len(right):
            return False
        return all(ast_equal(a, b) for a, b in zip(left, right))
    return left == right


class MicronautRuntimeTransformer(MicronautTransformer):
    """
    Minimal runtime-only compatibility transformer.

    Generated VFS modules provide imported annotations and Java types. This
    transformer handles only constructs that cannot be represented by those
    imports while retaining the original locations on user AST nodes.
    """

    def __init__(self, callback_get_class_element, callback_get_class_elements, missing_decorator_code=None,
                 package_name='', source_root=''):
        super().__init__(callback_get_class_element, callback_get_class_elements, True, package_name, source_root)
        if missing_decorator_code:
            self.transformed_code.extend(missing_decorator_code)
            # The stubs define generated decorators the module applies without importing them
            for decorator_code in missing_decorator_code:
                for statement in ast.parse(decorator_code).body:
                    if isinstance(statement, ast.FunctionDef) and statement.name != 'micronaut_annotation':
                        self.generated_decorators.add(statement.name)
                    elif isinstance(statement, ast.Assign):
                        self.generated_decorators.update(
                            target.id for target in statement.targets if isinstance(target, ast.Name)
                        )
        self.java_runtime_names = set()
        self.imported_java_interface_names = set()
        # The annotation names of the Java packages imported as modules, by the name the package is bound to
        # (``import jakarta.inject as i`` -> ``i``): applied qualified, as @i.Singleton, never bare
        self.package_decorators: Dict[str, Set[str]] = {}
        # The names the module defines or assigns itself: they shadow the names a star import binds
        self.locally_bound_names: Set[str] = set()
        # The names bound by the statements following each star import (by the id of the import statement): as
        # in Python, a star import rebinds the names defined before it and is shadowed by those defined after it
        self.names_bound_after_star_import: Dict[int, Set[str]] = {}

    def visit_Module(self, node: ast.Module) -> ast.Module:
        self.scan_annotation_functions(node)
        bound_after: Set[str] = set()
        for statement in reversed(node.body):
            if isinstance(statement, ast.ImportFrom) and any(alias.name == '*' for alias in statement.names):
                self.names_bound_after_star_import[id(statement)] = set(bound_after)
            elif not isinstance(statement, (ast.Import, ast.ImportFrom)):
                bound_after.update(self._statement_bound_names(statement))
        self.locally_bound_names.update(bound_after)
        self.generic_visit(node)

        if self._has_java_annotations(node):
            self._ensure_future_annotations(node)

        if self.uses_builtin_exception and not any(
            isinstance(statement, ast.Import)
            and any(alias.name == 'builtins' and alias.asname is None for alias in statement.names)
            for statement in node.body
        ):
            node.body.insert(
                self._generated_code_insert_index(node),
                ast.Import(names=[ast.alias(name='builtins', asname=None)])
            )

        if not (self.transformed_code or self.java_type_assignments or self.uses_builtin_exception
                or self.uses_java_interface_defaults):
            return node

        generated_nodes = list(self._java_interface_defaults_nodes())
        if self.transformed_code:
            generated_nodes.extend(ast.parse('''
def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(target):
        return target
    return decorator
''').body)

        micronaut_annotation_emitted = bool(self.transformed_code)
        for decorator_code in self.transformed_code:
            for generated_node in ast.parse(decorator_code).body:
                if (isinstance(generated_node, ast.FunctionDef)
                        and generated_node.name == 'micronaut_annotation'):
                    if micronaut_annotation_emitted:
                        continue
                    micronaut_annotation_emitted = True
                generated_nodes.append(generated_node)

        insert_at = self._generated_code_insert_index(node)
        node.body = node.body[:insert_at] + generated_nodes + node.body[insert_at:]
        return node

    def visit_ImportFrom(self, node: ast.ImportFrom):
        if not node.module:
            return node
        if node.module == 'pyronaut.build':
            return None

        java_module = self._to_java_import_module(node.module)
        for alias in node.names:
            variable_name = alias.asname if alias.asname else alias.name
            if alias.name == '*':
                # the annotations of the package are bound to their own names, unless the module binds a name itself
                # after the import (a star import nested in a block counts every name the module binds)
                shadowed = self.names_bound_after_star_import.get(id(node), self.locally_bound_names)
                self.generated_decorators.update(self._package_annotation_names(java_module) - shadowed)
                continue
            class_element = self._resolve_imported_java_type(java_module, alias.name)
            if class_element is None:
                # from jakarta import inject: the decorators of the package are applied as inject.Singleton
                self._track_package_decorators(variable_name, f'{java_module}.{alias.name}')
            elif self._is_annotation_class(class_element):
                self.generated_decorators.add(variable_name)
            elif not _JavaTypes.isPythonClass(class_element):
                self._track_java_class(variable_name, class_element)
                self.java_runtime_names.add(variable_name)
                if class_element.isInterface():
                    self.imported_java_interface_names.add(variable_name)

        if node.level == 0 and is_java_io_package(java_module):
            # (a relative ``from .io.util import helper`` names an application sub-package, never Java)
            transformed_module = strip_java_io_prefix(self._to_python_import_module(java_module))
            return ast.copy_location(
                ast.ImportFrom(module=transformed_module, names=node.names, level=node.level),
                node
            )
        return node

    def visit_Import(self, node: ast.Import):
        """
        Record the annotation types of imported Java packages and rewrite
        ``import io.micronaut.context.annotation as a`` (any Java ``io.*`` package) to the generated
        runtime package, which lives without the ``io.`` prefix.
        """
        for alias in node.names:
            # import jakarta.inject as i -> @i.Singleton; import jakarta.inject -> @jakarta.inject.Singleton
            self._track_package_decorators(alias.asname or alias.name, self._to_java_import_module(alias.name))
        if not any(is_java_io_package(alias.name) for alias in node.names):
            return node
        names = [
            ast.alias(name=strip_java_io_prefix(alias.name), asname=alias.asname)
            if is_java_io_package(alias.name) else alias
            for alias in node.names
        ]
        return ast.copy_location(ast.Import(names=names), node)

    def _track_package_decorators(self, bound_name: str, java_module: str):
        """
        Record the annotation types of an imported package under the name the package is bound to: they are
        applied as attributes of that name, so an unrelated local decorator of the same simple name is not one.
        """
        names = self._package_annotation_names(java_module)
        if names:
            self.package_decorators.setdefault(bound_name, set()).update(names)

    def _package_annotation_names(self, java_module: str) -> Set[str]:
        return {
            str(class_element.getSimpleName())
            for class_element in self.callback_get_class_elements(java_module) or []
            if self._is_annotation_class(class_element)
        }

    def _is_annotation_decorator(self, decorator) -> bool:
        if isinstance(decorator, ast.Attribute):
            qualifier = dotted_name(decorator.value)
            if qualifier in self.package_decorators:
                return decorator.attr in self.package_decorators[qualifier]
            # @Outer.Inner, a nested annotation of a generated decorator bound by name; never the attribute alone
            return isinstance(decorator.value, ast.Name) and decorator.value.id in self.generated_decorators
        return super()._is_annotation_decorator(decorator)

    def _normalize_decorator(self, decorator):
        # The runtime source keeps its keyword arguments as written: generated decorators accept them all.
        return self._normalize_bare_annotation_decorator(decorator)

    def visit_ClassDef(self, node: ast.ClassDef) -> ast.ClassDef:
        has_imported_interface_base = any(
            self._base_name(base) in self.imported_java_interface_names
            for base in node.bases
        )
        transformed = super().visit_ClassDef(node)
        if has_imported_interface_base:
            transformed.keywords = [
                class_keyword
                for class_keyword in transformed.keywords
                if class_keyword.arg != 'new_style'
            ]
        return transformed

    def visit_Assign(self, node: ast.Assign):
        variable_name = self._java_type_assignment_name(node)
        if variable_name is not None:
            class_name = self._java_type_name(node.value)
            class_element = self.callback_get_class_element(class_name)
            if not class_element or not self._is_annotation_class(class_element):
                self.java_runtime_names.add(variable_name)
        elif isinstance(node.value, ast.Name) and node.value.id in self.generated_decorators:
            # Bean = Singleton: the alias is the same factory, a bare @Bean is @Bean()
            self.generated_decorators.update(target.id for target in node.targets if isinstance(target, ast.Name))
        return super().visit_Assign(node)

    def visit_Expr(self, node: ast.Expr):
        if isinstance(node.value, ast.Call):
            function = node.value.func
            name = function.id if isinstance(function, ast.Name) else function.attr if isinstance(function, ast.Attribute) else ''
            if name in {'Dependency', 'MavenRepository', 'AppConfig'}:
                return None
        return self.generic_visit(node)

    def _java_type_assignment_name(self, node: ast.Assign) -> Optional[str]:
        if len(node.targets) != 1 or not isinstance(node.targets[0], ast.Name):
            return None
        if self._java_type_name(node.value):
            return node.targets[0].id
        return None

    def _has_java_annotations(self, node: ast.Module) -> bool:
        for child in ast.walk(node):
            annotations = []
            if isinstance(child, ast.arg) and child.annotation is not None:
                annotations.append(child.annotation)
            elif isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef)) and child.returns is not None:
                annotations.append(child.returns)
            elif isinstance(child, ast.AnnAssign):
                annotations.append(child.annotation)

            for annotation in annotations:
                for annotation_node in ast.walk(annotation):
                    if isinstance(annotation_node, ast.Name) and annotation_node.id in self.java_runtime_names:
                        return True
        return False
