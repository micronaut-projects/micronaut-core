import ast
import keyword
import re
import java
from typing import Optional, Dict, List, Any

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

def normalize_python_keyword_alias(name: str) -> str:
    if name.endswith('_') and keyword.iskeyword(name[:-1]):
        return name[:-1]
    return name

# Import ast.unparse if available (Python 3.9+), otherwise use a fallback
try:
    from ast import unparse
except ImportError:
    # For older Python versions, we need a different approach
    import sys
    if sys.version_info >= (3, 9):
        from ast import unparse
    else:
        # Fallback for older versions - we'll need to implement our own unparse
        def unparse(node):
            # Simple fallback - this won't handle all cases perfectly
            if isinstance(node, ast.Module):
                return '\n'.join(unparse(stmt) for stmt in node.body)
            elif isinstance(node, ast.FunctionDef):
                args = ', '.join(arg.arg for arg in node.args.args)
                body = '\n'.join('    ' + unparse(stmt) for stmt in node.body)
                return f"def {node.name}({args}):\n{body}"
            elif isinstance(node, ast.ClassDef):
                bases = ', '.join(unparse(base) for base in node.bases)
                body = '\n'.join('    ' + unparse(stmt) for stmt in node.body)
                return f"class {node.name}({bases}):\n{body}"
            elif isinstance(node, ast.Expr):
                return unparse(node.value)
            elif isinstance(node, ast.Str):
                return repr(node.s)
            elif isinstance(node, ast.Name):
                return node.id
            else:
                return str(node)


