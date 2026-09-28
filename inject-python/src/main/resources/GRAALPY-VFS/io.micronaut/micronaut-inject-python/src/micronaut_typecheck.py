"""
Static type checking of Python sources against the Java types they use.

The checker runs in two passes. While the processor visits a module (micronaut_processor.py),
it hands the checker every class, function and module it models together with the AST node the
model came from; the checker only records them (:meth:`TypeChecker.add_class`,
:meth:`TypeChecker.add_function`, :meth:`TypeChecker.add_module`). Once every source of the
compilation is modelled, :meth:`TypeChecker.check` resolves which of the recorded functions are
checked and at which severity, and runs the checks over them.

Which functions are checked is decided by the compilation mode (``off``, ``warn`` or ``error``)
and by the ``TypeChecked`` decorator, whose accepted names are configured: the nearest
declaration wins, a function over its class over its module over the mode; ``@TypeChecked``
under mode ``off`` checks that scope at ``error`` severity, ``@TypeChecked(False)`` leaves it
unchecked.
"""
import ast
import keyword

import java

PythonDiagnostic = java.type("io.micronaut.python.processing.diagnostic.PythonDiagnostic")
TypeFacts = java.type("io.micronaut.python.processing.typecheck.TypeFacts")

MODE_OFF = "off"
MODE_WARN = "warn"
MODE_ERROR = "error"
MODES = (MODE_OFF, MODE_WARN, MODE_ERROR)


def _switch_value(value):
    """The boolean a ``TypeChecked`` member value denotes; a string spelling of the literal counts too."""
    if isinstance(value, str):
        return value.strip().lower() not in ("false", "0", "")
    return bool(value)


class TypeCheckScope:
    """
    Resolves the severity a definition is checked at from its decorators and its enclosing scope.
    """

    def __init__(self, mode, annotation_names):
        if mode not in MODES:
            raise ValueError(f"Unknown type-check mode [{mode}]; expected one of {', '.join(MODES)}")
        self.mode = mode
        self.annotation_names = frozenset(annotation_names or ())

    @property
    def default_severity(self):
        """The severity of a definition without an enclosing switch: None when the mode is off."""
        return None if self.mode == MODE_OFF else self.mode

    def switch(self, decorators):
        """
        True or False when one of the decorators is an accepted ``TypeChecked`` switch, None otherwise.
        A bare ``@TypeChecked`` switches checking on.
        """
        for decorator in decorators or ():
            if decorator.annotationName() in self.annotation_names:
                members = decorator.members()
                value = members.get("value") if members is not None else None
                return True if value is None else _switch_value(value)
        return None

    def severity(self, decorators, enclosing):
        """
        The severity of a definition: its own switch when it has one, else the severity of the
        enclosing scope. Switching on under mode ``off`` checks at ``error`` severity.
        """
        switch = self.switch(decorators)
        if switch is None:
            return enclosing
        if not switch:
            return None
        return MODE_ERROR if self.mode == MODE_OFF else self.mode


class CheckUnit:
    """
    A definition that may be checked: a function (the model of the function, the AST node its body is
    in, the class it belongs to or None for a module-level function) or a class itself (no function,
    whose own decorators are checked), with the severity resolved for it (``warn``, ``error`` or None
    when it is not checked).
    """

    def __init__(self, source_path, qualified_name, function_def, node, class_def, severity, module=None):
        self.source_path = source_path
        self.qualified_name = qualified_name
        self.function_def = function_def
        self.node = node
        self.class_def = class_def
        self.severity = severity
        self.module = module

    def __repr__(self):
        return f"CheckUnit({self.qualified_name}, {self.severity})"


class _ModuleRecord:
    def __init__(self, source_path):
        self.source_path = source_path
        self.decorators = []
        self.classes = []      # (class_def, node)
        self.functions = []    # (function_def, node)
        self.visitor = None    # the MicronautAstVisitor that modelled the module: locations and name bindings
        self.span_of = lambda node: None


