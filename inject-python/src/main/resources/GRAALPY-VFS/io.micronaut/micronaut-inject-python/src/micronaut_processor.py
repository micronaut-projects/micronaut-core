import ast
import keyword
import os
import java
from collections import OrderedDict

JavaClassDef = java.type("io.micronaut.python.processing.visitor.ClassDef")
JavaFuncDef = java.type("io.micronaut.python.processing.visitor.FunctionDef")
JavaAttributeDef = java.type("io.micronaut.python.processing.visitor.AttributeDef")
PropertyDef = java.type("io.micronaut.python.processing.visitor.PropertyDef")
DecoratorDef = java.type("io.micronaut.python.processing.visitor.DecoratorDef")
ArgumentsDef = java.type("io.micronaut.python.processing.visitor.ArgumentsDef")
ArgumentDef = java.type("io.micronaut.python.processing.visitor.ArgumentDef")
ReturnDef = java.type("io.micronaut.python.processing.visitor.ReturnDef")
TypeRef = java.type("io.micronaut.python.processing.visitor.TypeRef")
ScriptDef = java.type("io.micronaut.python.processing.visitor.ScriptDef")

def extract_decorator_name(node):
    """
    Extract the decorator name from an AST decorator node.
    Returns the decorator name as a string, or None if not extractable.
    """
    if isinstance(node, ast.Name):
        return node.id
    elif isinstance(node, ast.Attribute):
        # Handle qualified names like module.Decorator
        names = []
        current = node
        while isinstance(current, ast.Attribute):
            names.insert(0, current.attr)
            current = current.value
        if isinstance(current, ast.Name):
            names.insert(0, current.id)
        return '.'.join(names)
    elif isinstance(node, ast.Call):
        # For decorator calls like @decorator(args), extract the function name
        return extract_decorator_name(node.func)
    return None

def is_abstract_method(funcdef):
    """
    Returns True if the ast.FunctionDef has an @abstractmethod decorator.
    """
    for dec in funcdef.decorator_list:
        if extract_decorator_name(dec) == "abstractmethod":
            return True
    return False

def is_placeholder_method(funcdef):
    """
    Returns True if the function body is only a declaration placeholder.
    """
    body = funcdef.body
    if len(body) == 1:
        stmt = body[0]
        if isinstance(stmt, ast.Expr):
            value = stmt.value
            if isinstance(value, ast.Constant) and value.value is Ellipsis:
                return True
            if isinstance(value, ast.Ellipsis):
                return True
    return False

def is_static_method(func_node):
    """
    Check if a function node represents a static method (has @staticmethod or @classmethod decorator).
    """
    for decorator in func_node.decorator_list:
        if isinstance(decorator, ast.Name):
            if decorator.id in ("staticmethod", "classmethod"):
                return True
        elif isinstance(decorator, ast.Attribute):
            if decorator.attr in ("staticmethod", "classmethod"):
                return True
    return False

def is_protocol_type_name(type_name):
    """
    Returns True if the type name references typing.Protocol.
    """
    return type_name in ("typing.Protocol", "typing_extensions.Protocol", "Protocol")