class MicronautTransformer(ast.NodeTransformer):
    """
    AST transformer that converts Java imports into appropriate Python constructs.
    Annotations become decorators, regular Java types become java.type() references.
    """

    def __init__(self, callback_get_class_element, callback_get_class_elements, strip_java_interface_bases=False):
        """
        Initialize the transformer.

        Args:
            callback_get_class_element: Function to lookup a single ClassElement by name
            callback_get_class_elements: Function to lookup ClassElements by package
        """
        self.callback_get_class_element = callback_get_class_element
        self.callback_get_class_elements = callback_get_class_elements
        self.strip_java_interface_bases = strip_java_interface_bases
        self.transformed_code = []
        self.java_type_assignments = []
        self.imports_to_transform = []
        self.generated_decorators = set()
        self.generated_decorator_code = {}
        self.java_class_imports = {}
        self.java_interface_names = set()
        self.java_keyword_method_aliases = {}
        self.has_java_import = False
        self.exported_types = []
        self.all_class_names = []
        self.class_depth = 0
        self.function_depth = 0

    def visit_ImportFrom(self, node: ast.ImportFrom):
        """
        Transform import statements like:
        from jakarta.inject import Singleton
        from jakarta.inject import singleton
        from io.micronaut.core.annotation import *
        """
        if not node.module:
            return node

        java_module = self._to_java_import_module(node.module)

        # Special handling for io. prefixed imports to avoid conflict with Python's builtin io module
        transformed_module = self._to_python_import_module(java_module)
        if transformed_module.startswith('io.'):
            transformed_module = transformed_module[3:]  # Remove 'io.' prefix

        # Collect imports to transform - check if JavaVisitorContext.getClassElements returns annotations
        transformed_any = False
        for alias in node.names:
            if alias.name == '*':
                # Handle star imports - scan the entire package
                if self._handle_star_import(java_module, transformed_module):
                    transformed_any = True
            else:
                # Handle specific imports
                if self._handle_specific_import(java_module, transformed_module, alias):
                    transformed_any = True

        # If any imports were transformed, replace the import with the transformed module name
        if transformed_any:
            if transformed_module != node.module:
                # Create a new ImportFrom node with the transformed module name
                new_node = ast.ImportFrom(
                    module=transformed_module,
                    names=node.names,
                    level=node.level
                )
                # Copy other attributes
                new_node.lineno = node.lineno
                new_node.end_lineno = node.end_lineno
                new_node.col_offset = node.col_offset
                new_node.end_col_offset = node.end_col_offset
                return new_node
            else:
                return None  # Remove the import from the AST

        return node

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
        transformed_any = False

        for alias in node.names:
            original_module_name = alias.name
            java_module_name = self._to_java_import_module(original_module_name)
            # Scan the entire package for annotation types
            try:
                class_elements = self.callback_get_class_elements(java_module_name)
            except Exception:
                class_elements = None
            if class_elements:
                for class_element in class_elements:
                    if self._is_annotation_class(class_element):
                        import_name = class_element.getSimpleName()
                        decorator_code = self._generate_decorator_from_class_element(class_element, import_name)
                        if decorator_code:
                            self.transformed_code.append(decorator_code)

        return node

    def visit_Module(self, node: ast.Module) -> ast.Module:
        """
        Process the entire module and add generated decorators and java.type assignments at the beginning.
        """
        # First visit all nodes to collect imports
        self.generic_visit(node)

        # Add generated code at the beginning
        if self.transformed_code or self.java_type_assignments or self.has_java_import:
            # Create AST nodes for the generated code
            generated_nodes = []

            # Add import java statement if we have java.type() calls
            if self.has_java_import:
                java_import_code = "import java"
                try:
                    java_import_ast = ast.parse(java_import_code)
                    generated_nodes.extend(java_import_ast.body)
                except SyntaxError as e:
                    print(f"Error parsing java import: {e}")

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
                    print(f"Error parsing micronaut_annotation: {e}")

            # Add java.type() assignments
            for java_type_assignment in self.java_type_assignments:
                try:
                    java_type_ast = ast.parse(java_type_assignment)
                    generated_nodes.extend(java_type_ast.body)
                except SyntaxError as e:
                    print(f"Error parsing java type assignment: {e}")

            # Add the generated decorator functions
            for decorator_code in self.transformed_code:
                try:
                    # Parse the generated decorator code
                    decorator_ast = ast.parse(decorator_code)
                    generated_nodes.extend(decorator_ast.body)
                except SyntaxError as e:
                    print(f"Error parsing generated decorator: {e}")
                    continue

            insert_at = self._generated_code_insert_index(node)
            node.body = node.body[:insert_at] + generated_nodes + node.body[insert_at:]

        return node

    def _generated_code_insert_index(self, node: ast.Module) -> int:
        insert_at = 0
        if node.body and self._is_module_docstring(node.body[0]):
            insert_at = 1
        while insert_at < len(node.body) and self._is_future_import(node.body[insert_at]):
            insert_at += 1
        return insert_at

    def _is_module_docstring(self, node: ast.AST) -> bool:
        if not isinstance(node, ast.Expr):
            return False
        value = node.value
        if isinstance(value, ast.Constant):
            return isinstance(value.value, str)
        return isinstance(value, ast.Str)

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
            node.bases = [
                base
                for base in node.bases
                if not self._is_java_interface_base(base)
            ]
            if len(node.bases) != original_base_count:
                node.keywords = [
                    keyword
                    for keyword in node.keywords
                    if keyword.arg != "new_style"
                ]
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

    def visit_Assign(self, node: ast.Assign):
        """
        Track direct java.type() aliases so Java interface bases can be stripped
        from runtime Python classes before GraalPy creates host adapters.
        """
        if self._track_java_type_assignment(node):
            return None
        return self.generic_visit(node)

    def visit_Attribute(self, node: ast.Attribute):
        self.generic_visit(node)
        java_method_name = self._java_keyword_method_name(node)
        if java_method_name is None:
            return node

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
        decorator = self._normalize_bare_generated_decorator(decorator)
        if isinstance(decorator, ast.Call) and self._is_generated_annotation_call(decorator):
            self._normalize_annotation_keyword_arguments(decorator)
        return decorator

    def _is_generated_annotation_call(self, call: ast.Call) -> bool:
        if isinstance(call.func, ast.Name):
            return call.func.id in self.generated_decorators
        if isinstance(call.func, ast.Attribute):
            return call.func.attr in self.generated_decorators
        return False

    def _normalize_bare_generated_decorator(self, decorator):
        """
        Convert @GeneratedAnnotation to @GeneratedAnnotation() so generated decorators
        can treat a single callable positional argument as an annotation value.
        """
        if isinstance(decorator, ast.Call):
            return decorator
        decorator_name = self._get_decorator_name(decorator)
        if decorator_name in self.generated_decorators:
            call = ast.Call(func=decorator, args=[], keywords=[])
            return ast.copy_location(call, decorator)
        return decorator

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
        Handle specific imports like 'from jakarta.inject import Singleton' or 'from jakarta.inject import Singleton as S'
        Returns True if the import was transformed.
        """
        import_name = alias.name  # The actual name being imported (e.g., "Singleton")
        variable_name = alias.asname if alias.asname else alias.name  # The name to use for the variable (e.g., "S" or "Singleton")

        full_name = f"{original_module_name}.{import_name}"

        # Try to get the ClassElement
        class_element = self.callback_get_class_element(full_name)
        if class_element:
            # Check if it's an annotation
            if self._is_annotation_class(class_element):
                # Generate decorator for annotations
                decorator_code = self._generate_decorator_from_class_element(class_element, variable_name)
                if decorator_code:
                    self.transformed_code.append(decorator_code)
                    return True
            else:
                self._track_java_class(variable_name, class_element)
                # Collect Java class import for VFS generation
                self._collect_java_class_import(transformed_module_name, variable_name, class_element.getName())
                # Generate java.type() assignment for regular Java types
                java_type_assignment = f"{variable_name} = java.type('{class_element.getName()}')"
                self.java_type_assignments.append(java_type_assignment)
                self.has_java_import = True
                return True
        else:
            # Try with different naming conventions
            # Java style: Singleton -> singleton
            alt_name = self._to_python_case(import_name)
            if alt_name != import_name:
                alt_full_name = f"{original_module_name}.{alt_name}"
                class_element = self.callback_get_class_element(alt_full_name)
                if class_element:
                    # Check if it's an annotation
                    if self._is_annotation_class(class_element):
                        # Generate decorator for annotations
                        decorator_code = self._generate_decorator_from_class_element(class_element, variable_name)
                        if decorator_code:
                            self.transformed_code.append(decorator_code)
                            return True
                    else:
                        self._track_java_class(variable_name, class_element)
                        # Collect Java class import for VFS generation
                        self._collect_java_class_import(transformed_module_name, variable_name, class_element.getName())
                        # Generate java.type() assignment for regular Java types
                        java_type_assignment = f"{variable_name} = java.type('{class_element.getName()}')"
                        self.java_type_assignments.append(java_type_assignment)
                        self.has_java_import = True
                        return True
        return False

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
        try:
            if class_element.isInterface():
                self.java_interface_names.add(variable_name)
        except Exception:
            pass
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
        class_name = self._java_type_name(base)
        if class_name:
            class_element = self.callback_get_class_element(class_name)
            if class_element:
                try:
                    return class_element.isInterface()
                except Exception:
                    return False
        base_name = self._base_name(base)
        return base_name in self.java_interface_names

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
        if isinstance(arg, ast.Str):
            return arg.s
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
                # Check if it's an annotation
                if self._is_annotation_class(class_element):
                    import_name = class_element.getSimpleName()
                    decorator_code = self._generate_decorator_from_class_element(class_element, import_name)
                    if decorator_code:
                        self.transformed_code.append(decorator_code)
                        transformed_any = True
            return transformed_any

        return False

    def _is_annotation_class(self, class_element) -> bool:
        """
        Check if a ClassElement represents an annotation class.
        """
        # Check if it has @Retention annotation or is in java.lang.annotation package
        if class_element.getPackageName().startswith('java.lang.annotation'):
            return True

        # Check for retention policy using annotation metadata (fallback)
        annotation_metadata = class_element.getAnnotationMetadata()
        has_retention = annotation_metadata.hasAnnotation('java.lang.annotation.Retention')
        if has_retention:
            return True

        # Use native Java AST to check if it's an annotation type
        try:
            native_type = class_element.getNativeType()
            if native_type and hasattr(native_type, 'element'):
                java_element = native_type.element()
                if java_element and hasattr(java_element, 'getKind'):
                    kind = java_element.getKind()
                    if hasattr(kind, 'name'):
                        is_annotation = kind.name() == 'ANNOTATION_TYPE'
                        return is_annotation
        except Exception as e:
            print(f"Error checking annotation kind for {class_element.getName()}: {e}")

        return False

    def _targets_annotation_type(self, class_element) -> bool:
        """
        Check if a ClassElement is directly applicable to annotation types.
        """
        if not self._is_annotation_class(class_element):
            return False

        try:
            annotation_metadata = class_element.getAnnotationMetadata()
            target_annotation = annotation_metadata.findDeclaredAnnotation('java.lang.annotation.Target').orElse(None)
            if target_annotation is not None:
                return self._annotation_value_targets_annotation_type(target_annotation)
        except Exception:
            pass

        try:
            annotation_metadata = class_element.getAnnotationMetadata()
            ElementType = java.type('java.lang.annotation.ElementType')
            declared_metadata = annotation_metadata.getDeclaredMetadata()
            targets = declared_metadata.enumValues(
                'java.lang.annotation.Target',
                'value',
                ElementType
            )
            for target in targets:
                if str(target).endswith('ANNOTATION_TYPE'):
                    return True
        except Exception:
            pass

        try:
            annotation_metadata = class_element.getAnnotationMetadata()
            declared_metadata = annotation_metadata.getDeclaredMetadata()
            target_annotation = declared_metadata.findDeclaredAnnotation('java.lang.annotation.Target').orElse(None)
            if target_annotation and 'ANNOTATION_TYPE' in str(target_annotation.getValues()):
                return True
        except Exception:
            pass

        try:
            native_type = class_element.getNativeType()
            if native_type:
                if self._native_class_targets_annotation_type(native_type):
                    return True
                java_element = self._native_type_element(native_type)
                if java_element:
                    if not self._java_element_is_annotation_type(java_element):
                        return False
                    try:
                        Target = java.type('java.lang.annotation.Target')
                        target_annotation = java_element.getAnnotation(Target)
                        if target_annotation is not None:
                            for target in target_annotation.value():
                                if str(target).endswith('ANNOTATION_TYPE'):
                                    return True
                            return False
                    except Exception:
                        pass
                    for annotation_mirror in java_element.getAnnotationMirrors():
                        annotation_type = annotation_mirror.getAnnotationType()
                        annotation_element = annotation_type.asElement()
                        if str(annotation_element) == 'java.lang.annotation.Target' or str(annotation_type) == 'java.lang.annotation.Target':
                            for target_value in annotation_mirror.getElementValues().values():
                                target_text = str(target_value)
                                try:
                                    target_text += ' ' + str(target_value.toString())
                                except Exception:
                                    pass
                                if 'ANNOTATION_TYPE' in target_text:
                                    return True
                            return False
        except Exception:
            pass

        return False

    def _annotation_value_targets_annotation_type(self, annotation_value) -> bool:
        try:
            ElementType = java.type('java.lang.annotation.ElementType')
            for target in annotation_value.enumValues('value', ElementType):
                if str(target).endswith('ANNOTATION_TYPE'):
                    return True
        except Exception:
            pass

        try:
            return 'ANNOTATION_TYPE' in str(annotation_value.getValues())
        except Exception:
            return False

    def _native_type_element(self, native_type):
        if native_type is None:
            return None
        try:
            return native_type.element()
        except Exception:
            return None

    def _java_element_is_annotation_type(self, java_element) -> bool:
        try:
            kind = java_element.getKind()
            if hasattr(kind, 'name'):
                return kind.name() == 'ANNOTATION_TYPE'
            return str(kind).endswith('ANNOTATION_TYPE')
        except Exception:
            return False

    def _native_class_targets_annotation_type(self, native_type) -> bool:
        try:
            if not native_type.isAnnotation():
                return False
            Target = java.type('java.lang.annotation.Target')
            target_annotation = native_type.getAnnotation(Target)
            if target_annotation is None:
                return False
            for target in target_annotation.value():
                if str(target).endswith('ANNOTATION_TYPE'):
                    return True
        except Exception:
            return False
        return False

    def _is_nested_class(self, class_element) -> bool:
        """
        Check if a ClassElement represents a nested (inner) class.
        """
        try:
            native_type = class_element.getNativeType()
            if native_type and hasattr(native_type, 'element'):
                java_element = native_type.element()
                if java_element and hasattr(java_element, 'getNestingKind'):
                    nesting_kind = java_element.getNestingKind()
                    if hasattr(nesting_kind, 'name'):
                        return nesting_kind.name() == 'MEMBER'
        except Exception as e:
            print(f"Error checking nesting kind for {class_element.getName()}: {e}")

        return False

    def _generate_decorator_from_class_element(self, class_element, import_name: str) -> Optional[str]:
        """
        Generate Python decorator code from a ClassElement.
        """
        decorator_name = import_name
        annotation_name = class_element.getName()

        # skip inners for now
        if "$" in annotation_name:
            return None

        # Skip if already generated
        if decorator_name in self.generated_decorators:
            return None

        self.generated_decorators.add(decorator_name)

        # Get annotation metadata
        annotation_metadata = class_element.getAnnotationMetadata()

        # Check for repeatable annotation
        repeatable_name = self._get_repeatable_name(annotation_metadata, class_element)
        repeatable_info = f', repeated="{repeatable_name}"' if repeatable_name else ''
        annotation_target_info = ', annotationTypeTarget=True' if self._targets_annotation_type(class_element) else ''
        # Get annotation parameters to generate proper function signature
        param_info = self._get_annotation_parameters(class_element)
        param_signature = param_info['signature']
        param_handling = param_info['handling']

        # Collect meta-annotations to include as decorators
        decorator_lines = [f'@micronaut_annotation("{annotation_name}"{repeatable_info}{annotation_target_info})']

        nested_members_prelude, nested_members_code = self._generate_nested_members_sections(class_element, decorator_name)

        # Get all annotations on this annotation class (meta-annotations).
        # Some Java annotations reference optional/provided meta-annotation types.
        # Only generate Python imports/decorator calls for types the processor can resolve.
        meta_annotations = []
        annotation_names = annotation_metadata.getAnnotationNames()
        for meta_annotation_name in annotation_names:
            # Skip retention and other built-in annotations that aren't user-facing
            if (not meta_annotation_name.startswith('java.lang.annotation.')
                    and meta_annotation_name not in META_ANNOTATIONS_TO_SKIP_IN_SOURCE):
                meta_class_element = self.callback_get_class_element(meta_annotation_name)
                if not meta_class_element or not self._is_annotation_class(meta_class_element):
                    continue
                meta_decorator_name = self._meta_decorator_name(meta_annotation_name, annotation_name, meta_class_element)
                if meta_decorator_name == decorator_name and meta_annotation_name != annotation_name:
                    continue

                meta_annotations.append((meta_annotation_name, meta_class_element, meta_decorator_name))

        for meta_annotation_name, meta_class_element, meta_decorator_name in meta_annotations:
            # Generate decorator for the meta-annotation if not already generated
            if meta_decorator_name not in self.generated_decorators:
                meta_decorator_code = self._generate_decorator_from_class_element(meta_class_element, meta_decorator_name)
                if meta_decorator_code:
                    self.transformed_code.append(meta_decorator_code)
            # Add the meta-annotation as a decorator
            decorator_lines.append(f'@{meta_decorator_name}()')

        # Collect imports for meta-annotations
        import_lines = []
        current_package = '.'.join(annotation_name.split('.')[:-1])  # Package of current annotation

        for meta_annotation_name, _, _ in meta_annotations:
            if '$' in meta_annotation_name:
                continue

            meta_package = '.'.join(meta_annotation_name.split('.')[:-1])
            meta_simple_name = meta_annotation_name.split('.')[-1]

            # Transform io. prefixed packages to avoid conflict with Python's builtin io module
            import_package = self._to_python_import_module(meta_package)
            if import_package.startswith('io.'):
                import_package = import_package[3:]  # Remove 'io.' prefix

            # Import from the concrete annotation module so duplicate VFS package
            # roots cannot resolve the package member as a module object.
            import_lines.append(f"from {import_package}.{meta_simple_name} import {meta_simple_name}")

        # Remove duplicates
        import_lines = list(set(import_lines))

        # Generate the decorator function with imports, meta-annotations and micronaut_annotation for VFS
        imports_section = '\n'.join(import_lines) + '\n\n' if import_lines else ''

        decorator_code = f'''
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
    """
    if len(args) == 1 and callable(args[0]) and not kwargs:
        target = args[0]
        if not any(value is target for value in globals().values()):
            return target

    def decorator(target):
        return target

    return decorator
{nested_members_code}
'''

        # Store the generated code in the dict for extraction (use qualified annotation name as key)
        self.generated_decorator_code[annotation_name] = decorator_code

        # Handle nested annotations (annotations referenced by this annotation's parameters)
        self._generate_nested_decorators(class_element, import_name)

        return decorator_code

    def _generate_decorator_from_class_element_with_name(self, class_element, import_name: str, custom_annotation_name: str) -> Optional[str]:
        """
        Generate Python decorator code from a ClassElement with a custom annotation name.
        """
        decorator_name = import_name

        # Skip if already generated
        if decorator_name in self.generated_decorators:
            return None

        self.generated_decorators.add(decorator_name)

        # Get annotation metadata
        annotation_metadata = class_element.getAnnotationMetadata()

        # Check for repeatable annotation
        repeatable_name = self._get_repeatable_name(annotation_metadata, class_element)
        repeatable_info = f', repeated="{repeatable_name}"' if repeatable_name else ''
        annotation_target_info = ', annotationTypeTarget=True' if self._targets_annotation_type(class_element) else ''

        # Get annotation parameters to generate proper function signature
        param_info = self._get_annotation_parameters(class_element)
        param_signature = param_info['signature']
        param_handling = param_info['handling']

        # Generate the decorator function with custom annotation name and micronaut_annotation
        nested_members_prelude, nested_members_code = self._generate_nested_members_sections(class_element, decorator_name)
        decorator_code = f'''