class TypeChecker:
    """
    Collects the definitions of a compilation while they are modelled and checks them afterwards.
    """

    def __init__(self, mode, annotation_names):
        self.scope = TypeCheckScope(mode, annotation_names)
        self._modules = {}
        self.diagnostics = []

    # ---------------------------------------------------------------- pass one

    def _module(self, source_path):
        module = self._modules.get(source_path)
        if module is None:
            module = self._modules[source_path] = _ModuleRecord(source_path)
        return module

    def begin_module(self, source_path, visitor):
        """Record the visitor modelling a module, before its definitions arrive: it locates the nodes and knows the module's name bindings."""
        module = self._module(source_path)
        module.visitor = visitor
        module.span_of = visitor._span

    def add_class(self, source_path, class_def, node):
        """Record a completed top-level or nested class with the AST node it came from."""
        self._module(source_path).classes.append((class_def, node))

    def add_function(self, source_path, function_def, node):
        """Record a module-level function with the AST node it came from."""
        self._module(source_path).functions.append((function_def, node))

    def add_module(self, source_path, decorators):
        """Record the annotations applied at module level, which switch the whole module."""
        self._module(source_path).decorators = list(decorators or ())

    # ---------------------------------------------------------------- pass two

    def units(self):
        """
        The functions of the compilation with the severity resolved for each; not-checked functions
        carry severity None. Order follows the sources.
        """
        units = []
        for module in self._modules.values():
            module_severity = self.scope.severity(module.decorators, self.scope.default_severity)
            for function_def, node in module.functions:
                severity = self.scope.severity(function_def.decorators(), module_severity)
                units.append(CheckUnit(module.source_path, function_def.name(), function_def, node, None, severity, module))
            for class_def, node in module.classes:
                class_severity = self.scope.severity(class_def.decorators(), module_severity)
                units.append(CheckUnit(module.source_path, class_def.name(), None, node, class_def, class_severity, module))
                members = list(class_def.functions())
                if class_def.constructor() is not None:
                    members.append(class_def.constructor())
                for function_def in members:
                    severity = self.scope.severity(function_def.decorators(), class_severity)
                    qualified_name = f"{class_def.name()}.{function_def.name()}"
                    units.append(CheckUnit(module.source_path, qualified_name, function_def, _function_node(node, function_def.name()), class_def, severity, module))
        return units

    def check(self, visitor_context):
        """
        Run the checks over the checked definitions and return the diagnostics found, as Java
        PythonDiagnostic values.
        """
        self.facts = TypeFacts(visitor_context)
        for unit in self.units():
            if unit.severity is None or unit.node is None:
                continue
            DecoratorRules(self, unit).check()
            if unit.function_def is not None:
                JavaReceiverRules(self, unit).check()
        return list(self.diagnostics)

    def report(self, unit, rule, message, span, suggestions=()):
        """Record a finding of a rule at the severity of the unit it was found in."""
        factory = PythonDiagnostic.error if unit.severity == MODE_ERROR else PythonDiagnostic.warning
        diagnostic = factory(rule, message, span)
        if suggestions:
            diagnostic = diagnostic.withSuggestions(list(suggestions))
        self.diagnostics.append(diagnostic)


# The Java types a Python literal of each kind may be given to. Anything not listed (Object, a nested
# annotation, an unknown type) takes any literal: the checker has no opinion rather than a guess.
_JAVA_STRING = {"java.lang.String", "java.lang.CharSequence"}
_JAVA_CHAR = {"char", "java.lang.Character"}
_JAVA_INT = {"int", "long", "short", "byte", "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte"}
_JAVA_FLOAT = {"double", "float", "java.lang.Double", "java.lang.Float"}
_JAVA_BOOL = {"boolean", "java.lang.Boolean"}
_JAVA_CLASS = {"java.lang.Class"}


def literal_kind(node):
    """The kind of a literal AST node: str, int, float, bool, bytes or none; None for anything else."""
    if isinstance(node, ast.Constant):
        value = node.value
        if value is None:
            return "none"
        if isinstance(value, bool):
            return "bool"
        if isinstance(value, int):
            return "int"
        if isinstance(value, float):
            return "float"
        if isinstance(value, str):
            return "str"
        if isinstance(value, bytes):
            return "bytes"
    return None


def literal_fits(kind, member):
    """Whether a literal of the kind can be the value of the annotation member."""
    java_type = member.type()
    if java_type in _JAVA_STRING or java_type in _JAVA_CHAR or java_type in _JAVA_CLASS or member.enumType():
        return kind == "str"
    if java_type in _JAVA_INT:
        return kind == "int"
    if java_type in _JAVA_FLOAT:
        return kind in ("int", "float")
    if java_type in _JAVA_BOOL:
        return kind == "bool"
    return True


def edit_distance(left, right):
    previous = list(range(len(right) + 1))
    for i, l in enumerate(left, 1):
        current = [i]
        for j, r in enumerate(right, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (l != r)))
        previous = current
    return previous[-1]


def camel_case(name):
    head, *tail = name.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


def suggest(name, candidates):
    """
    The candidates a misspelt name most likely meant: the same name in another case, its camel-case
    spelling, then names within two edits (one edit for short names).
    """
    candidates = [candidate for candidate in candidates if candidate != name]
    lowered = name.lower()
    same_case = [candidate for candidate in candidates if candidate.lower() == lowered]
    if same_case:
        return same_case
    camel = camel_case(name)
    if camel != name and camel in candidates:
        return [camel]
    limit = 2 if len(name) > 4 else 1
    close = sorted((candidate for candidate in candidates if edit_distance(name, candidate) <= limit),
                   key=lambda candidate: (edit_distance(name, candidate), candidate))
    return close[:3]


def _did_you_mean(suggestions):
    return f"; did you mean [{', '.join(suggestions)}]?" if suggestions else ""


