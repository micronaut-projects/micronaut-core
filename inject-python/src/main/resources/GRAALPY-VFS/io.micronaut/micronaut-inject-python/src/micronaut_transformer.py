import ast
import re
from typing import Optional, Dict, List, Any

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

    def __init__(self, callback_get_class_element, callback_get_class_elements):
        """
        Initialize the transformer.

        Args:
            callback_get_class_element: Function to lookup a single ClassElement by name
            callback_get_class_elements: Function to lookup ClassElements by package
        """
        self.callback_get_class_element = callback_get_class_element
        self.callback_get_class_elements = callback_get_class_elements
        self.transformed_code = []
        self.java_type_assignments = []
        self.imports_to_transform = []
        self.generated_decorators = set()
        self.generated_decorator_code = {}
        self.java_class_imports = {}
        self.has_java_import = False
        self.exported_types = []
        self.all_class_names = []

    def visit_ImportFrom(self, node: ast.ImportFrom):
        """
        Transform import statements like:
        from jakarta.inject import Singleton
        from jakarta.inject import singleton
        from io.micronaut.core.annotation import *
        """
        if not node.module:
            return node

        # Special handling for io. prefixed imports to avoid conflict with Python's builtin io module
        transformed_module = node.module
        if node.module.startswith('io.'):
            transformed_module = node.module[3:]  # Remove 'io.' prefix

        # Collect imports to transform - check if JavaVisitorContext.getClassElements returns annotations
        transformed_any = False
        for alias in node.names:
            if alias.name == '*':
                # Handle star imports - scan the entire package
                if self._handle_star_import(node.module, transformed_module):
                    transformed_any = True
            else:
                # Handle specific imports
                if self._handle_specific_import(node.module, transformed_module, alias):
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
            # Scan the entire package for annotation types
            try:
                class_elements = self.callback_get_class_elements(original_module_name)
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
def micronaut_annotation(name, repeated=None):
    """
    Decorator to mark functions as Micronaut annotations.
    """
    def decorator(func):
        return func
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

            # Insert generated nodes at the beginning of the module
            node.body = generated_nodes + node.body

        return node

    def visit_ClassDef(self, node: ast.ClassDef) -> ast.ClassDef:
        """
        Track all class definitions and exported types separately.
        """
        self.all_class_names.append(node.name)
        if node.decorator_list:
            for decorator in node.decorator_list:
                decorator_name = self._get_decorator_name(decorator)
                if decorator_name in self.generated_decorators:
                    self.exported_types.append(node.name)
                    break
        self.generic_visit(node)
        return node

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
            try:
                attr_name = decorator.attr
            except Exception:
                attr_name = None
            if attr_name in self.generated_decorators:
                return decorator.attr
            # Handle decorated decorators like @micronaut_annotation("...")
            if isinstance(decorator.value, ast.Name) and decorator.value.id in self.generated_decorators:
                return decorator.value.id
        return None

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
                        # Collect Java class import for VFS generation
                        self._collect_java_class_import(transformed_module_name, variable_name, class_element.getName())
                        # Generate java.type() assignment for regular Java types
                        java_type_assignment = f"{variable_name} = java.type('{class_element.getName()}')"
                        self.java_type_assignments.append(java_type_assignment)
                        self.has_java_import = True
                        return True
        return False

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
        try:
            is_annotation = class_element.isAssignable('java.lang.annotation.Annotation')
            if is_annotation:
                return True
        except Exception:
            pass

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
            if native_type:
                return native_type.element().getKind().name() == 'ANNOTATION_TYPE'
        except Exception as e:
            print(f"Error checking annotation kind for {class_element.getName()}: {e}")

        return False

    def _is_nested_class(self, class_element) -> bool:
        """
        Check if a ClassElement represents a nested (inner) class.
        """
        try:
            native_type = class_element.getNativeType()
            if native_type:
                return native_type.element().getNestingKind().name() == 'MEMBER'
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

        # Get annotation parameters to generate proper function signature
        param_info = self._get_annotation_parameters(class_element)
        param_signature = param_info['signature']
        param_handling = param_info['handling']

        # Collect meta-annotations to include as decorators
        decorator_lines = [f'@micronaut_annotation("{annotation_name}"{repeatable_info})']

        # Get all annotations on this annotation class (meta-annotations)
        annotation_names = annotation_metadata.getAnnotationNames()
        for meta_annotation_name in annotation_names:
            # Skip retention and other built-in annotations that aren't user-facing
            if not meta_annotation_name.startswith('java.lang.annotation.'):
                # Skip nested annotations (annotations with $ in their names)
                if '$' in meta_annotation_name:
                    continue

                # Generate decorator for the meta-annotation if not already generated
                meta_class_element = self.callback_get_class_element(meta_annotation_name)
                if meta_class_element and self._is_annotation_class(meta_class_element):
                    meta_decorator_name = meta_class_element.getSimpleName()
                    if meta_decorator_name not in self.generated_decorators:
                        meta_decorator_code = self._generate_decorator_from_class_element(meta_class_element, meta_decorator_name)
                        if meta_decorator_code:
                            self.transformed_code.append(meta_decorator_code)
                    # Add the meta-annotation as a decorator
                    decorator_lines.append(f'@{meta_decorator_name}')

        # Collect imports for meta-annotations
        import_lines = []
        current_package = '.'.join(annotation_name.split('.')[:-1])  # Package of current annotation

        for meta_annotation_name in annotation_metadata.getAnnotationNames():
            if not meta_annotation_name.startswith('java.lang.annotation.'):
                # Skip nested annotations (annotations with $ in their names)
                if '$' in meta_annotation_name:
                    continue

                meta_package = '.'.join(meta_annotation_name.split('.')[:-1])
                meta_simple_name = meta_annotation_name.split('.')[-1]

                # Transform io. prefixed packages to avoid conflict with Python's builtin io module
                import_package = meta_package
                if meta_package.startswith('io.'):
                    import_package = meta_package[3:]  # Remove 'io.' prefix

                # Use absolute import path of the function
                import_lines.append(f"from {import_package} import {meta_simple_name}")

        # Remove duplicates
        import_lines = list(set(import_lines))

        # Generate the decorator function with imports, meta-annotations and micronaut_annotation for VFS
        imports_section = '\n'.join(import_lines) + '\n\n' if import_lines else ''

        decorator_code = f'''
{imports_section}def micronaut_annotation(name, repeated=None):
    """
    Decorator to mark functions as Micronaut annotations.
    """
    def decorator(func):
        return func
    return decorator

{chr(10).join(decorator_lines)}
def {decorator_name}({param_signature}):
    """
    Micronaut annotation decorator for {annotation_name}.
    """
    def decorator(func):
        return func

    # Handle both @Annotation and @Annotation() usage patterns
    try:
        if len(args) == 1 and len(kwargs) == 0:
            args[0].__name__
            # Called as @Annotation (without parentheses) - args[0] is the function
            return decorator(args[0])
    except Exception:
        pass

    # Called as @Annotation() or @Annotation(param=value) - return decorator
    return decorator
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

        # Get annotation parameters to generate proper function signature
        param_info = self._get_annotation_parameters(class_element)
        param_signature = param_info['signature']
        param_handling = param_info['handling']

        # Generate the decorator function with custom annotation name and micronaut_annotation
        decorator_code = f'''