class MicronautAstVisitor(ast.NodeVisitor):

    def __init__(self, callback, package_name="", file_name = "Script.py", visitor_context=None, source_root=""):
        self.callback = callback
        self.package_name = package_name
        self.visitor_context = visitor_context
        self.source_root = source_root or ""
        # maintain insertion order
        self.known_decorators = OrderedDict()
        self.known_decorator_functions = OrderedDict()
        self.current_class = None
        self.current_class_attributes = []
        self.current_class_properties = {}  # Track properties being built: name -> PropertyDef
        self.last_attribute = None  # Track last processed attribute for docstring handling
        self.in_function = False  # Track if we're inside a function definition
        self.java_type_assignments = {}  # Track java.type() assignments: variable_name -> full_qualified_name
        self.imported_types = {}  # Track imported types: simple_name -> full_qualified_name
        self.local_classes = set()  # Track class names defined in this file
        self.local_constant_values = {}  # Track local class constants visible to annotation expressions
        self.current_class_nested_types = {}  # Track nested classes visible in the current class body
        # Script handling
        self.current_script = None
        self.current_script_attributes = []
        self.current_script_functions = []
        self.script_name = file_name

    def _resolve_top_level_import(self, module_name, imported_name):
        """
        Resolve absolute imports from source-root modules.
        Source-root files are represented in the synthetic "python" package,
        while source-root directories map to real packages.
        """
        if not self.source_root:
            return None

        module_path = module_name.replace(".", os.sep)
        package_dir = os.path.join(self.source_root, module_path)
        if os.path.isdir(package_dir):
            imported_module = os.path.join(package_dir, f"{imported_name}.py")
            if os.path.isfile(imported_module):
                return f"{module_name}.{imported_name}"
            return None

        module_file = os.path.join(self.source_root, f"{module_path}.py")
        if os.path.isfile(module_file):
            package_name = module_name.rsplit(".", 1)[0] if "." in module_name else "python"
            return f"{package_name}.{imported_name}"

        return None

    def visit(self, node: ast.AST) -> ast.AST:
        match node:
            case ast.ClassDef():
                if self.in_function:
                    return node
                return self._visit_class_def(node)
            case ast.FunctionDef():
                # Track function nesting to avoid processing nested functions as micronaut decorators
                was_in_function = self.in_function
                self.in_function = True
                try:
                    # Handle property decorators
                    if self.current_class is not None:
                        property_info = self._parse_property_decorators(node)
                        if property_info:
                            property_name, property_type = property_info
                            self._handle_property_function(property_name, property_type, node)
                            return node

                    # Only check for micronaut decorators on top-level functions (not nested)
                    if self.current_class is None and not was_in_function and is_micronaut_decorator(node, self):
                        arg_dict = extract_arg_defaults(node)
                        member_decorators = extract_arg_decorators(self, node)
                        member_types = extract_arg_types(self, node)
                        # Filter out micronaut_annotation decorators as they are internal helpers
                        stereotypes = [
                            decorator_to_function(self, d)
                            for d in node.decorator_list
                            if (decorator_to_function(self, d) is not None and
                                not is_micronaut_annotation_decorator(d))
                        ]
                        annotation_name = qualify_local_annotation_name(
                            self,
                            get_micronaut_annotation_value('name', node)
                        )
                        repeated_name = qualify_local_annotation_name(
                            self,
                            get_micronaut_annotation_value('repeated', node)
                        )

                        # For decorators defined with Micronaut annotations like @Around, use package naming
                        if annotation_name is None:
                            annotation_name = f"{self.package_name}.{node.name}" if self.package_name else node.name

                        # Track the annotation name for type resolution
                        if annotation_name:
                            self.java_type_assignments[node.name] = annotation_name

                        decorator_def = DecoratorDef(node.name, annotation_name, repeated_name, arg_dict, stereotypes, member_decorators, member_types)
                        self.known_decorators[node.name] = decorator_def
                        self.callback.apply(decorator_def)
                        return node
                    else:
                        decorators = [
                            decorator_to_function(self, d)
                            for d in node.decorator_list
                            if decorator_to_function(self, d) is not None
                        ]

                        # Parse function arguments and return type
                        arguments = self.parse_function_arguments(node)
                        return_type = self.parse_function_return_type(node)
                        # Extract function type parameters
                        func_type_params = self._parse_function_type_params(node)
                        # Extract function docstring
                        func_doc = self._extract_docstring(node)

                        is_abstract = (
                            is_abstract_method(node) or
                            self._current_class_is_protocol() or
                            is_placeholder_method(node)
                        )
                        is_static = is_static_method(node)

                        func_def = JavaFuncDef(node.name, arguments, decorators, return_type, "", func_type_params, func_doc, is_abstract, is_static)
                        if self.current_class is not None:
                            if node.name == "__init__":
                                # Set as constructor
                                self.current_class = self.current_class.withConstructor(func_def)
                            else:
                                self.current_class = self.current_class.withFunction(func_def)
                        elif self.current_class is None and self._is_script_function(node):
                            self._handle_script_function(func_def)
                        return super().visit(node)
                finally:
                    self.in_function = was_in_function
            case ast.Assign():
                # Track java.type() assignments first
                self._track_java_type_assignments(node)
                self._track_local_constant_assignment(node)
                # Handle class attribute assignments (only at class body level, not inside methods)
                if self.current_class is not None and not self.in_function:
                    self._handle_assign(node)
                # Handle script attribute assignments
                elif self.current_class is None and self._is_script_assignment(node):
                    self._handle_script_assign(node)
                return node
            case ast.AnnAssign():
                self._track_local_constant_assignment(node)
                # Handle annotated assignments (type hints) only at class body level, not inside methods
                if self.current_class is not None and not self.in_function:
                    self._handle_ann_assign(node)
                # Handle script attribute assignments
                elif self.current_class is None and self._is_script_assignment(node):
                    self._handle_script_ann_assign(node)
                return node
            case ast.ImportFrom():
                # Track imported types for name resolution
                if node.module:
                    for alias in node.names:
                        # Handle relative imports
                        is_relative = node.level > 0
                        level = node.level
                        base_pkg = node.module

                        if is_relative:
                            parts = self.package_name.split('.') if self.package_name else []

                            if level == 1:
                                base_pkg = self.package_name
                            else:
                                up = level - 1
                                base_pkg = '.'.join(parts[:max(0, len(parts) - up)])

                            full_name = f"{base_pkg}.{alias.name}"
                        else:
                            local_import = self._resolve_top_level_import(node.module, alias.name)
                            if local_import is not None:
                                full_name = local_import
                            else:
                                # Absolute import: map Python import modules to Java packages
                                # Micronaut packages (micronaut.*) need 'io.' prefix added back
                                # Other packages (jakarta.*, user packages) keep their names
                                base_pkg = self._to_java_import_module(base_pkg)
                                if not node.module.startswith('io.') and base_pkg.startswith('micronaut.'):
                                    base_pkg = f"io.{base_pkg}"
                                full_name = f"{base_pkg}.{alias.name}"

                        if alias.asname:
                            self.imported_types[alias.asname] = full_name
                        else:
                            self.imported_types[alias.name] = full_name

                return super().visit(node)
            case ast.Import():

                # Track imported modules/types for name resolution
                for alias in node.names:
                    mod_name = alias.name
                    # Normalize Micronaut packages to io.micronaut.*
                    mod_name = self._to_java_import_module(mod_name)
                    if not mod_name.startswith('io.') and mod_name.startswith('micronaut.'):
                        mod_name = f"io.{mod_name}"
                    if alias.asname:
                        self.imported_types[alias.asname] = mod_name
                    else:
                        # For 'import module', the module name becomes available
                        self.imported_types[alias.name] = mod_name
                    # Also expose the last segment of a dotted module for attribute-style usage:
                    # e.g., `import micronaut.context.annotation` allows `annotation.Executable`
                    if '.' in alias.name:
                        last_seg = alias.name.split('.')[-1]
                        self.imported_types[last_seg] = mod_name
                return super().visit(node)
            case ast.Expr():
                # Handle potential field docstrings - string literals that follow attribute assignments
                if self.current_class is not None and self.last_attribute is not None:
                    self._handle_field_docstring(node)
                return node
            case ast.Module():
                # Process the module and create script element if we have script-level constructs
                self.current_class = None
                result = super().visit(node)

                # Create script element if we have collected script attributes or functions
                if self.current_script_attributes or self.current_script_functions:
                    script_def = ScriptDef(self.script_name, self.package_name, self.current_script_functions, self.current_script_attributes, None)
                    self.callback.apply(script_def)

                    # Reset script state
                    self.current_script = None
                    self.current_script_attributes = []
                    self.current_script_functions = []

                return result
            case _:
                return node

    def _visit_class_def(self, node):
        parent_class = self.current_class
        class_name = node.name if parent_class is None else f"{parent_class.name()}${node.name}"

        if parent_class is None:
            self.local_classes.add(node.name)

        nested_types = {
            stmt.name: f"{self.package_name}.{class_name}${stmt.name}" if self.package_name else f"{class_name}${stmt.name}"
            for stmt in node.body
            if isinstance(stmt, ast.ClassDef)
        }

        previous_class = self.current_class
        previous_class_attributes = self.current_class_attributes
        previous_class_properties = self.current_class_properties
        previous_nested_types = self.current_class_nested_types
        previous_last_attribute = self.last_attribute

        try:
            self.current_class_nested_types = nested_types

            # Capture all decorator names, not just Micronaut annotations
            # This allows us to detect @dataclass and other non-Micronaut decorators
            decorators = []
            for d in node.decorator_list:
                # First try to get it as a Micronaut decorator
                micronaut_decorator = decorator_to_function(self, d)
                if micronaut_decorator is not None:
                    decorators.append(micronaut_decorator)
                else:
                    # For non-Micronaut decorators, extract the name and create a simple DecoratorDef
                    decorator_name = extract_decorator_name(d)
                    if decorator_name:
                        # Create a DecoratorDef for non-Micronaut decorators
                        simple_decorator = DecoratorDef(decorator_name, decorator_name, None, {}, [])
                        decorators.append(simple_decorator)

            # Extract base classes
            bases = []
            for base in node.bases:
                base_class_def = self._parse_base_class(base)
                if base_class_def:
                    bases.append(base_class_def)

            # Extract type parameters
            type_params = self._parse_type_params(node)

            # Extract class docstring
            class_doc = self._extract_docstring(node)
            self.current_class = JavaClassDef(class_name, self.package_name, bases, decorators, type_params, [], [], [], None, False, [], class_doc)
            self.current_class_attributes = []
            self.current_class_properties = {}
            self.last_attribute = None

            # Check if this is an enum class
            is_enum = self._is_enum_class(node)
            enum_values = []
            if is_enum:
                enum_values = self._extract_enum_values(node)

            try:
                result = super().visit(node)
            finally:
                # Check if this is a dataclass and generate constructor if needed
                is_dataclass = any(
                    (isinstance(dec, ast.Name) and dec.id == "dataclass") or
                    (isinstance(dec, ast.Attribute) and dec.attr == "dataclass")
                    for dec in node.decorator_list
                )

                if is_dataclass and self.current_class.constructor() is None:
                    # Generate constructor from dataclass attributes
                    dataclass_args = []
                    for attr in self.current_class_attributes:
                        # Only include attributes with type annotations (required for dataclass)
                        if attr.typeName() and attr.typeName() != "None":
                            # Create argument with same name as attribute
                            arg_def = ArgumentDef.of(
                                attr.name(),  # arg_name
                                attr.annotation() or "",  # annotation
                                attr.typeName(),  # type_annotation
                                attr.value(),  # default_value (may be None)
                                attr.decorators(),  # decorators
                                None  # param_doc
                            )
                            dataclass_args.append(arg_def)

                    if dataclass_args:
                        # Create constructor function def
                        arguments_def = ArgumentsDef.of(dataclass_args)
                        return_def = ReturnDef.none()
                        dataclass_constructor = JavaFuncDef(
                            "__init__",  # name
                            arguments_def,  # arguments
                            [],  # decorators
                            return_def,  # return_type
                            "",  # ??? (not sure what this is)
                            [],  # ??? (not sure what this is)
                            None,  # func_doc
                            False  # is_abstract
                        )
                        self.current_class = self.current_class.withConstructor(dataclass_constructor)

                # Add collected attributes to the class before applying callback
                for attr in self.current_class_attributes:
                    self.current_class = self.current_class.withAttribute(attr)

                # Add collected properties to the class
                for property_def in self.current_class_properties.values():
                    self.current_class = self.current_class.withProperty(property_def)

                # Set enum information if applicable
                if is_enum:
                    self.current_class = self.current_class.withEnum(True, enum_values)

                self.callback.apply(self.current_class)
            return result
        finally:
            self.current_class = previous_class
            self.current_class_attributes = previous_class_attributes
            self.current_class_properties = previous_class_properties
            self.current_class_nested_types = previous_nested_types
            self.last_attribute = previous_last_attribute

    def _handle_assign(self, node):
        """
        Handle ast.Assign nodes for class attributes.
        """
        # Only handle simple assignments to names (not complex expressions)
        if len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            attr_name = node.targets[0].id
            # Skip special dunder attributes and private attributes
            if not attr_name.startswith('__') and not attr_name.startswith('_'):
                try:
                    # Evaluate the AST expression to get a Python Value
                    code = compile(ast.Expression(body=node.value), filename='<ast>', mode='eval')
                    value = eval(code)
                except Exception:
                    value = None  # Non-evaluable expressions

                # Determine if it's a class variable (static) or instance variable
                # For Micronaut properties, treat class attributes as instance fields
                is_static = False  # Regular Python attributes should be writable
                type_name = None  # No type annotation for simple assignments

                self._track_current_class_constant(attr_name, node.value)
                attr_def = JavaAttributeDef(attr_name, None, type_name, value, [], None, is_static, None)
                self.current_class_attributes.append(attr_def)

    def _handle_ann_assign(self, node):
        """
        Handle ast.AnnAssign nodes for annotated assignments.
        """
        if isinstance(node.target, ast.Name):
            attr_name = node.target.id
            # Skip special dunder attributes and private attributes
            if not attr_name.startswith('__') and not attr_name.startswith('_'):
                # Extract type annotation
                try:
                    annotation = ast.unparse(node.annotation)
                except AttributeError:
                    # Fallback for older Python versions
                    annotation = ast.dump(node.annotation)

                try:
                    # Evaluate the AST expression to get a Python Value
                    if node.value:
                        code = compile(ast.Expression(body=node.value), filename='<ast>', mode='eval')
                        value = eval(code)
                    else:
                        value = None
                except Exception:
                    value = None

                # Check for typing.Annotated and extract decorators from metadata
                decorators = []
                type_name = annotation  # Default to full annotation

                if isinstance(node.annotation, ast.Subscript) and isinstance(node.annotation.value, ast.Name) and node.annotation.value.id == 'Annotated':
                    parsed_annotation, parsed_decorators = self._parse_annotated_type(node.annotation)
                    if parsed_annotation:
                        type_name = parsed_annotation   # Use extracted type for typeName
                        decorators = parsed_decorators  # Add any decorators found

                        # Check if the parsed type annotation is nullable and add @Nullable decorator
                        if self._is_nullable_type_annotation(parsed_annotation):
                            nullable_decorator = DecoratorDef("Nullable", "jakarta.annotation.Nullable", None, {}, [])
                            decorators.append(nullable_decorator)
                else:
                    # Parse type into TypeRef structure
                    type_name = self._parse_type(node.annotation)

                    # For non-Annotated types, check if the type itself is nullable
                    if self._is_nullable_union_type(node.annotation):
                        nullable_decorator = DecoratorDef("Nullable", "jakarta.annotation.Nullable", None, {}, [])
                        decorators.append(nullable_decorator)

                # Determine if static (heuristic)
                # For Micronaut properties, treat annotated attributes as instance fields
                is_static = False

                if node.value:
                    self._track_current_class_constant(attr_name, node.value)
                attr_def = JavaAttributeDef(attr_name, annotation, type_name, value, decorators, None, is_static, None)
                self.current_class_attributes.append(attr_def)
                self.last_attribute = attr_def

    def _literal_constant_value(self, value_node):
        try:
            return True, ast.literal_eval(value_node)
        except Exception:
            return False, None

    def _track_current_class_constant(self, attr_name, value_node):
        if self.current_class is None:
            return
        resolved, value = self._literal_constant_value(value_node)
        if not resolved:
            return
        class_name = self.current_class.name().replace("$", ".")
        self.local_constant_values[f"{class_name}.{attr_name}"] = value

    def _track_local_constant_assignment(self, node):
        if self.in_function:
            return
        targets = []
        value_node = None
        if isinstance(node, ast.Assign):
            targets = node.targets
            value_node = node.value
        elif isinstance(node, ast.AnnAssign):
            targets = [node.target]
            value_node = node.value
        if value_node is None:
            return

        resolved, value = self._literal_constant_value(value_node)
        if not resolved:
            return

        for target in targets:
            if not isinstance(target, ast.Attribute):
                continue
            names = []
            current = target
            while isinstance(current, ast.Attribute):
                names.insert(0, current.attr)
                current = current.value
            if isinstance(current, ast.Name):
                names.insert(0, current.id)
            if names and names[0] in self.local_classes:
                self.local_constant_values[".".join(names)] = value

    def _handle_field_docstring(self, node):
        """
        Handle ast.Expr nodes that might be field docstrings following attribute assignments.
        """
        if isinstance(node.value, (ast.Constant, ast.Str)):
            # Extract the string value
            if isinstance(node.value, ast.Constant) and isinstance(node.value.value, str):
                docstring = node.value.value
            elif isinstance(node.value, ast.Str):
                docstring = node.value.s
            else:
                return

            # If we have a last attribute and no documentation yet, set it
            if self.last_attribute is not None and self.last_attribute.documentation() is None:
                # Create a new attribute with documentation
                updated_attr = JavaAttributeDef(
                    self.last_attribute.name(),
                    self.last_attribute.annotation(),
                    self.last_attribute.typeName(),
                    self.last_attribute.value(),
                    self.last_attribute.decorators(),
                    docstring.strip(),
                    self.last_attribute.isStatic(),
                    None
                )
                # Replace the last attribute in the list
                if self.current_class_attributes and self.current_class_attributes[-1] == self.last_attribute:
                    self.current_class_attributes[-1] = updated_attr
                # Update last_attribute reference
                self.last_attribute = updated_attr

    def _is_script_assignment(self, node):
        if isinstance(getattr(node, "parent", None), ast.ClassDef):
            return False
        """
        Check if an assignment node represents a script-level attribute assignment.
        Script assignments are module-level variable assignments that can be dependency injection targets.
        """
        if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            attr_name = node.targets[0].id
            # Skip special dunder attributes and private attributes
            if not attr_name.startswith('__') and not attr_name.startswith('_'):
                return True
        elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name):
            attr_name = node.target.id
            # Skip special dunder attributes and private attributes
            if not attr_name.startswith('__') and not attr_name.startswith('_'):
                return True
        return False

    def _handle_script_assign(self, node):
        """
        Handle ast.Assign nodes for script attributes.
        """
        # Only handle simple assignments to names (not complex expressions)
        if len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            attr_name = node.targets[0].id
            # Skip special dunder attributes and private attributes
            if not attr_name.startswith('__') and not attr_name.startswith('_'):
                try:
                    # Evaluate the AST expression to get a Python Value
                    code = compile(ast.Expression(body=node.value), filename='<ast>', mode='eval')
                    value = eval(code)
                except Exception:
                    value = None  # Non-evaluable expressions

                # Determine if it's a static attribute (script attributes are typically static)
                is_static = False  # Script attributes should be injectable
                type_name = None  # No type annotation for simple assignments

                attr_def = JavaAttributeDef(attr_name, None, type_name, value, [], None, is_static, None)
                self.current_script_attributes.append(attr_def)

    def _handle_script_ann_assign(self, node):
        """
        Handle ast.AnnAssign nodes for script attributes.
        """
        if isinstance(node.target, ast.Name):
            attr_name = node.target.id
            # Skip special dunder attributes and private attributes
            if not attr_name.startswith('__') and not attr_name.startswith('_'):
                # Extract type annotation
                try:
                    annotation = ast.unparse(node.annotation)
                except AttributeError:
                    # Fallback for older Python versions
                    annotation = ast.dump(node.annotation)

                try:
                    # Evaluate the AST expression to get a Python Value
                    if node.value:
                        code = compile(ast.Expression(body=node.value), filename='<ast>', mode='eval')
                        value = eval(code)
                    else:
                        value = None
                except Exception:
                    value = None

                # Check for typing.Annotated and extract decorators from metadata
                decorators = []
                type_name = annotation  # Default to full annotation

                if isinstance(node.annotation, ast.Subscript) and isinstance(node.annotation.value, ast.Name) and node.annotation.value.id == 'Annotated':
                    parsed_annotation, parsed_decorators = self._parse_annotated_type(node.annotation)
                    if parsed_annotation:
                        type_name = parsed_annotation   # Use extracted type for typeName
                        decorators = parsed_decorators  # Add any decorators found

                        # Check if the parsed type annotation is nullable and add @Nullable decorator
                        if self._is_nullable_type_annotation(parsed_annotation):
                            nullable_decorator = DecoratorDef("Nullable", "jakarta.annotation.Nullable", None, {}, [])
                            decorators.append(nullable_decorator)
                else:
                    # Parse type into TypeRef structure
                    type_name = self._parse_type(node.annotation)

                    # For non-Annotated types, check if the type itself is nullable
                    if self._is_nullable_union_type(node.annotation):
                        nullable_decorator = DecoratorDef("Nullable", "jakarta.annotation.Nullable", None, {}, [])
                        decorators.append(nullable_decorator)

                # Script attributes are typically static
                is_static = False

                attr_def = JavaAttributeDef(attr_name, annotation, type_name, value, decorators, None, is_static, None)
                self.current_script_attributes.append(attr_def)

    def _is_script_function(self, node):
        parent = getattr(node, "parent", None)
        if isinstance(parent, ast.ClassDef) or node.name == 'micronaut_annotation':
            return False

        decorators = [
            decorator_to_function(self, d)
            for d in node.decorator_list
            if decorator_to_function(self, d) is not None
        ]
        if not decorators:
            return False
        """
        Check if a function node represents a script-level function.
        Script functions are module-level functions that can be executed.
        """
        # For now, we'll consider all module-level functions as script functions
        # In the future, we might want to check for specific decorators like @Executable
        return True

    def _handle_script_function(self, func_def):
        """
        Handle ast.FunctionDef nodes for script functions.
        """
        # Add the function to the script
        self.current_script_functions.append(func_def)

    def _is_enum_class(self, node):
        """
        Determine if the given ClassDef node represents an enum class.
        An enum class typically inherits from enum.Enum.
        """
        for base in node.bases:
            if isinstance(base, ast.Name) and base.id == 'Enum':
                return True
            elif isinstance(base, ast.Attribute):
                # Handle enum.Enum
                if (isinstance(base.value, ast.Name) and base.value.id == 'enum' and
                    base.attr == 'Enum'):
                    return True
        return False

    def _extract_enum_values(self, node):
        """
        Extract enum values from the class body.
        Enum values are typically uppercase assignments to names.
        """
        enum_values = []
        for item in node.body:
            if isinstance(item, ast.Assign):
                # Check if this is a simple assignment to a name
                if (len(item.targets) == 1 and isinstance(item.targets[0], ast.Name)):
                    name = item.targets[0].id
                    # Skip special/private attributes
                    if not name.startswith('_') and not name.startswith('__'):
                        # For enums, values are typically uppercase or specific patterns
                        # For now, collect all non-private assignments as potential enum values
                        enum_values.append(name)
        return enum_values

    def _parse_annotated_type(self, annotation_node):
        """
        Parse a typing.Annotated type annotation and extract the actual type and metadata decorators.
        Returns (type_annotation, decorators_list) where type_annotation is now a TypeRef
        """
        decorators = []
        type_annotation = TypeRef("object")  # default fallback

        # Parse the Annotated subscript arguments
        if isinstance(annotation_node, ast.Subscript):
            # Check if it's Annotated[...]
            if isinstance(annotation_node.value, ast.Name) and annotation_node.value.id == 'Annotated':
                # Extract from AST nodes
                args = self._extract_subscript_args(annotation_node)
                if args:
                    try:
                        type_annotation = self._parse_type(args[0])
                    except:
                        type_annotation = TypeRef("object")  # fallback
                    # Remaining args are metadata
                    for metadata in args[1:]:
                        if isinstance(metadata, ast.Call):
                            decorator = self._parse_metadata_call(metadata)
                            if decorator:
                                decorators.append(decorator)
                        elif isinstance(metadata, ast.Name):
                            # Handle simple decorator names like NotBlank or Inject
                            decorator_reference = metadata.id
                            decorator = self.to_decorator_from_reference(decorator_reference)
                            decorators.append(decorator)
                        elif isinstance(metadata, ast.Attribute):
                            # Handle qualified decorator names like validation.NotBlank
                            decorator_name = f"{metadata.value.id}.{metadata.attr}"
                            decorator = DecoratorDef(decorator_name, decorator_name, None, {}, [])
                            decorators.append(decorator)
                        # For other metadata types (strings, numbers), we could handle them
                        # but for now, focus on decorator names and calls
                else:
                    # Fallback to original annotation if no args
                    try:
                        type_name = ast.unparse(annotation_node) if hasattr(ast, 'unparse') else ast.dump(annotation_node)
                        type_annotation = TypeRef(type_name)
                    except:
                        type_annotation = TypeRef("object")
            else:
                # Not Annotated, fallback to original annotation
                try:
                    type_name = ast.unparse(annotation_node) if hasattr(ast, 'unparse') else ast.dump(annotation_node)
                    type_annotation = TypeRef(type_name)
                except:
                    type_annotation = TypeRef("object")
        else:
            # Not a subscript, fallback to original annotation
            try:
                type_name = ast.unparse(annotation_node) if hasattr(ast, 'unparse') else ast.dump(annotation_node)
                type_annotation = TypeRef(type_name)
            except:
                type_annotation = TypeRef("object")

        return type_annotation, decorators

    def to_decorator_from_reference(self, decorator_reference):
        return self.to_decorator_from_reference_with_members(decorator_reference, {})

    def to_decorator_from_reference_with_members(self, decorator_reference, members):
        known_decorator = self.known_decorators.get(decorator_reference)
        if known_decorator:
            # Use the fully qualified annotation name from the known decorator
            annotation_name = known_decorator.annotationName()
            repeated_name = known_decorator.repeatedName()
            decorator = DecoratorDef(decorator_reference, annotation_name, repeated_name, members,
                                     known_decorator.stereotypes())
        else:
            # Check if this is an imported type
            imported_name = self.imported_types.get(decorator_reference)
            if imported_name:
                # Use the fully qualified name from the import
                decorator = DecoratorDef(decorator_reference, imported_name, None, members, [])
            else:
                # Not a known decorator or imported type, use as-is
                decorator = DecoratorDef(decorator_reference, decorator_reference, None, members, [])
        return decorator

    def _extract_subscript_args(self, subscript_node):
        """
        Extract arguments from a subscript AST node.
        """
        args = []
        slice_node = subscript_node.slice

        # Handle different slice formats
        if isinstance(slice_node, ast.Index):  # Python < 3.9
            slice_node = slice_node.value

        if isinstance(slice_node, ast.Tuple):
            args = slice_node.elts
        elif slice_node:  # Single argument
            args = [slice_node]

        return args

    def _resolve_dotted_name(self, parts):
        """
        Resolve a dotted name given as a list of parts, applying import alias mapping and
        micronaut -> io.micronaut normalization.
        """
        if not parts:
            return ""
        root = parts[0]
        tail = parts[1:]
        if tail and root in self.local_classes:
            nested_name = f"{root}${'$'.join(tail)}"
            return f"{self.package_name}.{nested_name}" if self.package_name else nested_name
        base = self.imported_types.get(root) or self.java_type_assignments.get(root, root)
        if len(tail) == 1:
            nested_type = self.java_type_assignments.get(tail[0])
            if nested_type and (
                base == root
                or nested_type.startswith(f"{base}.")
                or nested_type.startswith(f"{base}$")
            ):
                return nested_type
        full = ".".join([base] + tail) if tail else base
        if full.startswith("micronaut.") or full.startswith("io.micronaut."):
            full = self._to_java_import_module(full)
        if full.startswith("micronaut."):
            full = f"io.{full}"
        return full

    def _to_java_import_module(self, module_name):
        """
        Convert Python-safe Java package segments such as async_ back to async.
        """
        return ".".join(
            part[:-1] if part.endswith("_") and keyword.iskeyword(part[:-1]) else part
            for part in module_name.split(".")
        )

    def _resolve_local_type_name(self, type_name):
        nested_type = self.current_class_nested_types.get(type_name)
        if nested_type:
            return nested_type
        if type_name in self.local_classes:
            return f"{self.package_name}.{type_name}" if self.package_name else type_name
        return None

    def _extract_type_name(self, type_node):
        """
        Extract a type name from an AST type node.
        Handles quoted strings by unquoting them and qualifying local class names.
        """
        if isinstance(type_node, ast.Constant):
            # Handle string literals like 'Engine' or "Engine"
            if isinstance(type_node.value, str):
                type_name = type_node.value
                # Check if this is a local class and qualify it
                local_name = self._resolve_local_type_name(type_name)
                if local_name:
                    return local_name
                # Check if this is an imported type
                imported_name = self.imported_types.get(type_name)
                if imported_name:
                    return imported_name
                # Check if this is a Java type that was imported
                return self.java_type_assignments.get(type_name, type_name)
        elif isinstance(type_node, ast.Str):
            # Handle older Python versions with ast.Str
            type_name = type_node.s
            # Check if this is a local class and qualify it
            local_name = self._resolve_local_type_name(type_name)
            if local_name:
                return local_name
            # Check if this is an imported type
            imported_name = self.imported_types.get(type_name)
            if imported_name:
                return imported_name
            # Check if this is a Java type that was imported
            return self.java_type_assignments.get(type_name, type_name)
        elif isinstance(type_node, ast.Name):
            # Check if this is an imported type first
            imported_name = self.imported_types.get(type_node.id)
            if imported_name:
                return imported_name
            # Check if this is a local class
            local_name = self._resolve_local_type_name(type_node.id)
            if local_name:
                return local_name
            # Then check if this is a Java type that was imported
            return self.java_type_assignments.get(type_node.id, type_node.id)
        elif isinstance(type_node, ast.Attribute):
            # Handle qualified names like typing.List
            names = []
            current = type_node
            while isinstance(current, ast.Attribute):
                names.insert(0, current.attr)
                current = current.value
            if isinstance(current, ast.Name):
                names.insert(0, current.id)
            return self._resolve_dotted_name(names)
        elif isinstance(type_node, ast.Subscript):
            # Handle generic types like List[SomeClass] or Dict[str, SomeClass]
            base_name = self._extract_type_name(type_node.value)  # e.g., "List"

            # Process the subscript arguments
            args = self._extract_subscript_args(type_node)
            if args:
                # Recursively extract type names for each argument
                arg_names = [self._extract_type_name(arg) for arg in args]
                return f"{base_name}[{', '.join(arg_names)}]"
            else:
                return base_name
        elif isinstance(type_node, ast.BinOp) and isinstance(type_node.op, ast.BitOr):
            # Handle union types like X | Y, extract non-None types
            return self._extract_union_type(type_node)
        elif hasattr(ast, 'unparse'):
            return ast.unparse(type_node)
        else:
            return ast.dump(type_node)

    def _extract_union_type(self, type_node):
        """
        Extract type from union, removing None types.
        For unions like X | Y | None, returns X | Y.
        For unions like X | None, returns X.
        """
        if isinstance(type_node, ast.BinOp) and isinstance(type_node.op, ast.BitOr):
            left = self._extract_union_type(type_node.left)
            right = self._extract_union_type(type_node.right)
            types = []
            if left and left != 'None':
                types.append(left)
            if right and right != 'None':
                types.append(right)
            return ' | '.join(types) if types else 'object'
        else:
            return self._extract_type_name(type_node)

    def _is_nullable_union_type(self, type_node):
        """
        Check if type annotation represents a nullable union type.
        Returns True if the type contains None in a union.
        """
        if isinstance(type_node, ast.BinOp) and isinstance(type_node.op, ast.BitOr):
            left_nullable = self._is_nullable_union_type(type_node.left)
            right_nullable = self._is_nullable_union_type(type_node.right)
            if left_nullable or right_nullable:
                return True
            left = self._extract_type_name(type_node.left)
            right = self._extract_type_name(type_node.right)
            return left == 'None' or right == 'None'
        elif isinstance(type_node, ast.Name):
            return type_node.id == 'None'
        elif isinstance(type_node, ast.Constant) and type_node.value is None:
            return True
        return False

    def _is_nullable_type_annotation(self, type_annotation):
        """
        Check if a type annotation represents a nullable type.
        This checks for union types containing None.
        """
        if isinstance(type_annotation, str):
            # Legacy string handling
            return type_annotation == 'None' or any(part.strip() == 'None' for part in type_annotation.split('|'))
        elif hasattr(type_annotation, 'name'):
            # TypeRef object
            return (
                type_annotation.name() == 'None'
                or any(part.strip() == 'None' for part in type_annotation.name().split('|'))
                or any(self._is_nullable_type_annotation(type_arg) for type_arg in type_annotation.typeArguments())
            )
        else:
            return False

    def _parse_metadata_call(self, call_node):
        """
        Parse a metadata call like Gt(0) into a DecoratorDef.
        """
        if isinstance(call_node, ast.Call) and isinstance(call_node.func, ast.Name):
            decorator_name = call_node.func.id
            # Extract arguments
            members = {}

            # For positional args
            for i, arg in enumerate(call_node.args):
                value = convert_ast_value(arg, self)
                if i == 0:
                    members['value'] = value
                else:
                    members[f'arg{i}'] = value

            # For keyword args
            for kw in call_node.keywords:
                if kw.arg:
                    members[kw.arg] = convert_ast_value(kw.value, self)

            # Create DecoratorDef with annotationName = name (assuming it's a Micronaut annotation)
            return self.to_decorator_from_reference_with_members(decorator_name, members)

        return None

    def _parse_property_decorators(self, func_node):
        """
        Parse property decorators from a function node.
        Returns (property_name, property_type) if this is a property decorator, None otherwise.
        property_type can be 'getter', 'setter', or 'deleter'
        """
        for decorator in func_node.decorator_list:
            if isinstance(decorator, ast.Name) and decorator.id == "property":
                # This is a @property getter
                return func_node.name, "getter"
            elif isinstance(decorator, ast.Attribute):
                # Handle @property.setter or @property.deleter
                if isinstance(decorator.value, ast.Name) and decorator.value.id == func_node.name:
                    if decorator.attr == "setter":
                        return func_node.name, "setter"
                    elif decorator.attr == "deleter":
                        return func_node.name, "deleter"
        return None

    def _is_python_property_decorator(self, decorator, func_name):
        if isinstance(decorator, ast.Name) and decorator.id == "property":
            return True
        if isinstance(decorator, ast.Attribute):
            return (
                decorator.attr in ("setter", "deleter")
                and isinstance(decorator.value, ast.Name)
                and decorator.value.id == func_name
            )
        return False

    def _handle_property_function(self, property_name, property_type, func_node):
        """
        Handle a property function (getter, setter, or deleter).
        Creates or updates PropertyDef instances in self.current_class_properties.
        """
        if property_name not in self.current_class_properties:
            # Create new property
            self.current_class_properties[property_name] = PropertyDef(property_name)

        property_def = self.current_class_properties[property_name]

        # Parse function arguments and return type
        arguments = self.parse_function_arguments(func_node)
        return_type_annotation = self.parse_function_return_type(func_node)
        func_doc = self._extract_docstring(func_node)

        decorators = [
            decorator_to_function(self, d)
            for d in func_node.decorator_list
            if not self._is_python_property_decorator(d, func_node.name)
            and decorator_to_function(self, d) is not None
        ]

        is_abstract = is_abstract_method(func_node)
        is_static = is_static_method(func_node)

        func_def = JavaFuncDef(func_node.name, arguments, decorators, return_type_annotation, "", [], func_doc, is_abstract, is_static)

        # Update the property based on type
        if property_type == "getter":
            property_def = property_def.withGetter(func_def)
        elif property_type == "setter":
            property_def = property_def.withSetter(func_def)

        self.current_class_properties[property_name] = property_def

    def _parse_type_params(self, node):
        """
        Parse type parameters from an ast.ClassDef node.
        Returns a list of TypeVar objects.
        Handles both Python 3.12+ type_params and Generic[T] syntax.
        """
        type_params = []
        TypeVar = java.type("io.micronaut.python.processing.visitor.TypeVar")

        # Check if the node has type_params (Python 3.12+)
        if hasattr(node, 'type_params') and node.type_params:
            for type_param in node.type_params:
                if isinstance(type_param, ast.TypeVar):
                    # Extract name
                    name = type_param.name

                    # Extract bound if present
                    bound = None
                    if type_param.bound is not None:
                        bound = self._extract_type_name(type_param.bound)

                    # Extract constraints (for TypeVar with constraints)
                    constraints = []
                    if hasattr(type_param, 'constraints') and type_param.constraints:
                        for constraint in type_param.constraints:
                            constraint_name = self._extract_type_name(constraint)
                            constraints.append(constraint_name)

                    # Create TypeVar object
                    type_var = TypeVar(name, bound, constraints)
                    type_params.append(type_var)
        else:
            # Check for Generic[T] syntax in bases (Python < 3.12)
            for base in node.bases:
                if isinstance(base, ast.Subscript):
                    # Check if it's Generic[...]
                    if isinstance(base.value, ast.Name) and base.value.id == 'Generic':
                        # Extract type arguments from Generic[T1, T2, ...]
                        args = self._extract_subscript_args(base)
                        for arg in args:
                            if isinstance(arg, ast.Name):
                                # Simple TypeVar reference like T
                                name = arg.id
                                type_var = TypeVar(name, None, [])
                                type_params.append(type_var)
                            elif isinstance(arg, ast.Call) and isinstance(arg.func, ast.Name) and arg.func.id == 'TypeVar':
                                # TypeVar call like TypeVar('T', bound=SomeType)
                                type_var = self._parse_type_var_call(arg)
                                if type_var:
                                    type_params.append(type_var)

        return type_params

    def _parse_function_type_params(self, func_node):
        """
        Parse type parameters from an ast.FunctionDef node.
        Returns a list of TypeVar objects.
        Handles Python 3.12+ type_params syntax and older syntax by parsing from type annotations.
        """
        type_params = []
        TypeVar = java.type("io.micronaut.python.processing.visitor.TypeVar")

        # Check if the function node has type_params (Python 3.12+)
        if hasattr(func_node, 'type_params') and func_node.type_params:
            for type_param in func_node.type_params:
                if isinstance(type_param, ast.TypeVar):
                    # Extract name
                    name = type_param.name

                    # Extract bound if present
                    bound = None
                    if type_param.bound is not None:
                        bound = self._extract_type_name(type_param.bound)

                    # Extract constraints (for TypeVar with constraints)
                    constraints = []
                    if hasattr(type_param, 'constraints') and type_param.constraints:
                        for constraint in type_param.constraints:
                            constraint_name = self._extract_type_name(constraint)
                            constraints.append(constraint_name)

                    # Create TypeVar object
                    type_var = TypeVar(name, bound, constraints)
                    type_params.append(type_var)
        else:
            # Try to parse type parameters from function annotations (fallback for older Python)
            type_params = self._parse_type_params_from_annotations(func_node)

        return type_params

    def _parse_type_params_from_annotations(self, func_node):
        """
        Parse type parameters from function annotations by looking for TypeVar usage.
        This is a fallback for Python versions that don't support type_params.
        """
        type_params = []
        TypeVar = java.type("io.micronaut.python.processing.visitor.TypeVar")

        # Look for type parameters in the function signature by examining type annotations
        # For syntax like def func[S](param: S), we need to parse S from the annotations

        # Check if the function name contains type parameters (e.g., def func[S](...))
        func_name = func_node.name

        if '[' in func_name and func_name.endswith(']'):
            # Extract type parameter names from function name
            # e.g., "singleton_list[S]" -> ["S"]
            try:
                bracket_content = func_name.split('[', 1)[1].rstrip(']')
                if bracket_content:
                    param_names = [name.strip() for name in bracket_content.split(',')]
                    for param_name in param_names:
                        # Create TypeVar objects for each parameter name
                        type_var = TypeVar(param_name, None, [])
                        type_params.append(type_var)
            except:
                pass

        return type_params

    def _parse_type_var_call(self, call_node):
        """
        Parse a TypeVar call like TypeVar('T', bound=SomeType) into a TypeVar object.
        Returns a TypeVar object or None if parsing fails.
        """
        if not (isinstance(call_node, ast.Call) and isinstance(call_node.func, ast.Name) and call_node.func.id == 'TypeVar'):
            return None

        TypeVar = java.type("io.micronaut.python.processing.visitor.TypeVar")

        # Extract arguments
        args = call_node.args
        kwargs = {kw.arg: kw.value for kw in call_node.keywords if kw.arg}

        # First argument should be the name (string literal)
        name = None
        if args and len(args) >= 1:
            try:
                name = ast.literal_eval(args[0])
            except:
                return None

        if not name or not isinstance(name, str):
            return None

        # Extract bound
        bound = None
        if 'bound' in kwargs:
            bound = self._extract_type_name(kwargs['bound'])
        elif len(args) >= 2:
            bound = self._extract_type_name(args[1])

        # Extract constraints
        constraints = []
        if 'constraints' in kwargs:
            # constraints should be a list/tuple
            constraint_node = kwargs['constraints']
            if isinstance(constraint_node, (ast.List, ast.Tuple)):
                for item in constraint_node.elts:
                    constraint_name = self._extract_type_name(item)
                    constraints.append(constraint_name)

        return TypeVar(name, bound, constraints)

    def _parse_base_class(self, base_node):
        """
        Parse a base class AST node and return a TypeDef.
        Handles simple names like 'str' and subscripted types like 'MyBase[str]'.
        """
        return self._parse_type(base_node)

    def _current_class_is_protocol(self):
        """
        Returns True if the current class directly extends typing.Protocol.
        """
        if self.current_class is None:
            return False
        for base in self.current_class.bases():
            if is_protocol_type_name(base.name()):
                return True
        return False

    def _current_class_has_external_base(self):
        """
        Returns True if the current class extends a non-local base type.
        """
        if self.current_class is None:
            return False
        for base in self.current_class.bases():
            name = base.name()
            if name in ("object", "abc.ABC") or is_protocol_type_name(name):
                continue
            simple_name = name.rsplit(".", 1)[-1]
            if simple_name not in self.local_classes:
                return True
        return False

    def _parse_type(self, type_node):
        """
        Parse a type AST node and return a TypeDef.
        Handles simple names, qualified names, and subscripted types recursively.
        """
        if isinstance(type_node, ast.Name):
            # Simple type like 'str' or 'MyBase'
            name = self._extract_type_name(type_node)
            return TypeRef(name)
        elif isinstance(type_node, ast.Attribute):
            # Qualified type like 'module.MyClass'
            name = self._extract_type_name(type_node)
            return TypeRef(name)
        elif isinstance(type_node, ast.Constant):
            # Handle string literals (forward references) like "Engine"
            if isinstance(type_node.value, str):
                name = self._extract_type_name(type_node)
                return TypeRef(name)
            else:
                # Fallback for other constant types
                name = str(type_node.value)
                return TypeRef(name)
        elif isinstance(type_node, ast.Subscript):
            # Generic type like 'MyBase[str]' or 'dict[str, int]'
            base_name = self._extract_type_name(type_node.value)
            type_args = self._extract_subscript_args(type_node)
            # Recursively parse each type argument
            type_arg_defs = [self._parse_type(arg) for arg in type_args]
            return TypeRef(base_name, type_arg_defs)
        elif isinstance(type_node, ast.BinOp) and isinstance(type_node.op, ast.BitOr):
            # Preserve nullable PEP 604 unions so Java type resolution can box primitives.
            return TypeRef(self._extract_union_type_annotation(type_node))
        else:
            # Fallback for other expression types
            try:
                name = ast.unparse(type_node) if hasattr(ast, 'unparse') else ast.dump(type_node)
                return TypeRef(name)
            except:
                return None

    def _extract_union_type_annotation(self, type_node):
        if isinstance(type_node, ast.BinOp) and isinstance(type_node.op, ast.BitOr):
            left = self._extract_union_type_annotation(type_node.left)
            right = self._extract_union_type_annotation(type_node.right)
            return f"{left} | {right}"
        return self._extract_type_name(type_node)

    def _track_java_type_assignments(self, node):
        """
        Track assignments that look like java.type() calls.
        This helps resolve fully qualified names for imported Java types.
        """
        if len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            var_name = node.targets[0].id
            # Check if the value is a call to java.type()
            if isinstance(node.value, ast.Call) and isinstance(node.value.func, ast.Attribute):
                if (isinstance(node.value.func.value, ast.Name) and
                    node.value.func.value.id == 'java' and
                    node.value.func.attr == 'type' and
                    len(node.value.args) == 1):
                    # Extract the string value using ast.literal_eval which handles both Constant and Str
                    try:
                        full_qualified_name = ast.literal_eval(node.value.args[0])
                        if isinstance(full_qualified_name, str):
                            self.java_type_assignments[var_name] = full_qualified_name
                    except (ValueError, TypeError):
                        # Fallback to manual extraction for edge cases
                        arg_node = node.value.args[0]
                        if hasattr(arg_node, 'value') and isinstance(arg_node.value, str):
                            self.java_type_assignments[var_name] = arg_node.value
                        elif hasattr(arg_node, 's') and isinstance(arg_node.s, str):
                            self.java_type_assignments[var_name] = arg_node.s

    def _extract_docstring(self, node):
        """
        Extract the docstring from a class or function node.
        In Python AST, docstrings are the first statement if it's a string literal.
        """
        if hasattr(node, 'body') and node.body:
            first_stmt = node.body[0]
            if isinstance(first_stmt, ast.Expr) and isinstance(first_stmt.value, ast.Constant):
                # Python 3.8+ uses ast.Constant for string literals
                if isinstance(first_stmt.value.value, str):
                    return first_stmt.value.value
            elif isinstance(first_stmt, ast.Expr) and isinstance(first_stmt.value, ast.Str):
                # Python < 3.8 uses ast.Str for string literals
                return first_stmt.value.s
        return None

    def parse_function_arguments(self, func_node):
        """
        Parse the arguments of an ast.FunctionDef node and return ArgumentsDef.
        """
        args_list = func_node.args.args
        defaults = func_node.args.defaults

        # Only the last len(defaults) arguments have defaults
        num_no_defaults = len(args_list) - len(defaults)
        default_values = [None] * num_no_defaults + defaults

        # Skip 'self' parameter for instance methods (methods inside classes)
        # Check if this is an instance method by looking for a 'self' parameter
        skip_self = (len(args_list) > 0 and args_list[0].arg == 'self')

        # Extract parameter documentation from docstring
        param_docs = extract_parameter_documentation(func_node)

        arguments = []
        for i, arg in enumerate(args_list):
            arg_name = arg.arg

                    # Skip self parameter for instance methods
            if skip_self and arg_name == 'self':
                continue

            # Extract type annotation if present
            annotation = ""
            type_annotation = None
            decorators = []
            if hasattr(arg, 'annotation') and arg.annotation is not None:
                try:
                    annotation = ast.unparse(arg.annotation)
                except AttributeError:
                    annotation = ast.dump(arg.annotation)

                # Check for typing.Annotated and extract decorators from metadata
                if isinstance(arg.annotation, ast.Subscript) and isinstance(arg.annotation.value, ast.Name) and arg.annotation.value.id == 'Annotated':
                    parsed_type, parsed_decorators = self._parse_annotated_type(arg.annotation)
                    type_annotation = parsed_type   # Use extracted type for typeAnnotation
                    decorators = parsed_decorators  # Add any decorators found
                else:
                    # Parse type into TypeRef structure
                    type_annotation = self._parse_type(arg.annotation)

                # Check for nullable union types and add @Nullable decorator
                if self._is_nullable_union_type(arg.annotation):
                    nullable_decorator = DecoratorDef("Nullable", "jakarta.annotation.Nullable", None, {}, [])
                    decorators.append(nullable_decorator)


            # Get default value
            default_value = default_values[i]
            if default_value is not None:
                try:
                    # Try to evaluate the value
                    default_value = ast.literal_eval(default_value)
                except Exception:
                    default_value = None

            # Get parameter documentation
            param_doc = param_docs.get(arg_name, None)

            arguments.append(ArgumentDef.of(arg_name, annotation, type_annotation, default_value, decorators, param_doc))

        return ArgumentsDef.of(arguments)

    def parse_function_return_type(self, func_node):
        """
        Parse the return type annotation of an ast.FunctionDef node and return a ReturnDef.
        """
        if hasattr(func_node, 'returns') and func_node.returns is not None:
            # Check for typing.Annotated and extract decorators from metadata
            if isinstance(func_node.returns, ast.Subscript) and isinstance(func_node.returns.value, ast.Name) and func_node.returns.value.id == 'Annotated':
                parsed_type, parsed_decorators = self._parse_annotated_type(func_node.returns)
                return ReturnDef.of(parsed_type, parsed_decorators)
            else:
                # Parse type into TypeRef structure
                type_annotation = self._parse_type(func_node.returns)
                return ReturnDef.of(type_annotation)

        return ReturnDef.none()