class DecoratorRules:
    """
    The checks of the decorators of one definition against the Java annotations they resolve to:
    every member named exists, every literal fits its member, positional arguments name a value
    member, and the annotation targets the kind of definition it is applied to.
    """

    def __init__(self, checker, unit):
        self.checker = checker
        self.unit = unit
        self.facts = checker.facts
        self.span_of = unit.module.span_of
        self.kind = "function" if unit.function_def is not None else "class"
        # the targets a decorator of this definition may have: a function is a method (a constructor for
        # __init__), a class a type; an around binding on a class advises every method, see _targets_allow
        if unit.function_def is None:
            self.targets = {"TYPE", "ANNOTATION_TYPE"}
        elif unit.function_def.name() == "__init__":
            self.targets = {"METHOD", "CONSTRUCTOR"}
        else:
            self.targets = {"METHOD"}

    def check(self):
        definition = self.unit.function_def if self.unit.function_def is not None else self.unit.class_def
        nodes = list(getattr(self.unit.node, "decorator_list", ()))
        for decorator in definition.decorators():
            node = self._decorator_node(decorator, nodes)
            if node is None:
                continue
            description = self.facts.describeAnnotation(self._annotation_name(decorator))
            if description is None:
                # a plain Python decorator (dataclass, property, a function of the application)
                continue
            simple_name = description.simpleName()
            if not description.annotation():
                self._report("not-an-annotation",
                             f"[{description.name()}] is not an annotation and cannot decorate {self.kind} [{self.unit.qualified_name}]",
                             node)
                continue
            targets = list(description.targets())
            if targets and not self._targets_allow(targets, description):
                self._report("decorator-target",
                             f"@{simple_name} cannot be applied to {self.kind} [{self.unit.qualified_name}]; its targets are [{', '.join(targets)}]",
                             node)
            if isinstance(node, ast.Call):
                self._check_arguments(node, description, simple_name)

    def _check_arguments(self, call, description, simple_name):
        members = description.members()
        member_names = list(members.keySet())
        # a decorator defined in Python takes its positional arguments in the order of its parameters,
        # and types its untyped members as strings, so only the member names are checked for it
        python_defined = description.pythonDefined()
        if call.args:
            if python_defined:
                if len(call.args) > len(member_names):
                    self._report("decorator-positional",
                                 f"@{simple_name} takes {len(member_names)} positional arguments ({', '.join(member_names)}); got {len(call.args)}",
                                 call.args[len(member_names)])
            else:
                value = members.get("value")
                if value is None:
                    message = (f"@{simple_name} takes no arguments" if not member_names
                               else f"@{simple_name} has no [value] member for a positional argument; name the member, one of [{', '.join(member_names)}]")
                    self._report("decorator-positional", message, call.args[0])
                elif len(call.args) > 1:
                    self._report("decorator-positional",
                                 f"@{simple_name} takes one positional argument, its [value] member; name the other members",
                                 call.args[1])
                else:
                    self._check_value("value", value, call.args[0], simple_name)
        for kw in call.keywords:
            if kw.arg is None:
                continue
            name = kw.arg[:-1] if kw.arg.endswith("_") and keyword.iskeyword(kw.arg[:-1]) else kw.arg
            member = members.get(name)
            if member is None:
                suggestions = suggest(name, member_names)
                self._report("unknown-decorator-member",
                             f"@{simple_name} has no member [{name}]{_did_you_mean(suggestions)}",
                             kw, suggestions)
            elif not python_defined:
                self._check_value(name, member, kw.value, simple_name)

    def _check_value(self, name, member, value_node, simple_name):
        if isinstance(value_node, (ast.List, ast.Tuple)):
            if not member.array():
                self._report("decorator-member-type",
                             f"member [{name}] of @{simple_name} is {member.typeName()}; got {'list' if isinstance(value_node, ast.List) else 'tuple'}",
                             value_node)
                return
            elements = value_node.elts
        else:
            elements = [value_node]
        for element in elements:
            kind = literal_kind(element)
            if kind is None:
                continue
            if not literal_fits(kind, member):
                self._report("decorator-member-type",
                             f"member [{name}] of @{simple_name} is {member.typeName()}; got {kind}",
                             element)
            elif member.enumType() and kind == "str":
                constants = list(member.enumConstants())
                if constants and element.value not in constants:
                    suggestions = suggest(element.value, constants)
                    self._report("decorator-member-type",
                                 f"member [{name}] of @{simple_name} is {member.typeName()}, which has no constant [{element.value}]{_did_you_mean(suggestions)}",
                                 element, suggestions)

    def _targets_allow(self, targets, description):
        if any(target in self.targets for target in targets):
            return True
        # a method-targeted around or introduction binding on a class advises all of its methods
        return self.kind == "class" and description.interceptorBinding() and "METHOD" in targets

    def _targets_allow(self, targets, description):
        if any(target in self.targets for target in targets):
            return True
        # a method-targeted around or introduction binding on a class advises all of its methods
        return self.kind == "class" and description.interceptorBinding() and "METHOD" in targets

    def _annotation_name(self, decorator):
        """
        The qualified name a decorator resolves to. A bare name the module bound with java.type()
        (an imported Java class used as a decorator) is resolved through that binding.
        """
        name = decorator.annotationName()
        visitor = self.unit.module.visitor
        if "." not in name and visitor is not None:
            return getattr(visitor, "java_type_assignments", {}).get(name, name)
        return name

    def _decorator_node(self, decorator, nodes):
        """The AST decorator expression a DecoratorDef came from, matched by its location."""
        span = decorator.span()
        if span is None:
            return None
        for node in nodes:
            node_span = self.span_of(node)
            if node_span is not None and node_span.line() == span.line() and node_span.column() == span.column():
                return node
        return None

    def _report(self, rule, message, node, suggestions=()):
        self.checker.report(self.unit, rule, message, self.span_of(node), suggestions)


