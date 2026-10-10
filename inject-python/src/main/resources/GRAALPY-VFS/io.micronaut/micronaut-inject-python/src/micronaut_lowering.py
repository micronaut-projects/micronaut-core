"""
Lowering of a candidate function body to the typed IR the Java side generates code from.

The lowering walks the body with the types and targets the type checker inferred in a silent run
(:class:`micronaut_typecheck.JavaReceiverRules`), and either produces a ``CompiledBody`` or
refuses with every reason it found. It never approximates: a construct without a lowering that
preserves Python semantics for the inferred operand types is a reason, with a stable rule and the
span of the construct.

Python ``int`` values are ``long`` with exact arithmetic (overflow raises where Python would
promote), ``float`` values ``double``, ``str`` values ``String``, ``None`` is ``null``.
"""
import ast

import java

from micronaut_typecheck import BUILTIN, CALLABLE, JAVA, JAVA_REF, MODULE, PY, PY_REF, STANDARD_TYPES, Typed

Ir = java.type("io.micronaut.python.processing.staticcompile.Ir")
CompiledBody = java.type("io.micronaut.python.processing.staticcompile.Ir$CompiledBody")
Stats = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Stats")


def _ir(name):
    return java.type(f"io.micronaut.python.processing.staticcompile.Ir${name}")


Body, Local, Assign, PutSelf, If, Return, Eval = (_ir(n) for n in ("Body", "Local", "Assign", "PutSelf", "If", "Return", "Eval"))
Const, Param, LocalRef, SelfProperty = (_ir(n) for n in ("Const", "Param", "LocalRef", "SelfProperty"))
InvokeJava, NewJava, StaticField, Field = (_ir(n) for n in ("InvokeJava", "NewJava", "StaticField", "Field"))
Binary, Unary, Compare, And, Or, Conditional, Truthy, StrJoin, Helper, Cast = (
    _ir(n) for n in ("Binary", "Unary", "Compare", "And", "Or", "Conditional", "Truthy", "StrJoin", "Helper", "Cast"))

LONG, DOUBLE, BOOLEAN, STRING, VOID, NONE = "long", "double", "boolean", "java.lang.String", "void", "none"
NUMBERS = (LONG, DOUBLE)
# the Java types a value of a Python builtin kind has inside a compiled body
BUILTIN_TYPES = {"int": LONG, "float": DOUBLE, "bool": BOOLEAN, "str": STRING, "none": NONE}
# the Java types the stub declares for a hint of a builtin kind
STUB_TYPES = {"int": "int", "float": DOUBLE, "bool": BOOLEAN, "str": STRING}
# the Java types the stub declares, and a compiled body uses, for a number or a boolean that may be None
BOXED_TYPES = {"int": "java.lang.Integer", "float": "java.lang.Double", "bool": "java.lang.Boolean"}
OBJECT = "java.lang.Object"
# the standard Java values whose toString() spells them as Python's str() does: the others (an Instant, a
# Duration, a BigDecimal) are a Python datetime, timedelta or Decimal at run time, spelled differently
STANDARD_STR_TYPES = {"java.util.UUID", "java.time.LocalDate", "java.time.LocalTime"}
# Java numeric types and the type a compiled body uses them at
JAVA_NUMBERS = {"int": LONG, "long": LONG, "short": LONG, "byte": LONG, "float": DOUBLE, "double": DOUBLE,
                "java.lang.Integer": LONG, "java.lang.Long": LONG, "java.lang.Short": LONG, "java.lang.Byte": LONG,
                "java.lang.Float": DOUBLE, "java.lang.Double": DOUBLE}
JAVA_BOOLEANS = {"boolean", "java.lang.Boolean"}
JAVA_STRINGS = {"java.lang.String"}  # a CharSequence is a Java value, not a Python str
BINARY_OPS = {ast.Add: "+", ast.Sub: "-", ast.Mult: "*", ast.Div: "/", ast.FloorDiv: "//", ast.Mod: "%", ast.Pow: "**"}
COMPARE_OPS = {ast.Lt: "<", ast.LtE: "<=", ast.Gt: ">", ast.GtE: ">=", ast.Eq: "==", ast.NotEq: "!="}
DEFAULTS = {LONG: 0, DOUBLE: 0.0, BOOLEAN: False}