def is_property_decorator(funcdef):
    """
    Returns True if the ast.FunctionDef has a @property decorator.
    """
    for dec in funcdef.decorator_list:
        if isinstance(dec, ast.Name) and dec.id == "property":
            return True
        elif isinstance(dec, ast.Attribute) and dec.attr == "property":
            return True
    return False

def decorator_to_function(visitor, node):
    DecoratorDef = java.type("io.micronaut.python.processing.visitor.DecoratorDef")

    match node:
        # when only a decorator is specified it is represented as ast.Name with an ID
        case ast.Name():
            decorator_declaration = visitor.known_decorators.get(node.id)
            if decorator_declaration is not None:
                return decorator_declaration
            else:
                # Check if this is an imported Micronaut/jakarta.inject annotation
                imported_name = visitor.imported_types.get(node.id)
                if imported_name:
                    # Create a DecoratorDef for the Micronaut/jakarta.inject annotation
                    return DecoratorDef(node.id, imported_name, None, {}, [])
                else:
                    # If not a known micronaut decorator, treat as direct annotation
                    return None
        case ast.Attribute():
            # Handle qualified decorator names like alias.Decorator used without parentheses
            names = []
            current = node
            while isinstance(current, ast.Attribute):
                names.insert(0, current.attr)
                current = current.value
            if isinstance(current, ast.Name):
                names.insert(0, current.id)
            simple_name = names[-1] if names else None
            # Resolve through visitor alias mapping if available
            resolved_name = '.'.join(names)
            if visitor is not None and hasattr(visitor, '_resolve_dotted_name'):
                resolved_name = visitor._resolve_dotted_name(names)
            return DecoratorDef(simple_name or resolved_name, resolved_name, None, {}, [])
        # when a decorator takes argument values it is represented by ast.Call
        # here we parse out the constants to the call and set them as the named
        # values to the decorator
        case ast.Call():
            # Support both simple names and qualified attributes as decorator functions
            # Determine decorator simple name and resolved annotation name
            if isinstance(node.func, ast.Name):
                decorator_name = node.func.id
                decorator_declaration = visitor.known_decorators.get(decorator_name)
                resolved_decorator_fqn = None
                if visitor is not None:
                    # Try imported types first, then java.type assignments
                    resolved_decorator_fqn = visitor.imported_types.get(decorator_name) or visitor.java_type_assignments.get(decorator_name, decorator_name)
            elif isinstance(node.func, ast.Attribute):
                # Build qualified name parts for alias resolution
                names = []
                current = node.func
                while isinstance(current, ast.Attribute):
                    names.insert(0, current.attr)
                    current = current.value
                if isinstance(current, ast.Name):
                    names.insert(0, current.id)
                decorator_name = names[-1] if names else ''
                decorator_declaration = visitor.known_decorators.get(decorator_name)
                resolved_decorator_fqn = '.'.join(names)
                if visitor is not None and hasattr(visitor, '_resolve_dotted_name'):
                    resolved_decorator_fqn = visitor._resolve_dotted_name(names)
            else:
                # Unknown node.func form; fallback
                decorator_name = getattr(getattr(node.func, 'id', None), '__str__', lambda: '')() or 'unknown'
                decorator_declaration = visitor.known_decorators.get(decorator_name)
                resolved_decorator_fqn = decorator_name

            if decorator_declaration is not None:
                members = extract_call_arguments_with_defaults(decorator_declaration, node, visitor)
                return DecoratorDef(
                    decorator_name,
                    decorator_declaration.annotationName(),
                    decorator_declaration.repeatedName(),
                    members,
                    decorator_declaration.stereotypes()
                )
            else:
                # Direct annotation or Java annotation used as a decorator
                members = extract_call_arguments_with_defaults(None, node, visitor)
                # Resolve names in member values
                resolved_members = {}
                for key, value in members.items():
                    if isinstance(value, str):
                        if visitor is not None:
                            imported_name = visitor.imported_types.get(value)
                            if imported_name:
                                resolved_members[key] = imported_name
                                continue
                            resolved_value = visitor.java_type_assignments.get(value, value)
                            if resolved_value == value:
                                local_value = visitor._resolve_local_type_name(value) if hasattr(visitor, '_resolve_local_type_name') else None
                                if local_value:
                                    resolved_value = local_value
                            resolved_members[key] = resolved_value
                        else:
                            resolved_members[key] = value
                    else:
                        resolved_members[key] = value

                # resolved_decorator_fqn may be None for simple names
                if resolved_decorator_fqn is None:
                    resolved_decorator_fqn = decorator_name
                    if visitor is not None:
                        resolved_decorator_fqn = visitor.imported_types.get(decorator_name) or visitor.java_type_assignments.get(decorator_name, decorator_name)

                return DecoratorDef(decorator_name, resolved_decorator_fqn, None, resolved_members, [])
        case _:
            return None