def _function_node(class_node, name):
    """The AST node of the method of the given name in a class body, or None."""
    if class_node is None:
        return None
    for statement in getattr(class_node, "body", ()):
        if getattr(statement, "name", None) == name and hasattr(statement, "args"):
            return statement
    return None


# ---------------------------------------------------------------- expression types

# The types the checker infers for expressions. Unknown (None) is the top: anything involving it is
# unknown too and never reported.
JAVA_REF = "javaref"     # the Java class itself, as a value: static members and constructors
JAVA = "java"            # an instance of a Java type
PY_REF = "pyref"         # a Python class of the compilation, as a value
PY = "py"                # an instance of a Python class of the compilation
BUILTIN = "builtin"      # a Python value: str, int, float, bool, none, bytes, list, dict, set, tuple
MODULE = "module"        # a Java package or a Python module
CALLABLE = "callable"    # a method referenced without a call

BUILTIN_HINTS = {
    "str": "str", "int": "int", "float": "float", "bool": "bool", "bytes": "bytes", "bytearray": "bytes",
    "list": "list", "dict": "dict", "set": "set", "frozenset": "set", "tuple": "tuple", "None": "none",
    "typing.List": "list", "typing.Dict": "dict", "typing.Set": "set", "typing.Tuple": "tuple",
    "List": "list", "Dict": "dict", "Set": "set", "Tuple": "tuple",
}
BUILTIN_JAVA_TYPES = {"java.lang.String": "str", "java.lang.CharSequence": "str", "boolean": "bool", "java.lang.Boolean": "bool",
                      "int": "int", "long": "int", "short": "int", "byte": "int", "java.lang.Integer": "int", "java.lang.Long": "int",
                      "java.lang.Short": "int", "java.lang.Byte": "int", "double": "float", "float": "float",
                      "java.lang.Double": "float", "java.lang.Float": "float", "void": "none"}
# every Java object has these, whether or not the element model lists them
OBJECT_METHODS = {"equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait"}
# the members GraalPy adds to host objects and classes
HOST_MEMBERS = {"class_"}
# the Python mapping methods GraalPy gives a Java Map
MAPPING_METHODS = {"keys", "values", "items", "get"}
# the methods every enum has, whether or not the element model lists them: those of the class and those of a constant
ENUM_STATIC_METHODS = {"values", "valueOf"}
ENUM_INSTANCE_METHODS = {"name", "ordinal", "compareTo", "getDeclaringClass", "describeConstable"}
# Java values GraalPy presents as Python standard types (a LocalDate is a datetime.date with its Java
# methods as well): their Python members are not modelled, so a value of one is an open type (a member
# it lacks is never reported) that flows through hints, fields, arguments and returns
STANDARD_TYPES = {"java.time.LocalDate", "java.time.LocalTime", "java.time.LocalDateTime", "java.time.Duration",
                  "java.time.ZoneOffset", "java.time.ZonedDateTime", "java.time.Instant", "java.util.UUID",
                  "java.math.BigInteger", "java.math.BigDecimal"}


class Typed:
    """
    An inferred type: its kind and its name (a qualified Java name, a Python kind or a ClassDef).
    An open type is a declared one whose value may be any implementation (a parameter hinted with
    an interface or an abstract class): a member the type lacks is not reported on it.
    """

    def __init__(self, kind, name, open=False, nullable=False):
        self.kind = kind
        self.name = name
        self.open = open
        # whether the value may be None as well: a hint of X | None or Optional[X]
        self.nullable = nullable

    def as_nullable(self):
        """This type, marked as possibly None."""
        return Typed(self.kind, self.name, self.open, nullable=True)

    def __repr__(self):
        return f"{self.kind}:{self.name}"

    def label(self):
        """The simple name of the type, as a message shows it."""
        if self.kind == BUILTIN:
            return self.name
        if self.kind in (PY, PY_REF):
            return self.name.name()
        return self.name.rsplit(".", 1)[-1]

    def java_name(self):
        """The name TypeFacts.isAssignable understands for a value of this type."""
        if self.kind == BUILTIN:
            return "python:" + self.name
        if self.kind == JAVA:
            return self.name
        return None


def of_java_type(name):
    """
    The type of a value of a Java type: a Python value for the types the runtime converts, unknown
    for Object, which is what an erased type variable (the value of a Map, the element of a List)
    reads as and says nothing about the actual value.
    """
    if name in BUILTIN_JAVA_TYPES:
        return Typed(BUILTIN, BUILTIN_JAVA_TYPES[name])
    if name == "java.lang.Object" or name.endswith("[]"):
        return None
    if name in STANDARD_TYPES:
        return Typed(JAVA, name, open=True)
    return Typed(JAVA, name)