# how well a parameter type fits an argument type: lower is more specific, as Java ranks overloads
_RANKS = {
    LONG: {"long": 0, "int": 1, "short": 2, "byte": 3, "java.lang.Long": 4, "java.lang.Integer": 5, "double": 6, "float": 7,
           "java.lang.Double": 8, "java.lang.Float": 9, "java.lang.Number": 10, "java.lang.Object": 11},
    DOUBLE: {"double": 0, "float": 1, "java.lang.Double": 2, "java.lang.Float": 3, "java.lang.Number": 4, "java.lang.Object": 5},
    STRING: {"java.lang.String": 0, "java.lang.CharSequence": 1, "java.lang.Object": 2},
    BOOLEAN: {"boolean": 0, "java.lang.Boolean": 1, "java.lang.Object": 2},
}


def _rank(argument_type, parameter_type):
    table = _RANKS.get(argument_type)
    if table is not None:
        return table.get(parameter_type, 20)
    if argument_type == parameter_type:
        return 0
    if parameter_type == "java.lang.Object":
        return 2
    return 1


def _erased(type_name):
    """A Java type name without the type arguments the lowering carries in it."""
    index = type_name.find("<")
    return type_name if index < 0 else type_name[:index]


def _pure_operand(node):
    """Whether evaluating the expression twice is what Python does once: a name, a literal or an attribute read of one."""
    if isinstance(node, (ast.Name, ast.Constant)):
        return True
    return isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name)


def _always_returns(statements):
    """Whether every path through the statements ends in a return (or a raise, which Java accepts as a terminal too)."""
    for statement in statements:
        if isinstance(statement, (ast.Return, ast.Raise)):
            return True
        if isinstance(statement, ast.If) and statement.orelse and _always_returns(statement.body) and _always_returns(statement.orelse):
            return True
    return False


def _assigned_on_every_path(statements, name):
    """Whether every path through the statements assigns the local before the statements end."""
    for statement in statements:
        if isinstance(statement, (ast.Assign, ast.AnnAssign, ast.AugAssign)):
            targets = statement.targets if isinstance(statement, ast.Assign) else [statement.target]
            if any(isinstance(target, ast.Name) and target.id == name for target in targets):
                return True
        if isinstance(statement, ast.If) and statement.orelse and _assigned_on_every_path(statement.body, name) and _assigned_on_every_path(statement.orelse, name):
            return True
        if isinstance(statement, (ast.Return, ast.Raise)):
            return True  # no path continues past here without the local being needed
    return False


class Refused(Exception):
    """A construct without a lowering; the reason is recorded before it is raised."""


