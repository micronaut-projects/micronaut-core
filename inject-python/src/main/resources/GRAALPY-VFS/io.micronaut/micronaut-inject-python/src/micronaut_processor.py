import ast
import java
from collections import OrderedDict

JavaClassDef = java.type("io.micronaut.python.processing.visitor.ClassDef")
JavaFuncDef = java.type("io.micronaut.python.processing.visitor.FunctionDef")
JavaAttributeDef = java.type("io.micronaut.python.processing.visitor.AttributeDef")
DecoratorDef = java.type("io.micronaut.python.processing.visitor.DecoratorDef")
ArgumentsDef = java.type("io.micronaut.python.processing.visitor.ArgumentsDef")
ArgumentDef = java.type("io.micronaut.python.processing.visitor.ArgumentDef")

def is_abstract_method(funcdef):
    """
    Returns True if the ast.FunctionDef has an @abstractmethod decorator.
    """
    for dec in funcdef.decorator_list:
        if isinstance(dec, ast.Name) and dec.id == "abstractmethod":
            return True
        elif isinstance(dec, ast.Attribute) and dec.attr == "abstractmethod":
            return True
    return False

class PrintNodeVisitor(ast.NodeVisitor):

    def __init__(self, callback, package_name=""):
        self.callback = callback
        self.package_name = package_name
        # maintain insertion order
        self.known_decorators = OrderedDict()
        self.known_decorator_functions = OrderedDict()
        self.current_class = None
        self.current_class_attributes = []

    def visit(self, node: ast.AST) -> ast.AST:
        match node:
            case ast.ClassDef():
                decorators = [
                    decorator_to_function(self, d)
                    for d in node.decorator_list
                    if decorator_to_function(self, d) is not None
                ]
                # Extract class docstring
                class_doc = self._extract_docstring(node)
                self.current_class = JavaClassDef(node.name, self.package_name, [], decorators, [], [], [], None, False, [], class_doc)
                self.current_class_attributes = []

                # Check if this is an enum class
                is_enum = self._is_enum_class(node)
                enum_values = []
                if is_enum:
                    enum_values = self._extract_enum_values(node)

                try:
                    result = super().visit(node)
                finally:
                    # Add collected attributes to the class before applying callback
                    for attr in self.current_class_attributes:
                        self.current_class = self.current_class.withAttribute(attr)

                    # Set enum information if applicable
                    if is_enum:
                        self.current_class = self.current_class.withEnum(True, enum_values)

                    self.callback.apply(self.current_class)
                    self.current_class = None
                    self.current_class_attributes = []
                return result
            case ast.FunctionDef():
                # Skip @property decorated functions - they should be represented as PropertyElement
                if self.current_class is not None and is_property_decorator(node):
                    return node

                if self.current_class is None and is_micronaut_decorator(node):
                    arg_dict = extract_arg_defaults(node)
                    stereotypes = [
                        decorator_to_function(self, d)
                        for d in node.decorator_list
                        if decorator_to_function(self, d) is not None
                    ]
                    annotation_name = get_micronaut_annotation_name_value(node)
                    decorator_def = DecoratorDef(node.name, annotation_name, arg_dict, stereotypes)
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
                    arguments = parse_function_arguments(node)
                    return_type_annotation = parse_function_return_type(node)
                    # Extract function docstring
                    func_doc = self._extract_docstring(node)

                    # Check if function is abstract
                    is_abstract = is_abstract_method(node)

                    func_def = JavaFuncDef(node.name, arguments, decorators, return_type_annotation, "", [], func_doc, is_abstract)
                    if self.current_class is not None:
                        if node.name == "__init__":
                            # Set as constructor
                            self.current_class = self.current_class.withConstructor(func_def)
                        else:
                            self.current_class = self.current_class.withFunction(func_def)
                    return super().visit(node)
            case ast.Assign():
                # Handle class attribute assignments
                if self.current_class is not None:
                    self._handle_assign(node)
                return node
            case ast.AnnAssign():
                # Handle annotated assignments (type hints)
                if self.current_class is not None:
                    self._handle_ann_assign(node)
                return node
            case ast.Module():
                self.current_class = None
                self.callback.apply(node)
                return super().visit(node)
            case _:
                return node

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
                # For simplicity, assume class-level assignments are static
                is_static = True  # This is a heuristic; could be improved

                attr_def = JavaAttributeDef(attr_name, None, value, [], None, is_static)
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

                # Check for @dataclass.field() or other decorators
                decorators = []
                # Note: AnnAssign doesn't have decorators directly, but we could check context

                # Determine if static (heuristic)
                is_static = True

                attr_def = JavaAttributeDef(attr_name, annotation, value, decorators, None, is_static)
                self.current_class_attributes.append(attr_def)

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
                return None
        # when a decorator takes argument values it is represnted by ast.Call
        # here we parse out the constants to the call and set them as the named
        # values to the decorator
        case ast.Call():
            decorator_name = node.func.id
            decorator_declaration = visitor.known_decorators.get(decorator_name)
            if decorator_declaration is not None:
                members = extract_call_arguments_with_defaults(decorator_declaration, node)
                return DecoratorDef(decorator_name, decorator_declaration.annotationName(), members, decorator_declaration.stereotypes())
            else:
                return None
        case _:
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
                val = ast.dump(default)
            arg_dict[arg] = val

    return arg_dict