class Bindings:
    """
    The names a function body can refer to and what they are: the module's imports and java.type()
    aliases, its classes, the function's parameters, and the locals assigned in the body.
    """

    def __init__(self, checker, unit):
        self.checker = checker
        self.facts = checker.facts
        self.unit = unit
        self.visitor = unit.module.visitor
        self.classes = {class_def.name(): class_def for class_def, _ in unit.module.classes}
        self.locals = {}
        self.unknown_locals = set()
        self._parameters()

    def _parameters(self):
        function_def = self.unit.function_def
        if self.unit.class_def is not None:
            if function_def.isStatic():
                self.locals["cls"] = Typed(PY_REF, self.unit.class_def)
            else:
                self.locals["self"] = Typed(PY, self.unit.class_def)
        for argument in function_def.arguments().arguments():
            if argument.variadic():
                self.unknown_locals.add(argument.name())
                continue
            hint = argument.typeAnnotation()
            typed = self.of_hint(hint) if hint is not None else None
            if typed is None:
                self.unknown_locals.add(argument.name())
            else:
                self.locals[argument.name()] = typed
        # a keyword-only parameter or one the model does not carry is unknown, never wrong
        for argument in getattr(self.unit.node.args, "kwonlyargs", ()):
            self.unknown_locals.add(argument.arg)

    def of_hint(self, type_ref):
        """The type a parameter hint denotes, or None when it cannot be resolved."""
        if type_ref is None:
            return None
        if type_ref.isUnion():
            members = [member for member in type_ref.nonNoneMembers()]
            if len(members) != 1:
                return None
            typed = self.of_hint(members[0])
            return typed.as_nullable() if typed is not None and type_ref.isNullableUnion() else typed
        name = type_ref.name()
        if name in ("typing.Optional", "Optional") and type_ref.typeArguments():
            typed = self.of_hint(type_ref.typeArguments()[0])
            return typed.as_nullable() if typed is not None else None
        if name in BUILTIN_HINTS:
            return Typed(BUILTIN, BUILTIN_HINTS[name])
        typed = self.of_name(name, instance=True)
        if typed is not None and typed.kind == JAVA:
            if typed.name in STANDARD_TYPES:
                return Typed(JAVA, typed.name, open=True)
            description = self.checker.facts.describe(typed.name)
            if description is not None and (description.anInterface() or description.isAbstract()):
                # any implementation may be behind the hint, with members of its own
                return Typed(JAVA, typed.name, open=True)
        return typed

    def of_name(self, name, instance=False):
        """The type a (possibly qualified) name denotes in the module: a class, or None."""
        if name in self.classes:
            return Typed(PY if instance else PY_REF, self.classes[name])
        local_class = self.visitor._resolve_local_type_name(name) if self.visitor is not None else None
        if local_class is not None:
            simple = local_class.rsplit(".", 1)[-1]
            if simple in self.classes:
                return Typed(PY if instance else PY_REF, self.classes[simple])
            return None
        qualified = name
        if self.visitor is not None:
            qualified = self.visitor.java_type_assignments.get(name) or self.visitor.imported_types.get(name) or name
        if "." not in qualified:
            return None
        description = self.facts.describe(qualified)
        if description is None:
            return None
        annotation = self.facts.describeAnnotation(qualified)
        if annotation is not None and annotation.annotation():
            # an annotation is bound to its generated decorator function, whose attributes are its own
            return None
        if description.pythonDefined():
            # a Python class: its members are more than those of its generated class, so the Java rules
            # do not apply to it
            return None
        return Typed(JAVA if instance else JAVA_REF, description.name())

    def lookup(self, name):
        if name in self.unknown_locals:
            return None
        if name in self.locals:
            return self.locals[name]
        return self.of_name(name)

    def assign(self, name, typed):
        if name in self.unknown_locals:
            return
        if typed is None:
            self.forget(name)
            return
        previous = self.locals.get(name)
        if previous is not None and (previous.kind, previous.name) != (typed.kind, typed.name):
            # assigned different types: no flow analysis, so the name is unknown for the whole body
            self.forget(name)
            return
        self.locals[name] = typed

    def forget(self, name):
        self.locals.pop(name, None)
        self.unknown_locals.add(name)


def _bound_names(target):
    if isinstance(target, ast.Name):
        return [target.id]
    if isinstance(target, (ast.Tuple, ast.List)):
        return [name for element in target.elts for name in _bound_names(element)]
    if isinstance(target, ast.Starred):
        return _bound_names(target.value)
    return []