def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    """
    Decorator to mark functions as Micronaut annotations.
    """
    def decorator(target):
        return target
    return decorator

{nested_members_prelude}
@micronaut_annotation("{custom_annotation_name}"{repeatable_info}{annotation_target_info})
def {decorator_name}({param_signature}):
    """
    Micronaut annotation decorator for {custom_annotation_name}.
    """
    if len(args) == 1 and callable(args[0]) and not kwargs:
        target = args[0]
        if not any(value is target for value in globals().values()):
            return target

    def decorator(target):
        return target

    return decorator
{nested_members_code}
'''
        self.generated_decorator_code[custom_annotation_name] = decorator_code
        # Handle nested annotations (annotations referenced by this annotation's parameters)
        self._generate_nested_decorators(class_element, import_name)

        return decorator_code

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
        Get the repeatable container annotation name if this is a repeatable annotation.
        """
        try:
            # First try the annotation metadata approach (for consistency)
            if annotation_metadata.hasAnnotation('java.lang.annotation.Repeatable'):
                repeatable_value = annotation_metadata.getValue('java.lang.annotation.Repeatable', 'value')
                if repeatable_value and hasattr(repeatable_value, 'getName'):
                    return repeatable_value.getName()

            # If that doesn't work, try accessing the native Java element directly
            # This provides more reliable access to annotation mirrors
            native_type = class_element.getNativeType()
            if native_type and hasattr(native_type, 'element'):
                java_element = native_type.element()
                if java_element and hasattr(java_element, 'getAnnotationMirrors'):
                    annotation_mirrors = java_element.getAnnotationMirrors()
                    # Look for @Repeatable annotation
                    for mirror in annotation_mirrors:
                        annotation_type = mirror.getAnnotationType()
                        if annotation_type and hasattr(annotation_type, 'toString'):
                            type_name = annotation_type.toString()
                            if 'java.lang.annotation.Repeatable' in type_name:
                                # Extract the value from the annotation
                                element_values = mirror.getElementValues()
                                for key, value in element_values.items():
                                    if hasattr(key, 'getSimpleName') and key.getSimpleName().toString() == 'value':
                                        if hasattr(value, 'getValue'):
                                            container_value = value.getValue()
                                            if container_value and hasattr(container_value, 'toString'):
                                                return container_value.toString()
        except Exception as e:
            print(f"Error checking repeatable annotation for {class_element.getName()}: {e}")

        return None



    def _generate_nested_decorators(self, class_element, parent_name: str):
        """
        Generate decorators for nested annotation members by inspecting annotation methods.
        """
        try:
            # Use the Java AST to inspect annotation methods and find those that return annotation types
            native_type = class_element.getNativeType()
            if native_type and hasattr(native_type, 'element'):
                java_element = native_type.element()
                if java_element and hasattr(java_element, 'getEnclosedElements'):
                    enclosed_elements = java_element.getEnclosedElements()
                    if enclosed_elements:
                        for element in enclosed_elements:
                            # Check if this is a method that returns an annotation type
                            if hasattr(element, 'getKind') and hasattr(element.getKind(), 'name'):
                                if element.getKind().name() == 'METHOD':
                                     # Get the return type
                                     return_type = None
                                     if hasattr(element, 'getReturnType'):
                                         return_type = element.getReturnType()

                                     if return_type and hasattr(return_type, 'toString'):
                                         return_type_name = return_type.toString()
                                         # Check if the return type is an annotation
                                         nested_annotation_element = self.callback_get_class_element(return_type_name)
                                         if nested_annotation_element and self._is_annotation_class(nested_annotation_element):
                                             # Skip nested annotations (annotations defined within the current annotation)
                                             nested_name = nested_annotation_element.getName()
                                             if nested_name.startswith(parent_name.replace('.', '$') + '$'):
                                                 continue

                                             # Generate decorator for the nested annotation (use the annotation's actual name)
                                             # We don't need a special nested-named decorator, just ensure the annotation decorator exists
                                             # Handle nested classes by extracting the simple name after the $
                                             full_name = nested_name
                                             if '$' in full_name:
                                                 annotation_simple_name = full_name.split('$')[-1]
                                             else:
                                                 annotation_simple_name = nested_annotation_element.getSimpleName()

                                             if annotation_simple_name not in self.generated_decorators:
                                                 original_name = nested_annotation_element.getName()

                                                 # Generate the decorator with the correct annotation name
                                                 self._generate_decorator_from_class_element_with_name(
                                                     nested_annotation_element, annotation_simple_name, original_name)
        except Exception as e:
            print(f"Error generating nested decorators for {class_element.getName()}: {e}")

    def _generate_nested_members_code(self, class_element, parent_name: str) -> str:
        """
        Generate Python attributes for Java nested types exposed through an annotation.
        """
        prelude, body = self._generate_nested_members_sections(class_element, parent_name)
        return prelude + body

    def _generate_nested_members_sections(self, class_element, parent_name: str):
        """
        Generate Python definitions and post-definition assignments for Java nested types
        exposed through an annotation.
        """
        prelude_lines = []
        lines = []
        needs_java = False
        for nested_element in self._get_nested_class_elements(class_element):
            nested_name = nested_element.getName()
            simple_name = nested_name.split('$')[-1].split('.')[-1]
            if self._is_annotation_class(nested_element):
                repeatable_name = self._get_repeatable_name(nested_element.getAnnotationMetadata(), nested_element)
                repeatable_info = f', repeated="{repeatable_name}"' if repeatable_name else ''
                self.generated_decorators.add(simple_name)
                prelude_lines.append(f'''