def micronaut_annotation(name, repeated=None):
    """
    Decorator to mark functions as Micronaut annotations.
    """
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("{custom_annotation_name}"{repeatable_info})
def {decorator_name}({param_signature}):
    """
    Micronaut annotation decorator for {custom_annotation_name}.
    """
    def decorator(func):
        return func

    # Handle both @Annotation and @Annotation() usage patterns
    try:
        if len(args) == 1 and len(kwargs) == 0:
            args[0].__name__
            # Called as @Annotation (without parentheses) - args[0] is the function
            return decorator(args[0])
    except Exception:
        pass

    # Called as @Annotation() or @Annotation(param=value) - return decorator
    return decorator
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
                if repeatable_value:
                    try:
                        return repeatable_value.getName()
                    except Exception:
                        pass

            # If that doesn't work, try accessing the native Java element directly
            # This provides more reliable access to annotation mirrors
            native_type = class_element.getNativeType()
            if native_type:
                annotation_mirrors = native_type.element().getAnnotationMirrors()
                # Look for @Repeatable annotation
                for mirror in annotation_mirrors:
                    annotation_type = mirror.getAnnotationType()
                    type_name = annotation_type.toString() if annotation_type else None
                    if type_name and 'java.lang.annotation.Repeatable' in type_name:
                        # Extract the value from the annotation
                        element_values = mirror.getElementValues()
                        for key, value in element_values.items():
                            simple_name = key.getSimpleName().toString()
                            if simple_name == 'value':
                                container_value = value.getValue()
                                if container_value:
                                    container_name = container_value.toString()
                                    if container_name:
                                        return container_name
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
            if native_type:
                enclosed_elements = native_type.element().getEnclosedElements()
                for element in enclosed_elements:
                    # Check if this is a method that returns an annotation type
                    kind_name = element.getKind().name()
                    if kind_name == 'METHOD':
                         # Get the return type
                         return_type = element.getReturnType()
                         return_type_name = return_type.toString() if return_type else None
                         if return_type_name:
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
                                     # Create a modified ClassElement with the dot notation name for proper annotation name
                                     original_name = nested_annotation_element.getName()
                                     dot_name = original_name.replace('$', '.')
                                     modified_annotation_name = dot_name

                                     # Generate the decorator with the correct annotation name
                                     self._generate_decorator_from_class_element_with_name(
                                         nested_annotation_element, annotation_simple_name, modified_annotation_name)
        except Exception as e:
            print(f"Error generating nested decorators for {class_element.getName()}: {e}")

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

    def _to_python_case(self, java_name: str) -> str:
        """
        Convert Java PascalCase to Python snake_case.
        """
        # General conversion: PascalCase to snake_case
        s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', java_name)
        return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()

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