def convert_ast_value(node, visitor=None):
    """
    Convert an AST node to a Python value, handling complex expressions like lists.
    If visitor is provided, resolve Java type names to fully qualified names and Java constants to their values.
    """
    # Handle different AST node types first, before trying ast.literal_eval
    if isinstance(node, ast.Name):
        # Class references - check if this is a type that should be resolved
        name = node.id
        if visitor is not None:
            # Check imported types first
            imported_name = visitor.imported_types.get(name)
            if imported_name:
                return imported_name
            # Then check Java type assignments
            resolved_name = visitor.java_type_assignments.get(name, name)
            if resolved_name != name:
                return resolved_name
            local_name = visitor._resolve_local_type_name(name) if hasattr(visitor, '_resolve_local_type_name') else None
            if local_name:
                return local_name
            return name
        else:
            return name
    elif isinstance(node, ast.List):
        # Handle lists like [str, int]
        return [convert_ast_value(elt, visitor) for elt in node.elts]
    elif isinstance(node, ast.Tuple):
        # Handle tuples
        return tuple(convert_ast_value(elt, visitor) for elt in node.elts)
    elif isinstance(node, ast.Dict):
        return {
            convert_ast_value(key, visitor): convert_ast_value(value, visitor)
            for key, value in zip(node.keys, node.values)
            if key is not None
        }
    elif isinstance(node, ast.Call):
        nested_decorator = convert_ast_call_to_decorator(node, visitor)
        if nested_decorator is not None:
            return nested_decorator
    elif isinstance(node, ast.Attribute):
        # Handle qualified names like module.Class or constant references like StringUtils.TRUE
        names = []
        current = node
        while isinstance(current, ast.Attribute):
            names.insert(0, current.attr)
            current = current.value
        if isinstance(current, ast.Name):
            names.insert(0, current.id)

        # Special handling for __qualname__ and __name__ attributes on local classes/functions
        if visitor is not None and len(names) == 2 and names[1] in ('__qualname__', '__name__'):
            if names[0] in visitor.java_type_assignments:
                return visitor.java_type_assignments[names[0]]
            else:
                local_name = visitor._resolve_local_type_name(names[0]) if hasattr(visitor, '_resolve_local_type_name') else None
                if local_name:
                    return local_name

        # Check if this might be a Java constant reference (e.g., StringUtils.TRUE)
        if visitor is not None and len(names) >= 2:
            constant_key = '.'.join(names)
            if hasattr(visitor, 'local_constant_values') and constant_key in visitor.local_constant_values:
                return visitor.local_constant_values[constant_key]

            # Try to resolve as a Java constant
            constant_value = _resolve_java_constant(visitor, names)
            if constant_value is not None:
                return constant_value

        # Fallback for __qualname__ and __name__ attributes
        if visitor is not None and len(names) >= 2 and names[-1] in ('__qualname__', '__name__'):
            base_name = '.'.join(names[:-1])
            if base_name:
                return f"{visitor.package_name}.{base_name}"

        # Return as qualified name string, applying alias resolution when possible
        if visitor is not None and hasattr(visitor, '_resolve_dotted_name'):
            return visitor._resolve_dotted_name(names)
        return '.'.join(names)

    # Try to evaluate the value if it's a constant or simple expression
    try:
        return ast.literal_eval(node)
    except Exception:
        # Fallback to AST dump for complex expressions
        return ast.dump(node) if hasattr(ast, 'dump') else str(node)

