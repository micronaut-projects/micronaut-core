import ast
import java
from collections import OrderedDict

JavaClassDef = java.type("io.micronaut.python.processing.visitor.ClassDef")
JavaFuncDef = java.type("io.micronaut.python.processing.visitor.FunctionDef")
JavaAttributeDef = java.type("io.micronaut.python.processing.visitor.AttributeDef")
DecoratorDef = java.type("io.micronaut.python.processing.visitor.DecoratorDef")

class PrintNodeVisitor(ast.NodeVisitor):

    def __init__(self, callback):
        self.callback = callback
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
                self.current_class = JavaClassDef(node.name, decorators)
                self.current_class_attributes = []
                try:
                    result = super().visit(node)
                finally:
                    # Add collected attributes to the class before applying callback
                    for attr in self.current_class_attributes:
                        self.current_class = self.current_class.withAttribute(attr)
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
                    # Skip __init__ method (constructor)
                    if node.name == "__init__":
                        return super().visit(node)

                    decorators = [
                        decorator_to_function(self, d)
                        for d in node.decorator_list
                        if decorator_to_function(self, d) is not None
                    ]
                    func_def = JavaFuncDef(node.name, decorators)
                    if self.current_class is not None:
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
                    value = ast.literal_eval(node.value)
                except (ValueError, TypeError):
                    value = None  # Non-literal values

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
                    value = ast.literal_eval(node.value) if node.value else None
                except (ValueError, TypeError):
                    value = None

                # Check for @dataclass.field() or other decorators
                decorators = []
                # Note: AnnAssign doesn't have decorators directly, but we could check context

                # Determine if static (heuristic)
                is_static = True

                attr_def = JavaAttributeDef(attr_name, annotation, value, decorators, None, is_static)
                self.current_class_attributes.append(attr_def)

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