class JavaReceiverRules:
    """
    The checks of calls, attribute reads and constructions whose receiver is a Java type: the member
    exists, an overload accepts the arguments, and an abstract type is not instantiated. Anything the
    checker cannot type is unknown and never reported.
    """

    def __init__(self, checker, unit):
        self.checker = checker
        self.unit = unit
        self.facts = checker.facts
        self.bindings = Bindings(checker, unit)
        self.span_of = unit.module.span_of

    def check(self):
        for statement in getattr(self.unit.node, "body", ()):
            self.statement(statement)

    # ---------------------------------------------------------------- statements

    def statement(self, node):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef, ast.Lambda)):
            return  # a nested scope has its own names; it is not checked here
        if isinstance(node, (ast.Assign, ast.AnnAssign, ast.AugAssign)):
            self.assignment(node)
            return
        if isinstance(node, (ast.For, ast.AsyncFor, ast.comprehension)):
            for name in _bound_names(node.target):
                self.bindings.forget(name)
            self.expression(node.iter)
            for child in getattr(node, "body", []) + getattr(node, "orelse", []):
                self.statement(child)
            return
        if isinstance(node, (ast.With, ast.AsyncWith)):
            for item in node.items:
                self.expression(item.context_expr)
                if item.optional_vars is not None:
                    for name in _bound_names(item.optional_vars):
                        self.bindings.forget(name)
            for child in node.body:
                self.statement(child)
            return
        if isinstance(node, (ast.Try, ast.TryStar)):
            for handler in node.handlers:
                if handler.name:
                    self.bindings.forget(handler.name)
            for child in node.body + node.orelse + node.finalbody:
                self.statement(child)
            for handler in node.handlers:
                for child in handler.body:
                    self.statement(child)
            return
        if isinstance(node, ast.stmt):
            for field, value in ast.iter_fields(node):
                if isinstance(value, ast.expr):
                    self.expression(value)
                elif isinstance(value, list):
                    for child in value:
                        if isinstance(child, ast.stmt):
                            self.statement(child)
                        elif isinstance(child, ast.expr):
                            self.expression(child)

    def assignment(self, node):
        if isinstance(node, ast.AugAssign):
            self.expression(node.value)
            for name in _bound_names(node.target):
                self.bindings.forget(name)
            return
        value = getattr(node, "value", None)
        typed = self.expression(value) if value is not None else None
        targets = node.targets if isinstance(node, ast.Assign) else [node.target]
        for target in targets:
            if isinstance(target, ast.Name):
                if isinstance(node, ast.AnnAssign):
                    hinted = self._hint_type(node.annotation)
                    self.bindings.assign(target.id, hinted if hinted is not None else typed)
                else:
                    self.bindings.assign(target.id, typed)
            elif isinstance(target, ast.Attribute):
                self.expression(target.value)
            elif isinstance(target, ast.Subscript):
                self.expression(target.value)
                self.expression(target.slice)
            else:
                for name in _bound_names(target):
                    self.bindings.forget(name)

    def _hint_type(self, annotation):
        if self.bindings.visitor is None:
            return None
        try:
            return self.bindings.of_hint(self.bindings.visitor._parse_type(annotation))
        except Exception:
            return None

    # ---------------------------------------------------------------- expressions

    def expression(self, node):
        """Check an expression and return its type, or None when unknown."""
        if node is None:
            return None
        if isinstance(node, ast.Constant):
            kind = literal_kind(node)
            return Typed(BUILTIN, kind) if kind is not None else None
        if isinstance(node, ast.JoinedStr):
            for value in node.values:
                if isinstance(value, ast.FormattedValue):
                    self.expression(value.value)
            return Typed(BUILTIN, "str")
        if isinstance(node, ast.Name):
            return self.bindings.lookup(node.id)
        if isinstance(node, ast.Attribute):
            return self.attribute(node)
        if isinstance(node, ast.Call):
            return self.call(node)
        if isinstance(node, (ast.List, ast.Tuple, ast.Set)):
            for element in node.elts:
                self.expression(element)
            return Typed(BUILTIN, "list" if isinstance(node, ast.List) else "tuple" if isinstance(node, ast.Tuple) else "set")
        if isinstance(node, ast.Dict):
            for key in node.keys:
                self.expression(key)
            for value in node.values:
                self.expression(value)
            return Typed(BUILTIN, "dict")
        if isinstance(node, ast.BinOp):
            left = self.expression(node.left)
            right = self.expression(node.right)
            if left is not None and right is not None and left.kind == BUILTIN and right.kind == BUILTIN:
                if left.name == "str" and right.name == "str" and isinstance(node.op, ast.Add):
                    return Typed(BUILTIN, "str")
                if left.name in ("int", "float") and right.name in ("int", "float"):
                    return Typed(BUILTIN, "float" if "float" in (left.name, right.name) or isinstance(node.op, ast.Div) else "int")
            return None
        if isinstance(node, (ast.Compare, ast.BoolOp, ast.UnaryOp)):
            for child in ast.iter_child_nodes(node):
                if isinstance(child, ast.expr):
                    self.expression(child)
            return Typed(BUILTIN, "bool") if isinstance(node, ast.Compare) or (isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.Not)) else None
        if isinstance(node, ast.IfExp):
            # a conditional of one type has that type; one branch None makes it nullable
            self.expression(node.test)
            then, or_else = self.expression(node.body), self.expression(node.orelse)
            if then is None or or_else is None:
                return None
            if (then.kind, then.name) == (or_else.kind, or_else.name):
                return then.as_nullable() if then.nullable or or_else.nullable else then
            if then.name == "none" and then.kind == BUILTIN:
                return or_else.as_nullable()
            if or_else.name == "none" and or_else.kind == BUILTIN:
                return then.as_nullable()
            return None
        if isinstance(node, (ast.Lambda, ast.ListComp, ast.SetComp, ast.DictComp, ast.GeneratorExp)):
            return None  # its own scope; not checked here
        for child in ast.iter_child_nodes(node):
            if isinstance(child, ast.expr):
                self.expression(child)
        return None

    def attribute(self, node, calling=False):
        receiver = self.expression(node.value)
        if receiver is None or receiver.kind == MODULE:
            return self._dotted_reference(node)
        name = node.attr
        if name.startswith("__") or name in HOST_MEMBERS:
            return Typed(JAVA_REF if name == "class_" else JAVA, "java.lang.Class") if name == "class_" and receiver.kind in (JAVA, JAVA_REF) else None
        if receiver.kind in (JAVA, JAVA_REF):
            return self._java_member(receiver, name, node, calling)
        if receiver.kind == BUILTIN:
            return None  # the methods of Python values are not checked
        return None

    def _dotted_reference(self, node):
        """
        A fully qualified Java class written out (java.util.ArrayList after `import java`), whose
        root is no name of the function; None for any other attribute chain the checker cannot type.
        """
        parts = []
        while isinstance(node, ast.Attribute):
            parts.append(node.attr)
            node = node.value
        if not isinstance(node, ast.Name) or node.id in self.bindings.locals or node.id in self.bindings.unknown_locals:
            return None
        parts.append(node.id)
        return self.bindings.of_name(".".join(reversed(parts)))

    def _java_member(self, receiver, name, node, calling):
        description = self.facts.describe(receiver.name)
        if description is None:
            return None
        member = name[:-1] if name.endswith("_") and keyword.iskeyword(name[:-1]) else name
        methods = description.methods()
        fields = description.fields()
        nested = description.nestedTypes()
        if member in OBJECT_METHODS or (description.anEnum() and member in self._enum_methods(receiver)):
            return Typed(CALLABLE, member)
        if member in MAPPING_METHODS and self.facts.isAssignable(description.name(), "java.util.Map"):
            return Typed(CALLABLE, member)
        if methods.containsKey(member):
            if receiver.kind == JAVA_REF and not calling and not any(signature.isStatic() for signature in methods.get(member)):
                self._report("static-access",
                             f"[{description.simpleName()}.{member}] is an instance method; read it on an instance of [{description.name()}]",
                             node)
                return None
            return Typed(CALLABLE, member) if not calling else None
        if fields.containsKey(member):
            if receiver.kind == JAVA_REF and not description.staticFields().contains(member):
                self._report("static-access",
                             f"[{description.simpleName()}.{member}] is an instance field; read it on an instance of [{description.name()}]",
                             node)
                return None
            return of_java_type(fields.get(member))
        if nested.containsKey(member):
            return Typed(JAVA_REF, nested.get(member))
        if description.enumConstants().contains(member):
            return Typed(JAVA, description.name())
        if receiver.kind == JAVA_REF and self.facts.describe(f"{description.name()}${member}") is not None:
            return Typed(JAVA_REF, f"{description.name()}${member}")
        if description.open() or receiver.open:
            return None  # a hidden supertype or the actual implementation may contribute the member
        candidates = list(methods.keySet()) + list(fields.keySet()) + list(nested.keySet())
        suggestions = suggest(member, candidates)
        if calling:
            self._report("unknown-method",
                         f"Java type [{description.name()}] has no method named [{member}]{_did_you_mean(suggestions)}",
                         node, suggestions)
        else:
            getter = next((candidate for candidate in ("get" + member[:1].upper() + member[1:], "is" + member[:1].upper() + member[1:]) if methods.containsKey(candidate)), None)
            if getter is not None and not suggestions:
                suggestions = [getter + "()"]
            self._report("unknown-attribute",
                         f"Java type [{description.name()}] has no member named [{member}]{_did_you_mean(suggestions)}",
                         node, suggestions)
        return None

    def call(self, node):
        function = node.func
        argument_types = [self.expression(argument) for argument in node.args if not isinstance(argument, ast.Starred)]
        for kw in node.keywords:
            self.expression(kw.value)
        star_args = any(isinstance(argument, ast.Starred) for argument in node.args)
        kwargs = bool(node.keywords)
        if isinstance(function, ast.Attribute):
            receiver = self.expression(function.value)
            if receiver is None or receiver.kind == MODULE:
                target = self._dotted_reference(function)
                if target is not None and target.kind == JAVA_REF:
                    return self._java_construction(target, node, argument_types, star_args, kwargs)
                return None
            name = function.attr
            if name.startswith("__") or name in HOST_MEMBERS:
                return None
            if receiver.kind in (JAVA, JAVA_REF):
                return self._java_call(receiver, name, node, argument_types, star_args, kwargs)
            return None
        if isinstance(function, ast.Name):
            target = self.bindings.lookup(function.id)
            if target is None:
                return None
            if target.kind == JAVA_REF:
                return self._java_construction(target, node, argument_types, star_args, kwargs)
            if target.kind == PY_REF:
                return Typed(PY, target.name)
            return None
        self.expression(function)
        return None

    def _java_call(self, receiver, name, node, argument_types, star_args, kwargs):
        description = self.facts.describe(receiver.name)
        if description is None:
            return None
        member = name[:-1] if name.endswith("_") and keyword.iskeyword(name[:-1]) else name
        methods = description.methods()
        if member in OBJECT_METHODS and not methods.containsKey(member):
            return None
        if description.anEnum() and not methods.containsKey(member):
            if member in self._enum_methods(receiver):
                return self._enum_result(description, member)
            if member in ENUM_INSTANCE_METHODS:
                self._report("static-access",
                             f"[{description.simpleName()}.{member}] is an instance method; call it on a constant of [{description.name()}]",
                             node)
                return None
        if member in MAPPING_METHODS and not methods.containsKey(member) and self.facts.isAssignable(description.name(), "java.util.Map"):
            return None  # a Java map is a Python mapping at run time
        if not methods.containsKey(member):
            fields = description.fields()
            if fields.containsKey(member):
                return None  # calling a field's value: not judged here
            nested = description.nestedTypes().get(member) if description.nestedTypes().containsKey(member) else None
            if nested is None and receiver.kind == JAVA_REF and self.facts.describe(f"{description.name()}${member}") is not None:
                nested = f"{description.name()}${member}"
            if nested is not None:
                return self._java_construction(Typed(JAVA_REF, nested), node, argument_types, star_args, kwargs)
            self._java_member(receiver, member, node.func, calling=True)
            return None
        signatures = list(methods.get(member))
        if receiver.kind == JAVA_REF:
            # the class itself only offers its static methods
            static = [signature for signature in signatures if signature.isStatic()]
            if not static:
                self._report("static-access",
                             f"[{description.simpleName()}.{member}] is an instance method; call it on an instance of [{description.name()}]",
                             node)
                return None
            signatures = static
        if star_args or kwargs:
            return self._return_type(signatures)
        matching = self._matching(signatures, argument_types)
        if not matching:
            rendered = ", ".join(signature.render(member) for signature in signatures)
            self._report("no-matching-overload",
                         f"no overload of [{description.simpleName()}.{member}] accepts ({self._render_arguments(argument_types)}); candidates: {rendered}",
                         node)
            return None
        return self._return_type(matching)

    def _java_construction(self, target, node, argument_types, star_args, kwargs):
        description = self.facts.describe(target.name)
        if description is None:
            return None
        if description.anInterface() or description.isAbstract():
            kind = "an interface" if description.anInterface() else "abstract"
            self._report("abstract-instantiation", f"[{description.name()}] is {kind} and cannot be instantiated", node)
            return Typed(JAVA, description.name())
        if star_args or kwargs:
            return Typed(JAVA, description.name())
        constructors = list(description.constructors())
        if not constructors:
            if not description.pythonDefined():
                kind = "an enum" if description.anEnum() else "a type without an accessible constructor"
                self._report("unknown-constructor", f"[{description.name()}] is {kind} and cannot be instantiated", node)
        elif not self._matching(constructors, argument_types):
            rendered = ", ".join(signature.render(description.simpleName()) for signature in constructors)
            self._report("unknown-constructor",
                         f"no constructor of [{description.name()}] accepts ({self._render_arguments(argument_types)}); candidates: {rendered}",
                         node)
        return Typed(JAVA, description.name())

    def _matching(self, signatures, argument_types):
        """The signatures whose arity and parameter types accept the arguments; unknown arguments accept anything."""
        matching = []
        for signature in signatures:
            parameters = list(signature.parameterTypes())
            count = len(argument_types)
            if signature.varargs():
                if count < len(parameters) - 1:
                    continue
            elif count != len(parameters):
                continue
            fits = True
            for index, argument in enumerate(argument_types):
                if argument is None:
                    continue
                if signature.varargs() and index >= len(parameters) - 1:
                    parameter = parameters[-1]
                    parameter = parameter[:-2] if parameter.endswith("[]") and not (count == len(parameters) and argument.kind == BUILTIN and argument.name in ("list", "tuple")) else parameter
                else:
                    parameter = parameters[index]
                source = argument.java_name()
                if source is not None and not self.facts.isAssignable(source, parameter):
                    fits = False
                    break
            if fits:
                matching.append(signature)
        return matching

    def _return_type(self, signatures):
        types = {signature.returnType() for signature in signatures}
        if len(types) != 1:
            return None
        name = next(iter(types))
        if name == "void":
            return Typed(BUILTIN, "none")
        if name.endswith("[]"):
            return None  # arrays reach Python as sequences; their members are not judged
        return of_java_type(name)

    @staticmethod
    def _enum_methods(receiver):
        """The implicit enum methods a receiver offers: the class its static ones, a constant both."""
        return ENUM_STATIC_METHODS if receiver.kind == JAVA_REF else ENUM_STATIC_METHODS | ENUM_INSTANCE_METHODS

    @staticmethod
    def _enum_result(description, member):
        """What an implicit enum method returns: valueOf a constant, name a str, ordinal and compareTo an int."""
        if member == "valueOf":
            return Typed(JAVA, description.name())
        if member == "name":
            return Typed(BUILTIN, "str")
        if member in ("ordinal", "compareTo"):
            return Typed(BUILTIN, "int")
        return None

    @staticmethod
    def _render_arguments(argument_types):
        return ", ".join("?" if argument is None else argument.label() for argument in argument_types)

    def _report(self, rule, message, node, suggestions=()):
        self.checker.report(self.unit, rule, message, self.span_of(node), suggestions)