@micronaut_annotation("{nested_name}"{repeatable_info})
def {simple_name}(*args, **kwargs):
    """
    Micronaut annotation decorator for {nested_name}.
    """
    if len(args) == 1 and callable(args[0]) and not kwargs:
        target = args[0]
        if not any(value is target for value in globals().values()):
            return target

    def decorator(target):
        return target

    return decorator
''')
                lines.append(f'''

{parent_name}.{simple_name} = {simple_name}
''')
            else:
                binary_name = self._to_binary_nested_name(class_element.getName(), nested_name)
                needs_java = True
                lines.append(f'''
{simple_name} = java.type("{binary_name}")
{parent_name}.{simple_name} = {simple_name}
''')

        if needs_java:
            lines.insert(0, "\nimport java\n")
        return ''.join(prelude_lines), ''.join(lines)

    def _meta_decorator_name(self, meta_annotation_name: str, annotation_name: str, meta_class_element) -> str:
        if meta_annotation_name.startswith(annotation_name + '$'):
            return meta_annotation_name.split('$')[-1]
        if '$' in meta_annotation_name:
            return meta_annotation_name.split('$')[-1]
        return meta_class_element.getSimpleName()

    def _get_nested_class_elements(self, class_element):
        nested_elements = []
        seen_nested_names = set()

        def add_nested_element(nested_element):
            if not nested_element:
                return
            nested_name = nested_element.getName()
            if nested_name in seen_nested_names:
                return
            seen_nested_names.add(nested_name)
            nested_elements.append(nested_element)

        def add_nested_by_simple_name(simple_name):
            nested_element = (
                self.callback_get_class_element(f"{class_element.getName()}${simple_name}") or
                self.callback_get_class_element(f"{class_element.getName()}.{simple_name}")
            )
            add_nested_element(nested_element)

        try:
            native_type = class_element.getNativeType()
            if native_type and hasattr(native_type, 'element'):
                java_element = native_type.element()
                if java_element and hasattr(java_element, 'getEnclosedElements'):
                    for enclosed in java_element.getEnclosedElements():
                        if not (hasattr(enclosed, 'getKind') and hasattr(enclosed.getKind(), 'name')):
                            continue
                        kind = enclosed.getKind().name()
                        if kind not in ('ANNOTATION_TYPE', 'INTERFACE', 'CLASS', 'ENUM'):
                            continue
                        simple_name = str(enclosed.getSimpleName())
                        add_nested_by_simple_name(simple_name)
            if native_type and hasattr(native_type, 'getDeclaredClasses'):
                for nested_class in native_type.getDeclaredClasses():
                    simple_name = str(nested_class.getSimpleName())
                    add_nested_by_simple_name(simple_name)
            if hasattr(class_element, 'getMethods'):
                parent_name = class_element.getName()
                for method in class_element.getMethods():
                    if not hasattr(method, 'getReturnType'):
                        continue
                    return_type = method.getReturnType()
                    try:
                        if return_type.isArray():
                            return_type = return_type.fromArray()
                    except Exception:
                        pass
                    return_type_name = return_type.getName()
                    if return_type_name.startswith(parent_name + '$') or return_type_name.startswith(parent_name + '.'):
                        add_nested_element(return_type)
        except Exception as e:
            print(f"Error generating nested members for {class_element.getName()}: {e}")
        return nested_elements

    def _to_binary_nested_name(self, parent_name: str, nested_name: str) -> str:
        if '$' in nested_name:
            return nested_name
        prefix = parent_name + '.'
        if nested_name.startswith(prefix):
            return parent_name + '$' + nested_name[len(prefix):]
        return nested_name

    def _collect_java_class_import(self, package_name: str, variable_name: str, full_class_name: str):
        """
        Collect Java class import for VFS generation.
        """
        if package_name not in self.java_class_imports:
            self.java_class_imports[package_name] = []

        self.java_class_imports[package_name].append({
            'variable': variable_name,
            'class_name': full_class_name
        })

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

    def _normalize_keyword_safe_module(self, module_name: str) -> str:
        """
        Convert Python-safe package segments such as async_ back to Java names.
        """
        return '.'.join(
            part[:-1] if part.endswith('_') and keyword.iskeyword(part[:-1]) else part
            for part in module_name.split('.')
        )

    def get_transformed_code(self) -> str:
        """
        Get all the generated decorator code.
        """
        return '\n'.join(self.transformed_code)

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