def extract_call_arguments_with_defaults(funcdef, call):
    """
    Given an ast.FunctionDef (can be None) and an ast.Call node,
    return a dict mapping argument names (from funcdef) or integer indices (if funcdef is None)
    to values from the call (and funcdef defaults if available).
    """
    result = {}
    if funcdef is None:
        for i, arg in enumerate(call.args):
            try:
                value = ast.literal_eval(arg)
            except Exception:
                value = ast.dump(arg)
            result[i] = value
    else:
        for i, (entry) in enumerate(funcdef.members().entrySet()):
            value = entry.getValue()
            key = entry.getKey()
            arg = call.args[i]
            try:
                value = ast.literal_eval(arg)
            except Exception:
                value = entry.getValue()
            if value is not None:
                result[key] = value

    for kw in call.keywords:
        if kw.arg is not None:
            try:
                value = ast.literal_eval(kw.value)
            except Exception:
                value = ast.dump(kw.value)
            result[kw.arg] = value

    return result

def is_micronaut_decorator(funcdef):
    """
    Returns True if the ast.FunctionDef is a top-level function (not inside a class)
    and has a decorator named 'micronaut_annotation' in its decorators.
    """
    if not isinstance(funcdef, ast.FunctionDef):
        return False

    # Check for 'micronaut_annotation' in decorators.
    for dec in funcdef.decorator_list:
        # Handles both @micronaut_annotation and @something.micronaut_annotation
        if isinstance(dec, ast.Name) and dec.id == "micronaut_annotation":
            return True
        elif isinstance(dec, ast.Attribute) and dec.attr == "micronaut_annotation":
            return True
        elif (
                isinstance(dec, ast.Call)
                and (
                        (isinstance(dec.func, ast.Name) and dec.func.id == "micronaut_annotation")
                        or (isinstance(dec.func, ast.Attribute) and dec.func.attr == "micronaut_annotation")
                )
        ):
            # Handles @micronaut_annotation(...) or @something.micronaut_annotation(...)
            return True

    return False

def get_micronaut_annotation_name_value(funcdef):
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
                    if kw.arg == 'name':
                        try:
                            return ast.literal_eval(kw.value)
                        except Exception:
                            return None

                # 2. Or first positional argument, if present
                if dec.args:
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

def parse_function_arguments(func_node):
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
        type_annotation = ""
        if hasattr(arg, 'annotation') and arg.annotation is not None:
            try:
                type_annotation = ast.unparse(arg.annotation)
            except AttributeError:
                type_annotation = ast.dump(arg.annotation)

        # Get default value
        default_value = default_values[i]
        if default_value is not None:
            try:
                # Try to evaluate the value
                default_value = ast.literal_eval(default_value)
            except Exception:
                # For non-literal defaults, keep as is or dump
                default_value = ast.dump(default_value)

        # Get parameter documentation
        param_doc = param_docs.get(arg_name, None)

        arguments.append(ArgumentDef.of(arg_name, type_annotation, default_value, param_doc))

    return ArgumentsDef.of(arguments)

def parse_function_return_type(func_node):
    """
    Parse the return type annotation of an ast.FunctionDef node and return a string.
    """
    if hasattr(func_node, 'returns') and func_node.returns is not None:
        try:
            return ast.unparse(func_node.returns)
        except AttributeError:
            return ast.dump(func_node.returns)

    return ""

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