def convert_ast_call_to_decorator(node, visitor=None):
    if visitor is None or not isinstance(node, ast.Call):
        return None
    decorator_reference = extract_decorator_name(node.func)
    if decorator_reference is None:
        return None
    simple_name = decorator_reference.split(".")[-1]
    if (
        simple_name in visitor.known_decorators
        or simple_name in visitor.imported_types
        or decorator_reference in visitor.imported_types
    ):
        return decorator_to_function(visitor, node)
    return None

def merge_keyword_argument(result, kw, visitor=None):
    if kw.arg is not None:
        value = convert_ast_value(kw.value, visitor)
        result[kw.arg] = value
        return

    for key, value in extract_keyword_expansion(kw.value, visitor).items():
        result[key] = value

def extract_keyword_expansion(node, visitor=None):
    if isinstance(node, ast.Dict):
        result = {}
        for key_node, value_node in zip(node.keys, node.values):
            if key_node is None:
                result.update(extract_keyword_expansion(value_node, visitor))
                continue
            key = convert_ast_value(key_node, visitor)
            if isinstance(key, str):
                result[key] = convert_ast_value(value_node, visitor)
        return result

    try:
        value = ast.literal_eval(node)
    except Exception:
        return {}

    if isinstance(value, dict):
        return {
            key: val
            for key, val in value.items()
            if isinstance(key, str)
        }
    return {}