class Lowering:
    """Lowers one function body; see the module documentation."""

    def __init__(self, checker, module, class_def, function_def, node, rules, class_model=None):
        self.checker = checker
        self.module = module
        self.class_def = class_def
        self.function_def = function_def
        self.node = node
        self.rules = rules
        self.bindings = rules.bindings
        self.class_model = class_model
        self.reasons = []
        self.locals = {}          # name -> Java type
        self.hoisted = []         # Local statements declared at the top of the body
        self.depth = 0            # nesting of the statement being lowered
        self.branches = []        # the if statements enclosing the statement being lowered
        self.parameters = {}      # name -> (type used at, stub type)
        self.java_calls = 0
        self.bridge_calls = 0
        self.helper_calls = 0
        self.checked = False      # a call declares a checked exception

    # ---------------------------------------------------------------- entry

    def lower(self):
        """The CompiledBody, or None when the body was refused (the reasons say why)."""
        try:
            parameter_names, parameter_types = self._parameters()
            return_type = self._return_type()
            if return_type != VOID and not _always_returns(self.node.body):
                self._refuse("unknown-type", "the function does not return a value on every path", self.node)
            statements = self._block(self.node.body)
            body = Body(self.hoisted + statements)
        except Refused:
            return None
        if self.reasons:
            return None
        stats = Stats(len(self.node.body), self.java_calls, self.bridge_calls, self.helper_calls)
        return CompiledBody(self.class_def.qualifiedName(), self.function_def.name(), parameter_names, parameter_types,
                            return_type, body, self.function_def.span(), stats, self.checked)

    def _refuse(self, rule, message, node):
        self.reasons.append((rule, message, self.module.span_of(node) if node is not None else self.function_def.span()))
        raise Refused()

    # ---------------------------------------------------------------- signature

    def _parameters(self):
        names, types = [], []
        for argument in self.function_def.arguments().arguments():
            name = argument.name()
            if name in ("self", "cls") and not self.function_def.isStatic():
                continue
            # from the hint itself: the checker's pass forgets a parameter the body reassigns
            hint = argument.typeAnnotation()
            typed = self.bindings.of_hint(hint) if hint is not None else None
            stub_type = self._stub_type(typed, hint, self.node)
            self.parameters[name] = (self._value_type(typed, self.node), stub_type)
            names.append(name)
            types.append(stub_type)
        return names, types

    def _return_type(self):
        return_def = self.function_def.returnType()
        hint = return_def.typeAnnotation() if return_def is not None else None
        if hint is None:
            # the stub declares an Object return for an unhinted function that returns a value
            return OBJECT if self.function_def.hasReturnValue() else VOID
        if hint.name() == "None":
            return VOID
        typed = self.bindings.of_hint(hint)
        return self._stub_type(typed, hint, self.node)

    def _stub_type(self, typed, hint, node):
        """The Java type the stub declares for a hinted parameter or return."""
        if hint is not None and hint.name() in ("typing.Optional", "Optional"):
            self._refuse("unsupported-expression", "an Optional hint has no static lowering yet", node)
        if typed is None:
            self._refuse("unknown-type", f"the hint [{hint.name() if hint is not None else '?'}] resolves to no static type", node)
        if typed.kind == BUILTIN:
            if typed.name not in STUB_TYPES:
                self._refuse("unsupported-expression", f"a Python {typed.name} has no static lowering", node)
            if typed.nullable and typed.name in BOXED_TYPES:
                # the stub boxes a number or a boolean that may be None, as its hint says
                return BOXED_TYPES[typed.name]
            return STUB_TYPES[typed.name]
        if typed.kind == JAVA:
            return typed.name
        if typed.kind == PY:
            return typed.name.qualifiedName()
        self._refuse("unknown-type", f"the hint [{hint.name()}] denotes no value type", node)

    # ---------------------------------------------------------------- types

    def _value_type(self, typed, node):
        """The Java type a compiled body uses for a value of the inferred type."""
        if typed is None:
            self._refuse("unknown-type", "the expression has no static type", node)
        if typed.kind == BUILTIN:
            if typed.name not in BUILTIN_TYPES:
                self._refuse("unsupported-expression", f"a Python {typed.name} has no static lowering", node)
            if typed.nullable and typed.name in BOXED_TYPES:
                # a number or a boolean that may be None stays boxed: unboxing None is not a Python failure
                return BOXED_TYPES[typed.name]
            return BUILTIN_TYPES[typed.name]
        if typed.kind == JAVA:
            return JAVA_NUMBERS.get(typed.name) or (BOOLEAN if typed.name in JAVA_BOOLEANS else STRING if typed.name in JAVA_STRINGS else typed.name)
        if typed.kind == PY:
            return typed.name.qualifiedName()
        if typed.kind in (JAVA_REF, PY_REF):
            self._refuse("unknown-type", "a class is not a value here", node)
        if typed.kind == MODULE:
            self._refuse("unknown-type", "a module is not a value here", node)
        self._refuse("dynamic-call", "a callable used as a value has no static lowering", node)

    def _typed(self, node):
        return self.rules.node_types.get(id(node))

    @staticmethod
    def _is_number(type_name):
        return type_name in NUMBERS

    def _coerce(self, expression, to, node):
        """The expression at the Java type, widening or narrowing numbers and casting references."""
        source = expression.type()
        if source == to:
            return expression
        if source == NONE:
            if to in NUMBERS or to in (BOOLEAN, "int", "short", "byte", "float"):
                self._refuse("unknown-type", f"None is given where a [{to}] is expected", node)
            return Const(None, to)
        target = JAVA_NUMBERS.get(to, to)
        if source == LONG and target == DOUBLE:
            return Cast(expression, to)
        if source == LONG and to in ("int", "short", "byte", "long", "java.lang.Long", "java.lang.Integer", "java.lang.Short", "java.lang.Byte"):
            return Cast(expression, to)  # exact narrowing, boxed through the primitive by the generator
        if source == DOUBLE and to in ("double", "float", "java.lang.Double", "java.lang.Float"):
            return Cast(expression, to)
        if source == BOOLEAN and to in JAVA_BOOLEANS:
            return expression
        if source == STRING and to in ("java.lang.String", "java.lang.CharSequence", "java.lang.Object"):
            return expression
        if to in ("java.lang.Object", "java.lang.Number", "java.io.Serializable", "java.lang.Comparable"):
            if source == LONG:
                # a Python int reaches an Object parameter as an Integer when it fits one, else a Long
                self.helper_calls += 1
                return Helper("box", [expression], to)
            if source == DOUBLE:
                return Cast(expression, "java.lang.Double")
            if source == BOOLEAN:
                return Cast(expression, "java.lang.Boolean")
            return expression
        if source in JAVA_NUMBERS and target in NUMBERS:
            return self._coerce(Cast(expression, JAVA_NUMBERS[source]), to, node)
        if self.checker.facts.isAssignable(source, to):
            return expression
        self._refuse("unknown-type", f"a [{source}] is given where a [{to}] is expected", node)

    # ---------------------------------------------------------------- statements

    def _block(self, statements):
        self.depth += 1
        lowered = []
        for statement in statements:
            lowered.extend(self._statement(statement))
        self.depth -= 1
        return lowered

    def _statement(self, node):
        if isinstance(node, ast.Pass):
            return []
        if isinstance(node, ast.Assert):
            # assertions are enabled in the runtime, so a false condition raises as Python raises AssertionError
            test = self._truthy(node.test)
            message = self._expression(node.msg) if node.msg is not None else Const(None, NONE)
            self.helper_calls += 1
            return [Eval(Helper("assertion", [test, StrJoin([message]) if node.msg is not None else Const(None, STRING)], VOID))]
        if isinstance(node, ast.Expr):
            if isinstance(node.value, ast.Constant) and isinstance(node.value.value, str):
                return []  # a docstring
            return [Eval(self._expression(node.value))]
        if isinstance(node, ast.Return):
            return [self._return(node)]
        if isinstance(node, ast.Assign):
            return [self._assign(node.targets[0], self._expression(node.value), node)]
        if isinstance(node, ast.AnnAssign):
            if node.value is None:
                self._refuse("unsupported-statement", "a declaration without a value has no static lowering", node)
            return [self._assign(node.target, self._expression(node.value), node, hinted=node.annotation)]
        if isinstance(node, ast.AugAssign):
            return [self._augmented(node)]
        if isinstance(node, ast.If):
            test = self._truthy(node.test)
            self.branches.append(node)
            then = Body(self._block(node.body))
            or_else = Body(self._block(node.orelse)) if node.orelse else None
            self.branches.pop()
            return [If(test, then, or_else)]
        if isinstance(node, (ast.While, ast.For, ast.Try, ast.Raise, ast.Break, ast.Continue)):
            self._refuse("unsupported-statement", f"{type(node).__name__.lower()} statements have no static lowering yet", node)
        self._refuse("unsupported-statement", f"a {type(node).__name__} statement has no static lowering", node)

    def _return(self, node):
        return_type = self._return_type()
        if node.value is None:
            if return_type != VOID:
                self._refuse("unknown-type", "a bare return where the hint says a value is returned", node)
            return Return(None)
        if return_type == VOID:
            self._refuse("unknown-type", "a value is returned where the hint says none is", node)
        return Return(self._coerce(self._expression(node.value), return_type, node))

    def _assign(self, target, value, node, hinted=None):
        if isinstance(target, ast.Name):
            name = target.id
            if name in self.parameters:
                self._refuse("unsupported-statement", f"reassigning the parameter [{name}] has no static lowering", node)
            if hinted is not None:
                typed = self.rules._hint_type(hinted)
                declared = self._value_type(typed, node) if typed is not None else value.type()
            else:
                declared = self.locals.get(name, value.type())
            if declared == NONE:
                self._refuse("unknown-type", f"local [{name}] is None where it is declared", node)
            if name in self.locals and value.type() not in (declared, NONE) and not (declared == DOUBLE and value.type() == LONG):
                self._refuse("unknown-type", f"local [{name}] is a [{declared}] and then a [{value.type()}]", node)
            value = self._coerce(value, declared, node)
            if name not in self.locals:
                self.locals[name] = declared
                if self.depth > 1:
                    # a local first assigned inside a branch is declared at the top of the body, which
                    # keeps Python's behaviour only when every path assigns it before it is read
                    if not all(statement.orelse and _assigned_on_every_path(statement.body, name) and _assigned_on_every_path(statement.orelse, name) for statement in self.branches):
                        self._refuse("unsupported-statement", f"local [{name}] is assigned on some paths only; Python would raise UnboundLocalError on the others", node)
                    self.hoisted.append(Local(name, declared, Const(DEFAULTS.get(declared), declared) if declared in DEFAULTS else Const(None, declared)))
                    return Assign(name, value)
                return Local(name, declared, value)
            if self.locals[name] != declared:
                self._refuse("unknown-type", f"local [{name}] is a [{self.locals[name]}] and then a [{declared}]", node)
            return Assign(name, value)
        if isinstance(target, ast.Attribute) and isinstance(target.value, ast.Name) and target.value.id == "self":
            property_type = self._self_property_type(target.attr, target)
            self.bridge_calls += 1
            return PutSelf(target.attr, property_type, self._coerce(value, property_type, node))
        self._refuse("unsupported-statement", "assigning to anything but a local or a property of self has no static lowering", node)

    def _augmented(self, node):
        op = BINARY_OPS.get(type(node.op))
        if op is None:
            self._refuse("unsupported-expression", "the operator has no static lowering", node)
        if isinstance(node.target, ast.Name):
            if node.target.id not in self.locals:
                self._refuse("unknown-type", f"local [{node.target.id}] is updated before it is assigned", node)
            current = LocalRef(node.target.id, self.locals[node.target.id])
        elif isinstance(node.target, ast.Attribute) and isinstance(node.target.value, ast.Name) and node.target.value.id == "self":
            current = SelfProperty(node.target.attr, self._self_property_type(node.target.attr, node.target))
            self.bridge_calls += 1
        else:
            self._refuse("unsupported-statement", "updating anything but a local or a property of self has no static lowering", node)
        value = self._binary(op, current, self._expression(node.value), node)
        return self._assign(node.target, value, node)

    # ---------------------------------------------------------------- expressions

    def _expression(self, node):
        if isinstance(node, ast.Constant):
            return self._constant(node)
        if isinstance(node, ast.Name):
            return self._name(node)
        if isinstance(node, ast.Attribute):
            return self._attribute(node)
        if isinstance(node, ast.Call):
            return self._call(node)
        if isinstance(node, ast.BinOp):
            op = BINARY_OPS.get(type(node.op))
            if op is None:
                self._refuse("unsupported-expression", "the operator has no static lowering", node)
            return self._binary(op, self._expression(node.left), self._expression(node.right), node)
        if isinstance(node, ast.UnaryOp):
            return self._unary(node)
        if isinstance(node, ast.Compare):
            return self._compare(node)
        if isinstance(node, ast.BoolOp):
            return self._bool_op(node)
        if isinstance(node, ast.JoinedStr):
            return self._joined(node)
        if isinstance(node, ast.IfExp):
            return self._conditional(node)
        self._refuse("unsupported-expression", f"a {type(node).__name__} expression has no static lowering", node)

    def _constant(self, node):
        value = node.value
        if value is None:
            return Const(None, NONE)
        if isinstance(value, bool):
            return Const(value, BOOLEAN)
        if isinstance(value, int):
            if not -2 ** 63 <= value < 2 ** 63:
                self._refuse("unbounded-integer-op", f"the integer {value} does not fit a long", node)
            return Const(value, LONG)
        if isinstance(value, float):
            return Const(value, DOUBLE)
        if isinstance(value, str):
            return Const(value, STRING)
        self._refuse("unsupported-expression", f"a {type(value).__name__} literal has no static lowering", node)

    def _name(self, node):
        name = node.id
        if name in self.parameters:
            used, stub = self.parameters[name]
            return Param(name, used, stub)
        if name in self.locals:
            return LocalRef(name, self.locals[name])
        if name == "self":
            self._refuse("unsupported-expression", "self as a value has no static lowering", node)
        typed = self._typed(node)
        self._value_type(typed, node)  # a class, a module or a callable refuses with its reason
        self._refuse("unknown-type", f"[{name}] has no static lowering", node)

    def _attribute(self, node):
        if isinstance(node.value, ast.Name) and node.value.id == "self":
            self.bridge_calls += 1
            property_type = self._self_property_type(node.attr, node)
            read = SelfProperty(node.attr, property_type)
            # an int attribute (an instance attribute hinted through __init__) is a long in the body
            used = JAVA_NUMBERS.get(property_type, property_type)
            return Cast(read, used) if used != property_type else read
        target = self.rules.targets.get(id(node))
        if target is not None and target[0] == "field":
            _, owner, name, field_type, static = target
            self.java_calls += 1
            used = JAVA_NUMBERS.get(field_type, field_type)
            if static:
                read = StaticField(owner, name, field_type)
            else:
                read = Field(self._expression(node.value), name, field_type)
            return Cast(read, used) if used != field_type else read
        typed = self._typed(node)
        if typed is not None and typed.kind in (JAVA_REF, PY_REF, MODULE):
            self._refuse("unknown-type", "a class is not a value here", node)
        self._refuse("dynamic-call", f"the attribute [{node.attr}] resolves to no Java field or property of self", node)

    def _self_property_type(self, name, node):
        """The Java type of a property of self, from the hint of the attribute or property."""
        if self.class_model is None:
            self._refuse("unknown-self-attribute", f"self.{name} has no known type", node)
        found = self.class_model.find(name)
        if found is None:
            self._refuse("unknown-self-attribute", f"[{self.class_model.name}] has no attribute [{name}]", node)
        kind = found[0]
        typed = None
        if kind == "attribute":
            typed = self.bindings.of_hint(found[1].typeName())
        elif kind == "property":
            # a @property runs Python code: reading it is a call into the class, not a value read
            self._refuse("sibling-call", f"the property [{name}] runs its Python getter; not compiled yet", node)
        elif kind == "instance":
            typed = self.class_model.instance_attribute_type(name, self.bindings)
        if typed is None:
            self._refuse("unknown-self-attribute", f"self.{name} has no hinted type", node)
        return self._stub_type(typed, None, node)

    def _call(self, node):
        function = node.func
        if node.keywords:
            self._refuse("kwargs-to-java", "keyword arguments have no static lowering", node)
        if any(isinstance(argument, ast.Starred) for argument in node.args):
            self._refuse("dynamic-call", "spreading arguments has no static lowering", node)
        if isinstance(function, ast.Name) and function.id == "str" and len(node.args) == 1 and function.id not in self.locals:
            self.helper_calls += 1
            return StrJoin([self._spelled(self._expression(node.args[0]), node.args[0])])
        target = self.rules.targets.get(id(node))
        if target is None:
            typed = self._typed(function.value) if isinstance(function, ast.Attribute) else None
            if isinstance(function, ast.Attribute) and isinstance(function.value, ast.Name) and function.value.id == "self":
                self._refuse("sibling-call", f"calling the method [{function.attr}] of the class has no static lowering yet", node)
            if typed is not None and typed.kind in (PY, PY_REF):
                self._refuse("sibling-call", "calling a Python class of the compilation has no static lowering yet", node)
            if isinstance(function, ast.Name) and function.id in ("len", "int", "float", "bool", "abs", "min", "max", "isinstance", "range", "print"):
                self._refuse("python-builtin-not-lowered", f"the builtin [{function.id}] has no static lowering yet", node)
            self._refuse("dynamic-call", "the call resolves to no Java method or constructor", node)
        kind, owner, name, matching, static = target
        lowered_arguments = [self._expression(argument) for argument in node.args]
        signature = self._most_specific(list(matching), lowered_arguments, owner, name, node)
        parameter_types = list(signature.parameterTypes())
        if signature.varargs():
            self._refuse("unsupported-expression", "a call of a varargs method has no static lowering yet", node)
        if signature.throwsChecked():
            self.checked = True
        arguments = [self._coerce(argument, parameter_type, argument_node)
                     for argument, parameter_type, argument_node in zip(lowered_arguments, parameter_types, node.args)]
        self.java_calls += 1
        if kind == "constructor":
            return NewJava(owner, parameter_types, arguments)
        receiver = None if static else self._expression(function.value)
        if receiver is not None and receiver.type() in (LONG, DOUBLE, BOOLEAN):
            self._refuse("unsupported-expression", "a call on a Python value has no static lowering", node)
        return_type = signature.returnType()
        call = InvokeJava(receiver, owner, name, parameter_types, arguments, return_type)
        used = JAVA_NUMBERS.get(return_type, return_type)
        return Cast(call, used) if used != return_type else call

    def _most_specific(self, matching, arguments, owner, name, node):
        """
        The overload the compiled call binds to: the one whose parameters are the most specific
        for the argument types (a long before an int before a double, a String before a
        CharSequence before an Object), as Java would pick. Two overloads that rank the same are
        ambiguous, and the call is refused rather than guessed.
        """
        if len(matching) == 1:
            return matching[0]
        ranked = []
        for signature in matching:
            ranks = tuple(_rank(argument.type(), parameter) for argument, parameter in zip(arguments, signature.parameterTypes()))
            ranked.append((ranks, signature))
        ranked.sort(key=lambda entry: entry[0])
        if len(ranked) > 1 and ranked[0][0] == ranked[1][0]:
            self._refuse("ambiguous-overload", f"{len(matching)} overloads of [{owner.rsplit('.', 1)[-1]}.{name or '<init>'}] accept the arguments equally well", node)
        return ranked[0][1]

    def _binary(self, op, left, right, node):
        left_type, right_type = left.type(), right.type()
        if left_type == STRING and right_type == STRING and op == "+":
            return Binary("concat", left, right, STRING)
        if not (self._is_number(left_type) and self._is_number(right_type)):
            self._refuse("unknown-type", f"the operator {op} on a [{left_type}] and a [{right_type}] has no static lowering", node)
        if op == "/":
            result = DOUBLE
        elif op == "**":
            if left_type == LONG and right_type == LONG:
                if not (isinstance(node, ast.BinOp) and isinstance(node.right, ast.Constant) and isinstance(node.right.value, int) and node.right.value >= 0):
                    self._refuse("unbounded-integer-op", "an integer power with a non-literal exponent may not fit a long", node)
                result = LONG
            else:
                result = DOUBLE
        else:
            result = DOUBLE if DOUBLE in (left_type, right_type) else LONG
        if op in ("/", "//", "%", "**"):
            self.helper_calls += 1
        if result == DOUBLE and op != "/":
            left = self._coerce(left, DOUBLE, node) if left_type == LONG else left
            right = self._coerce(right, DOUBLE, node) if right_type == LONG else right
        elif op == "/" and (left_type, right_type) != (LONG, LONG):
            left = self._coerce(left, DOUBLE, node) if left_type == LONG else left
            right = self._coerce(right, DOUBLE, node) if right_type == LONG else right
        return Binary(op, left, right, result)

    def _unary(self, node):
        if isinstance(node.op, ast.Not):
            return Unary("not", self._truthy(node.operand), BOOLEAN)
        operand = self._expression(node.operand)
        if isinstance(node.op, ast.UAdd) and self._is_number(operand.type()):
            return operand
        if isinstance(node.op, ast.USub) and self._is_number(operand.type()):
            return Unary("-", operand, operand.type())
        self._refuse("unsupported-expression", "the unary operator has no static lowering for the operand", node)

    def _compare(self, node):
        if len(node.ops) != 1:
            self._refuse("unsupported-expression", "a chained comparison has no static lowering yet", node)
        op = node.ops[0]
        left = self._expression(node.left)
        right_node = node.comparators[0]
        if isinstance(op, (ast.Is, ast.IsNot)):
            if isinstance(right_node, ast.Constant) and right_node.value is None:
                if left.type() in (LONG, DOUBLE, BOOLEAN):
                    return Const(isinstance(op, ast.IsNot), BOOLEAN)
                return Compare("is None" if isinstance(op, ast.Is) else "is not None", left, None)
            self._refuse("unsupported-expression", "identity comparison with anything but None has no static lowering", node)
        if isinstance(op, (ast.In, ast.NotIn)):
            self._refuse("unsupported-expression", "membership tests have no static lowering yet", node)
        symbol = COMPARE_OPS.get(type(op))
        if symbol is None:
            self._refuse("unsupported-expression", "the comparison has no static lowering", node)
        right = self._expression(right_node)
        left_type, right_type = left.type(), right.type()
        if self._is_number(left_type) and self._is_number(right_type):
            if DOUBLE in (left_type, right_type):
                left = self._coerce(left, DOUBLE, node) if left_type == LONG else left
                right = self._coerce(right, DOUBLE, node) if right_type == LONG else right
            return Compare(symbol, left, right)
        if symbol in ("==", "!="):
            if left_type == BOOLEAN and right_type == BOOLEAN:
                return Compare(symbol, left, right)
            if left_type == STRING and right_type == STRING:
                return Compare("equals" if symbol == "==" else "!equals", left, right)
            if NONE in (left_type, right_type):
                other = right if left_type == NONE else left
                if other.type() in (LONG, DOUBLE, BOOLEAN):
                    return Const(symbol == "!=", BOOLEAN)
                return Compare("is None" if symbol == "==" else "is not None", other, None)
            description = self.checker.facts.describe(left_type) if left_type == right_type else None
            if description is not None and description.anEnum():
                return Compare("same" if symbol == "==" else "!same", left, right)
            self._refuse("equality-on-object", f"== between [{left_type}] and [{right_type}] has no static lowering", node)
        self._refuse("unsupported-expression", f"the comparison {symbol} of a [{left_type}] and a [{right_type}] has no static lowering", node)

    def _bool_op(self, node):
        values = [self._expression(value) for value in node.values]
        if all(value.type() == BOOLEAN for value in values):
            result = values[0]
            for value in values[1:]:
                result = And(result, value) if isinstance(node.op, ast.And) else Or(result, value)
            return result
        if len(values) == 2 and _pure_operand(node.values[0]):
            # `x or default` on values of one type, or of two reference types one of which takes the
            # other: the left operand is pure (a name, a literal or an attribute of a name), so it
            # may be tested and yielded
            first, second = values
            result_type = self._common_type(first.type(), second.type())
            if result_type is not None:
                test = self._truthy(node.values[0])
                first, second = self._coerce(first, result_type, node), self._coerce(second, result_type, node)
                return Conditional(test, first, second, result_type) if isinstance(node.op, ast.Or) else Conditional(test, second, first, result_type)
        self._refuse("unsupported-expression", "and/or on values that are not booleans of one type has no static lowering", node)

    def _common_type(self, first, second):
        """The type of both operands of an or: one type, or the reference type that takes the other, else None."""
        if first == second:
            return first
        if NONE in (first, second):
            other = second if first == NONE else first
            return None if other in (LONG, DOUBLE, BOOLEAN) else other
        if first in (LONG, DOUBLE, BOOLEAN, STRING) or second in (LONG, DOUBLE, BOOLEAN, STRING):
            return None
        facts = self.checker.facts
        if facts.isAssignable(_erased(second), _erased(first)):
            return first
        if facts.isAssignable(_erased(first), _erased(second)):
            return second
        return None

    def _joined(self, node):
        parts = []
        for value in node.values:
            if isinstance(value, ast.Constant):
                parts.append(Const(str(value.value), STRING))
            elif isinstance(value, ast.FormattedValue):
                if value.format_spec is not None or value.conversion not in (-1, 115):
                    self._refuse("unsupported-expression", "a format specification has no static lowering yet", value)
                parts.append(self._spelled(self._expression(value.value), value.value))
            else:
                self._refuse("unsupported-expression", "the f-string part has no static lowering", value)
        self.helper_calls += 1
        return StrJoin(parts)

    def _spelled(self, expression, node):
        """A value str() spells: refused for a standard Java value whose Python spelling differs from toString()."""
        kind = _erased(expression.type())
        if kind in STANDARD_TYPES and kind not in STANDARD_STR_TYPES:
            self._refuse("unsupported-expression", f"str() of a [{kind}] spells the value as Python does not", node)
        return expression

    def _conditional(self, node):
        test = self._truthy(node.test)
        then, or_else = self._expression(node.body), self._expression(node.orelse)
        if then.type() != or_else.type():
            if self._is_number(then.type()) and self._is_number(or_else.type()):
                then, or_else = self._coerce(then, DOUBLE, node), self._coerce(or_else, DOUBLE, node)
            elif NONE in (then.type(), or_else.type()):
                other = or_else.type() if then.type() == NONE else then.type()
                # a number or a boolean on the other branch is boxed, since the value may be None
                other = BOXED_TYPES.get({LONG: "int", DOUBLE: "float", BOOLEAN: "bool"}.get(other), other)
                then, or_else = self._coerce(then, other, node), self._coerce(or_else, other, node)
            else:
                self._refuse("unsupported-expression", "a conditional expression whose branches differ in type has no static lowering", node)
        return Conditional(test, then, or_else, then.type())

    def _truthy(self, node):
        """The boolean a test evaluates to, with Python's truthiness for the value's type."""
        value = self._expression(node)
        value_type = value.type()
        if value_type == BOOLEAN:
            return value
        if value_type == NONE:
            return Const(False, BOOLEAN)
        if value_type in (LONG, DOUBLE, STRING):
            if value_type == STRING:
                self.helper_calls += 1
            return Truthy(value)
        if value_type == "java.lang.CharSequence":
            self.helper_calls += 1
            return Truthy(value)
        facts = self.checker.facts
        if value_type not in JAVA_NUMBERS and (facts.isAssignable(value_type, "java.util.Collection") or facts.isAssignable(value_type, "java.util.Map")):
            self.helper_calls += 1
            return Truthy(value)
        if getattr(self.checker, "python_classes", None) is not None and self.checker.python_classes.by_qualified.get(_erased(value_type)) is not None:
            # an object of a class of the compilation is true unless None: the design leaves __bool__ and __len__ aside
            return Compare("is not None", value, None)
        self._refuse("truthiness-of-java-object", f"the truthiness of a [{value_type}] has no static lowering", node)


__all__ = ["Lowering"]