def _resolve_java_constant(visitor, name_parts):
    """
    Try to resolve a qualified name as a Java constant (e.g., ['StringUtils', 'TRUE'] -> "true")
    Returns the constant value if found, None otherwise.
    """
    if visitor is None or len(name_parts) < 2:
        return None

    # The last part is the field name, everything before is the class name
    field_name = name_parts[-1]
    class_name_parts = name_parts[:-1]
    class_name = '.'.join(class_name_parts)

    # First check if the class name is in java_type_assignments (imported types)
    resolved_class_name = visitor.java_type_assignments.get(class_name, class_name)

    # Try to get the class element from the visitor context
    try:
        if hasattr(visitor, 'visitor_context') and visitor.visitor_context is not None:
            class_element = visitor.visitor_context.getClassElement(resolved_class_name).orElse(None)
            if class_element is not None:
                # Try to find the field using getFields() method
                fields = class_element.getFields()
                for field in fields:
                    if field.getName() == field_name:
                        if hasattr(field, 'getConstantValue'):
                            constant_value = field.getConstantValue()
                            # Check if it's an Optional or the value directly
                            if hasattr(constant_value, 'isPresent') and constant_value.isPresent():
                                return constant_value.get()
                            elif constant_value is not None:
                                # Direct value
                                return constant_value
                        break
    except Exception:
        # If constant resolution fails, continue with fallback
        pass

    return None

def extract_arg_defaults(func_node):
    """
    Given an ast.FunctionDef node, return an ordered dictionary
    mapping argument names to their default values (or None).
    """
    arg_names = [a.arg for a in func_node.args.args]
    defaults = func_node.args.defaults

    # Only the last len(defaults) arguments have defaults
    num_no_defaults = len(arg_names) - len(defaults)
    default_values = [None]*num_no_defaults + defaults

    # Evaluate AST nodes to their actual values if needed
    # (here just represent as ast.dump for illustration)
    arg_dict = {}
    for arg, default in zip(arg_names, default_values):
        if default is None:
            arg_dict[arg] = None
        else:
            try:
                # Try to evaluate the value if it's a constant
                val = ast.literal_eval(default)
            except Exception:
                # Handle Name nodes (class references) specially
                if isinstance(default, ast.Name):
                    val = default.id
                else:
                    val = ast.dump(default)
            arg_dict[arg] = val

    return arg_dict

def extract_arg_decorators(visitor, func_node):
    """
    Given an ast.FunctionDef node for a Python decorator, return decorators applied to its members
    through typing.Annotated metadata.
    """
    member_decorators = {}
    for arg in func_node.args.args:
        annotation = getattr(arg, 'annotation', None)
        if (
            isinstance(annotation, ast.Subscript)
            and isinstance(annotation.value, ast.Name)
            and annotation.value.id == 'Annotated'
        ):
            _, decorators = visitor._parse_annotated_type(annotation)
            if decorators:
                member_decorators[arg.arg] = decorators
    return member_decorators

def extract_arg_types(visitor, func_node):
    """
    Given an ast.FunctionDef node for a Python decorator, return the parsed member
    types keyed by argument name.
    """
    member_types = {}
    for arg in func_node.args.args:
        annotation = getattr(arg, 'annotation', None)
        if annotation is None:
            continue
        if (
            isinstance(annotation, ast.Subscript)
            and isinstance(annotation.value, ast.Name)
            and annotation.value.id == 'Annotated'
        ):
            parsed_type, _ = visitor._parse_annotated_type(annotation)
            if parsed_type is not None:
                member_types[arg.arg] = parsed_type
        else:
            parsed_type = visitor._parse_type(annotation)
            if parsed_type is not None:
                member_types[arg.arg] = parsed_type
    return member_types

def extract_call_arguments_with_defaults(funcdef, call, visitor=None):
    """
    Given an ast.FunctionDef (can be None) and an ast.Call node,
    return a dict mapping argument names (from funcdef) or integer indices (if funcdef is None)
    to values from the call (and funcdef defaults if available).
    If visitor is provided, use it for type name resolution.
    """
    result = {}
    if funcdef is None:
        # For Java annotations used as decorators (funcdef is None),
        # we need to map positional args to parameter names.
        # Java annotations conventionally use "value" for the first positional argument,
        # including mixed calls like @Get("/path", produces="text/plain").
        for i, arg in enumerate(call.args):
            value = convert_ast_value(arg, visitor)
            result["value" if i == 0 else f"arg{i}"] = value
        # Handle keyword arguments
        for kw in call.keywords:
            merge_keyword_argument(result, kw, visitor)
    else:
        # Get parameter names from function definition
        try:
            param_names = [entry.getKey() for entry in funcdef.members().entrySet()]
        except:
            # If funcdef.members() fails, treat as no parameters
            param_names = []

        # Special handling for Java annotations that use *args, **kwargs
        # If no named parameters but we have positional args, assume single arg uses "value"
        if len(param_names) == 0:
            for i, arg in enumerate(call.args):
                value = convert_ast_value(arg, visitor)
                result["value" if i == 0 else f"arg{i}"] = value
            # Handle keyword arguments
            for kw in call.keywords:
                merge_keyword_argument(result, kw, visitor)
        else:
            # Normal case with named parameters
            # Map positional arguments by their position in the parameter list
            for i, arg in enumerate(call.args):
                if i < len(param_names):
                    param_name = param_names[i]
                    value = convert_ast_value(arg, visitor)
                    result[param_name] = value

            # Map keyword arguments by their parameter names
            for kw in call.keywords:
                merge_keyword_argument(result, kw, visitor)

    return result

def is_micronaut_annotation_decorator(decorator_node):
    """
    Returns True if the decorator node is a @micronaut_annotation decorator.
    This is an internal decorator that should not be treated as a stereotype.
    """
    if isinstance(decorator_node, ast.Call):
        # Check decorator name
        is_target = (
                (isinstance(decorator_node.func, ast.Name) and decorator_node.func.id == 'micronaut_annotation')
                or (isinstance(decorator_node.func, ast.Attribute) and decorator_node.func.attr == 'micronaut_annotation')
        )
        return is_target
    return False

def is_micronaut_decorator(funcdef, visitor=None):
    """
    Returns True if the ast.FunctionDef is a top-level function (not inside a class)
    and has been transformed by the micronaut_transformer to create micronaut annotations,
    or is decorated with Micronaut/jakarta.inject annotations.
    This checks for functions that have @micronaut_annotation decorators or the _micronaut_annotations pattern,
    or decorators imported from micronaut.* or jakarta.inject.* packages.
    """
    if not isinstance(funcdef, ast.FunctionDef):
        return False

    # Check if this function has a @micronaut_annotation decorator
    for dec in funcdef.decorator_list:
        if isinstance(dec, ast.Call):
            # Check decorator name
            is_target = (
                    (isinstance(dec.func, ast.Name) and dec.func.id == 'micronaut_annotation')
                    or (isinstance(dec.func, ast.Attribute) and dec.func.attr == 'micronaut_annotation')
            )
            if is_target:
                return True

    # Check if this function has decorators imported from micronaut.* or jakarta.inject.* packages
    if visitor is not None:
        for dec in funcdef.decorator_list:
            decorator_name = extract_decorator_name(dec)
            if decorator_name:
                existing_decorator = visitor.known_decorators.get(decorator_name)
                if existing_decorator:
                    annotation_name = existing_decorator.annotationName()
                    if is_annotation_stereotype_for_python_decorator(annotation_name):
                        return True

    return False

def is_annotation_stereotype_for_python_decorator(annotation_name):
    if annotation_name.startswith('io.micronaut.aop') or annotation_name.startswith('jakarta.inject.'):
        return True
    return annotation_name in {
        'io.micronaut.context.annotation.AnnotationExpressionContext',
        'io.micronaut.context.annotation.Bean',
        'io.micronaut.context.annotation.Prototype',
        'io.micronaut.context.annotation.Requires',
        'io.micronaut.core.bind.annotation.Bindable',
        'io.micronaut.http.annotation.FilterMatcher',
    }

def get_micronaut_annotation_value(name, funcdef):
    """
    If the ast.FunctionDef has a `@micronaut_annotation` decorator,
    returns the value used for `name` (either as keyword or first positional arg).
    Returns None if not found.
    """
    for dec in funcdef.decorator_list:
        # Handle @micronaut_annotation(...), i.e., ast.Call node
        if isinstance(dec, ast.Call):
            # Check decorator name
            is_target = (
                    (isinstance(dec.func, ast.Name) and dec.func.id == 'micronaut_annotation')
                    or (isinstance(dec.func, ast.Attribute) and dec.func.attr == 'micronaut_annotation')
            )
            if is_target:
                # 1. Prefer kwarg 'name'
                for kw in dec.keywords:
                    if kw.arg == name:
                        try:
                            return ast.literal_eval(kw.value)
                        except Exception:
                            return None

                # 2. Or first positional argument, if present
                if dec.args and name == 'name':
                    try:
                        return ast.literal_eval(dec.args[0])
                    except Exception:
                        return None
        # Handle @micronaut_annotation (no call): uncommon, but possible
        elif isinstance(dec, ast.Name) and dec.id == 'micronaut_annotation':
            return None  # no args: no name specified
        elif isinstance(dec, ast.Attribute) and dec.attr == 'micronaut_annotation':
            return None
    return None

def qualify_local_annotation_name(visitor, annotation_name):
    if annotation_name is None:
        return None
    if isinstance(annotation_name, str) and "." not in annotation_name and visitor.package_name:
        return f"{visitor.package_name}.{annotation_name}"
    return annotation_name





def extract_parameter_documentation(func_node):
    """
    Extract parameter documentation from a function's docstring.
    Returns a dictionary mapping parameter names to their documentation.
    """
    param_docs = {}

    # Get the function docstring
    docstring = None
    if hasattr(func_node, 'body') and func_node.body:
        first_stmt = func_node.body[0]
        if isinstance(first_stmt, ast.Expr) and isinstance(first_stmt.value, ast.Constant):
            # Python 3.8+ uses ast.Constant for string literals
            if isinstance(first_stmt.value.value, str):
                docstring = first_stmt.value.value
        elif isinstance(first_stmt, ast.Expr) and isinstance(first_stmt.value, ast.Str):
            # Python < 3.8 uses ast.Str for string literals
            docstring = first_stmt.value.s

    if not docstring:
        return param_docs

    # Parse the docstring to extract parameter documentation
    lines = docstring.split('\n')
    current_section = None
    param_name = None
    param_description = []

    for line in lines:
        stripped = line.strip()
        lower_stripped = stripped.lower()

        # Check for parameter sections
        if lower_stripped in ['args:', 'arguments:', 'parameters:', 'param:']:
            current_section = 'params'
            continue
        elif lower_stripped in ['returns:', 'return:', 'raises:', 'exceptions:', 'note:', 'notes:', 'example:', 'examples:', 'see also:']:
            current_section = None
            if param_name:
                # Save previous parameter
                param_docs[param_name] = ' '.join(param_description).strip()
                param_name = None
                param_description = []
            continue

        if current_section == 'params':
            # Check if this line starts a parameter definition
            # Common formats: "param_name : description" or "param_name (type): description"
            if ':' in stripped:
                parts = stripped.split(':', 1)
                potential_param = parts[0].strip()

                # Extract parameter name (handle type annotations)
                param_name_candidate = potential_param.split('(')[0].strip() if '(' in potential_param else potential_param

                # Check if it's a parameter name (should be a valid identifier)
                if param_name_candidate and param_name_candidate.replace('_', '').isalnum() and not param_name_candidate[0].isdigit():
                    # Save previous parameter if any
                    if param_name:
                        param_docs[param_name] = ' '.join(param_description).strip()

                    param_name = param_name_candidate
                    param_description = [parts[1].strip()] if len(parts) > 1 else []
                elif param_name:
                    # Continuation of current parameter description
                    param_description.append(stripped)
            elif param_name and stripped:
                # Continuation of current parameter description
                param_description.append(stripped)

    # Save the last parameter
    if param_name:
        param_docs[param_name] = ' '.join(param_description).strip()

    return param_docs
