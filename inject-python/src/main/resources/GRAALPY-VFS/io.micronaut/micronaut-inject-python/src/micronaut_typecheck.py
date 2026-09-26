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
        self.script = None     # the ScriptDef of the module, once modelled: the generated class of its functions and attributes
        self.visitor = None    # the MicronautAstVisitor that modelled the module: locations and name bindings
        self.span_of = lambda node: None
        self._class_index = None
        self._class_count = -1

    def class_index(self):
        """The classes of the module by name, built once the modelling is over and shared by every check."""
        if self._class_index is None or self._class_count != len(self.classes):
            self._class_index = {class_def.name(): class_def for class_def, _ in self.classes}
            self._class_count = len(self.classes)
        return self._class_index


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

    def set_script(self, source_path, script_def):
        """Record the script modelled for the module: the generated class its functions become methods of."""
        self._module(source_path).script = script_def

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
        self.python_classes = PythonClasses(self)
        self.inference = {}
        self._annotation_views = {}
        for unit in self.units():
            if unit.severity is None or unit.node is None:
                continue
            DecoratorRules(self, unit).check()
            if unit.function_def is not None:
                rules = JavaReceiverRules(self, unit)
                rules.check()
                # what the check inferred, for the static planner: the same inference, not a second run
                self.inference[id(unit.node)] = rules
        return list(self.diagnostics)

    def rules_of(self, model, function_def, function_node):
        """
        What the checker inferred for a method of a class of the compilation: the check's own
        inference when it ran, else a silent run of it, once per method. None while the method's
        own inference is under way (a method whose type depends on itself) or when it has no body.
        """
        if function_node is None:
            return None
        inference = self.__dict__.setdefault("inference", {})
        rules = inference.get(id(function_node))
        if rules is not None:
            return rules
        under_way = self.__dict__.setdefault("_inferring", set())
        if id(function_node) in under_way or getattr(self, "facts", None) is None:
            return None
        under_way.add(id(function_node))
        try:
            unit = CheckUnit(model.module.source_path, f"{model.name}.{function_def.name()}", function_def, function_node, model.class_def, None, model.module)
            rules = JavaReceiverRules(self, unit, silent=True)
            rules.check()
        finally:
            under_way.discard(id(function_node))
        inference[id(function_node)] = rules
        return rules

    def inferred_return(self, model, function_def, function_node):
        """
        The type of the values an unhinted method returns, when every return statement of its body
        has the one type the checker infers; None when they disagree or one is unknown.
        """
        rules = self.rules_of(model, function_def, function_node)
        if rules is None:
            return None
        returned = []
        for statement in _own_statements(function_node):
            if isinstance(statement, ast.Return):
                if statement.value is None:
                    return None
                typed = rules.node_types.get(id(statement.value))
                if typed is None:
                    return None
                returned.append(typed)
        if not returned:
            return None
        first = returned[0]
        if any((typed.kind, typed.name, tuple(getattr(typed, "args", ()))) != (first.kind, first.name, tuple(getattr(first, "args", ()))) for typed in returned[1:]):
            return None
        if first.kind in (JAVA, PY, BUILTIN) and first.name != "none":
            return first.as_nullable() if any(typed.nullable for typed in returned) else first
        return None

    def annotation_view(self, name):
        """The description of an annotation and its targets as a list, asked of the Java facts once per compilation."""
        views = self.__dict__.setdefault("_annotation_views", {})
        view = views.get(name)
        if view is None:
            description = self.facts.describeAnnotation(name)
            view = (description, list(description.targets()) if description is not None else [])
            views[name] = view
        return view

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
        nodes = self._decorator_nodes_by_position(getattr(self.unit.node, "decorator_list", ()))
        for decorator in definition.decorators():
            node = self._decorator_node(decorator, nodes)
            if node is None:
                continue
            description, targets = self.checker.annotation_view(self._annotation_name(decorator))
            if description is None:
                # a plain Python decorator (dataclass, property, a function of the application)
                continue
            simple_name = description.simpleName()
            if not description.annotation():
                self._report("not-an-annotation",
                             f"[{description.name()}] is not an annotation and cannot decorate {self.kind} [{self.unit.qualified_name}]",
                             node)
                continue
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

    def _decorator_nodes_by_position(self, nodes):
        """The decorator expressions of the definition by (line, column): each located once."""
        by_position = {}
        for node in nodes:
            node_span = self.span_of(node)
            if node_span is not None:
                by_position.setdefault((node_span.line(), node_span.column()), node)
        return by_position

    @staticmethod
    def _decorator_node(decorator, nodes):
        """The AST decorator expression a DecoratorDef came from, matched by its location."""
        span = decorator.span()
        return None if span is None else nodes.get((span.line(), span.column()))

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
# the Java types the builtin hints are as type arguments (a list[int] is a List<Integer>)
BOXED_JAVA_NAMES = {"str": "java.lang.String", "int": "java.lang.Integer", "float": "java.lang.Double", "bool": "java.lang.Boolean"}
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

    def __init__(self, kind, name, open=False, args=(), nullable=False):
        self.kind = kind
        self.name = name
        self.open = open
        # the type arguments when known (a list[int] hint, a List<Order> return type): Typed or None each
        self.args = tuple(args)
        # whether the value may be None as well: a hint of X | None or Optional[X]
        self.nullable = nullable

    def as_nullable(self):
        """This type, marked as possibly None."""
        return Typed(self.kind, self.name, self.open, self.args, nullable=True)

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
        if self.kind == PY:
            # a Python class passes to Java as its generated class
            return self.name.qualifiedName()
        return None


def of_java_type(name, args=()):
    """
    The type of a value of a Java type: a Python value for the types the runtime converts, unknown
    for Object, which is what an erased type variable (the value of a Map, the element of a List)
    reads as and says nothing about the actual value. The type arguments, when given, are the
    qualified names of the arguments of a parameterized type.
    """
    if name in BUILTIN_JAVA_TYPES:
        return Typed(BUILTIN, BUILTIN_JAVA_TYPES[name])
    if name == "java.lang.Object" or name.endswith("[]"):
        return None
    if name in STANDARD_TYPES:
        return Typed(JAVA, name, open=True)
    return Typed(JAVA, name, args=[of_java_type(argument) for argument in args])


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
        self.classes = unit.module.class_index()
        script = getattr(unit.module, "script", None)
        self.module_attributes = {attribute.name(): attribute for attribute in script.attributes()} if script is not None else {}
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
        args = [self.of_hint(argument) for argument in (type_ref.typeArguments() or [])]
        if name in BUILTIN_HINTS:
            return Typed(BUILTIN, BUILTIN_HINTS[name], args=args)
        typed = self.of_name(name, instance=True)
        if typed is not None and typed.kind == JAVA:
            if typed.name in STANDARD_TYPES:
                return Typed(JAVA, typed.name, open=True)
            description = self.checker.facts.describe(typed.name)
            if description is not None and (description.anInterface() or description.isAbstract()):
                # any implementation may be behind the hint, with members of its own
                return Typed(JAVA, typed.name, open=True, args=args)
            return Typed(JAVA, typed.name, args=args)
        return typed

    def of_name(self, name, instance=False):
        """The type a (possibly qualified) name denotes in the module: a class, a hinted module attribute's value, or None."""
        if name in self.module_attributes:
            hint = self.module_attributes[name].typeName()
            return self.of_hint(hint) if hint is not None else None
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
        model = self.checker.python_classes.by_qualified.get(qualified)
        if model is not None:
            return Typed(PY if instance else PY_REF, model.class_def)
        description = self.facts.describe(qualified)
        if description is None:
            return None
        annotation = self.facts.describeAnnotation(qualified)
        if annotation is not None and annotation.annotation():
            # an annotation is bound to its generated decorator function, whose attributes are its own
            return None
        if description.pythonDefined():
            # a Python class of another compilation: its members are more than those of its generated
            # class, so the Java rules do not apply to it
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


def _own_statements(function_node):
    """The statements of a function body, nested scopes excluded."""
    own = []
    stack = list(reversed(getattr(function_node, "body", [])))
    while stack:
        statement = stack.pop()
        if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef, ast.Lambda)):
            continue
        own.append(statement)
        for field in ("body", "orelse", "finalbody"):
            stack.extend(reversed(getattr(statement, field, []) or []))
        for handler in getattr(statement, "handlers", []) or []:
            stack.extend(reversed(handler.body))
    return own


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

    def __init__(self, checker, unit, silent=False):
        self.checker = checker
        self.unit = unit
        self.facts = checker.facts
        self.bindings = Bindings(checker, unit)
        self.span_of = unit.module.span_of
        # a silent run records what it infers for the static compiler instead of reporting: the
        # type of every expression, the target of every resolved call, and the problems found
        self.silent = silent
        self.problems = []
        self.node_types = {}
        self.targets = {}

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
            iterated = self.expression(node.iter)
            for name in _bound_names(node.target):
                self.bindings.forget(name)
            element = self._element_type(node.iter, iterated)
            if isinstance(node.target, ast.Name) and element is not None and not isinstance(node, ast.comprehension):
                self.bindings.assign(node.target.id, element)
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
                caught = self.expression(handler.type) if handler.type is not None else None
                if handler.name:
                    if caught is not None and caught.kind == JAVA_REF:
                        self.bindings.assign(handler.name, Typed(JAVA, caught.name))
                    else:
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

    def _element_type(self, iterable_node, iterated):
        """The type of the elements a for loop yields, when known: range() yields ints, a typed collection its element type, a map its keys, a str its characters."""
        if isinstance(iterable_node, ast.Call) and isinstance(iterable_node.func, ast.Name) and iterable_node.func.id == "range" and self.bindings.lookup("range") is None:
            return Typed(BUILTIN, "int")
        if iterated is None or not iterated.args:
            return Typed(BUILTIN, "str") if iterated is not None and iterated.kind == BUILTIN and iterated.name == "str" else None
        if iterated.kind == BUILTIN and iterated.name in ("list", "set", "tuple", "dict"):
            return iterated.args[0]
        if iterated.kind == JAVA and (self.facts.isAssignable(iterated.name, "java.lang.Iterable") or self.facts.isAssignable(iterated.name, "java.util.Map")):
            return iterated.args[0]
        return None

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
        typed = self._expression(node)
        if node is not None:
            self.node_types[id(node)] = typed
        return typed

    def _expression(self, node):
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
        if receiver.kind in (PY, PY_REF):
            return self._python_member(receiver, name, node, calling)
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

    def _described_receiver(self, receiver):
        """The description of a Java receiver: with its type arguments substituted when all of them are known."""
        if receiver.args and all(argument is not None for argument in receiver.args):
            names = []
            for argument in receiver.args:
                name = BOXED_JAVA_NAMES.get(argument.name) if argument.kind == BUILTIN else argument.java_name()
                if name is None:
                    break
                names.append(name)
            if len(names) == len(receiver.args):
                return self.facts.describe(receiver.name, names)
        return self.facts.describe(receiver.name)

    def _java_member(self, receiver, name, node, calling, description=None):
        description = description or self._described_receiver(receiver)
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
            self.targets[id(node)] = ("field", description.name(), member, fields.get(member), description.staticFields().contains(member))
            return of_java_type(fields.get(member))
        if nested.containsKey(member):
            return Typed(JAVA_REF, nested.get(member))
        if description.enumConstants().contains(member):
            self.targets[id(node)] = ("field", description.name(), member, description.name(), True)
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
        keyword_types = [self.expression(kw.value) for kw in node.keywords]
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
            if receiver.kind in (PY, PY_REF):
                return self._python_call(receiver, name, node, argument_types, star_args, keyword_types)
            return None
        if isinstance(function, ast.Name):
            target = self.bindings.lookup(function.id)
            if target is None:
                return None
            if target.kind == JAVA_REF:
                return self._java_construction(target, node, argument_types, star_args, kwargs)
            if target.kind == PY_REF:
                return self._python_construction(target, node, argument_types, star_args, keyword_types)
            return None
        self.expression(function)
        return None

    def _java_call(self, receiver, name, node, argument_types, star_args, kwargs, description=None):
        description = description or self._described_receiver(receiver)
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
        self.targets[id(node)] = ("method", description.name(), member, matching, receiver.kind == JAVA_REF)
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
        else:
            matching = self._matching(constructors, argument_types)
            if not matching:
                rendered = ", ".join(signature.render(description.simpleName()) for signature in constructors)
                self._report("unknown-constructor",
                             f"no constructor of [{description.name()}] accepts ({self._render_arguments(argument_types)}); candidates: {rendered}",
                             node)
            else:
                self.targets[id(node)] = ("constructor", description.name(), None, matching, False)
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
        args = list(signatures[0].returnTypeArguments()) if len(signatures) == 1 else []
        return self._own_class(of_java_type(name, args))

    def _own_class(self, typed):
        """A Java type that is the generated class of a Python class of the compilation is that class."""
        if typed is None:
            return None
        classes = getattr(self.checker, "python_classes", None)
        if classes is None:
            return typed
        args = [self._own_class(argument) for argument in typed.args]
        if typed.kind == JAVA and "." in typed.name:
            model = classes.by_qualified.get(typed.name)
            if model is not None:
                return Typed(PY, model.class_def, args=args, nullable=typed.nullable)
        return Typed(typed.kind, typed.name, typed.open, args, nullable=typed.nullable) if args != list(typed.args) else typed

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
        # the problems are kept in both modes: the static planner reads them off a reporting run too
        self.problems.append((rule, message, self.span_of(node)))
        if self.silent:
            return
        self.checker.report(self.unit, rule, message, self.span_of(node), suggestions)


# ---------------------------------------------------------------- Python receivers

# Python bases that add no members of their own; any other base the checker cannot see makes a class open
TRANSPARENT_BASES = {"object", "abc.ABC", "ABC", "typing.Protocol", "Protocol", "typing.Generic", "Generic", "Generic[...]"}


def _self_attributes(class_node):
    """The names assigned as self.<name> anywhere in the methods of a class."""
    names = set()
    for statement in getattr(class_node, "body", ()):
        if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef)) and statement.args.args:
            receiver = statement.args.args[0].arg
            for node in ast.walk(statement):
                if isinstance(node, (ast.Assign, ast.AnnAssign, ast.AugAssign)):
                    targets = node.targets if isinstance(node, ast.Assign) else [node.target]
                    for target in targets:
                        for element in (target.elts if isinstance(target, (ast.Tuple, ast.List)) else [target]):
                            if isinstance(element, ast.Attribute) and isinstance(element.value, ast.Name) and element.value.id == receiver:
                                names.add(element.attr)
    return names


class Signature:
    """
    The parameters of a Python function as its AST declares them. The receiver parameter is excluded
    when the function is called bound: a method on an instance, a class method or a static method on
    either; a plain method called on the class takes its receiver as the first argument.
    """

    def __init__(self, node, bound):
        args = node.args
        positional_only = list(getattr(args, "posonlyargs", []))
        positional = positional_only + list(args.args)
        self.receiver = None
        if bound and positional and not any(_decorator_name(d) == "staticmethod" for d in node.decorator_list):
            self.receiver = positional.pop(0).arg
        self.positional = [argument.arg for argument in positional]
        self.positional_only = max(0, len(positional_only) - (1 if self.receiver is not None else 0))
        self.defaults = len(args.defaults)
        self.vararg = args.vararg is not None
        self.keyword_only = [argument.arg for argument in args.kwonlyargs]
        self.keyword_required = {argument.arg for argument, default in zip(args.kwonlyargs, args.kw_defaults) if default is None}
        self.kwarg = args.kwarg is not None
        self.annotations = {argument.arg: argument.annotation for argument in positional + list(args.kwonlyargs)}

    @classmethod
    def of_model(cls, function_def):
        """The signature of a function the model generated (a dataclass constructor), which has no AST node."""
        signature = cls.__new__(cls)
        arguments = list(function_def.arguments().arguments())
        signature.receiver = "self"
        signature.positional = [argument.name() for argument in arguments if not argument.variadic()]
        signature.positional_only = 0
        signature.defaults = sum(1 for argument in arguments if not argument.variadic() and argument.hasDefaultValue())
        signature.vararg = any(argument.variadic() for argument in arguments)
        signature.keyword_only = []
        signature.keyword_required = set()
        signature.kwarg = False
        signature.annotations = {}
        return signature

    @property
    def required(self):
        return len(self.positional) - self.defaults

    def accepts_keyword(self, name):
        """Whether a keyword argument of the name reaches a parameter of the name (not a positional-only one)."""
        return name in self.positional[self.positional_only:] or name in self.keyword_only

    def render(self):
        parts = list(self.positional)
        if self.positional_only:
            parts.insert(self.positional_only, "/")
        if self.vararg:
            parts.append("*args")
        elif self.keyword_only:
            parts.append("*")
        parts.extend(self.keyword_only)
        if self.kwarg:
            parts.append("**kwargs")
        return ", ".join(parts)


def _bound_on_class(node):
    """Whether a method called on its class is bound (a class method or a static method takes no explicit receiver)."""
    return any(_decorator_name(d) in ("staticmethod", "classmethod") for d in node.decorator_list)


def _accessor_targets(name):
    """The attribute names a Java-style accessor (getName, isName, setName) of a generated class reads or writes."""
    for prefix in ("get", "is", "set"):
        if name.startswith(prefix) and len(name) > len(prefix) and name[len(prefix)].isupper():
            rest = name[len(prefix):]
            camel = rest[0].lower() + rest[1:]
            snake = "".join("_" + c.lower() if c.isupper() else c for c in rest).lstrip("_")
            return {camel, snake}
    return set()


def _decorator_name(decorator):
    if isinstance(decorator, ast.Name):
        return decorator.id
    if isinstance(decorator, ast.Attribute):
        return decorator.attr
    if isinstance(decorator, ast.Call):
        return _decorator_name(decorator.func)
    return None


class PythonClassModel:
    """
    What the checker knows about a Python class of the compilation: its own members from the model
    and the AST, and its bases. A class is open when it or a base can have members the checker
    cannot see (a __getattr__, a base outside the compilation); nothing is reported on an open class.
    """

    def __init__(self, classes, class_def, node, module):
        self.classes = classes
        self.class_def = class_def
        self.node = node
        self.module = module
        self.name = class_def.name().replace("$", ".")  # a nested class, as Python spells it
        self.qualified = class_def.qualifiedName()
        self.methods = {}
        for function_def in class_def.functions():
            self.methods[function_def.name()] = (function_def, _function_node(node, function_def.name()))
        constructor = class_def.constructor()
        self.constructor = (constructor, _function_node(node, "__init__")) if constructor is not None else None
        self.properties = {prop.name(): prop for prop in class_def.properties()}
        self.attributes = {attribute.name(): attribute for attribute in class_def.attributes()}
        self.instance_attributes = _self_attributes(node)
        self.nested = {child.name: child for child in getattr(node, "body", ()) if isinstance(child, ast.ClassDef)}
        # a __getattr__ may add members; a __getattribute__ may replace even the declared ones; a
        # metaclass or a decorator the checker does not know may install members of its own
        self.dynamic = "__getattr__" in self.methods
        self.lookup_dynamic = "__getattribute__" in self.methods
        self.metaclass = any(kw.arg == "metaclass" for kw in getattr(node, "keywords", ()))
        self._bases = None
        self._subclasses = None

    @property
    def bases(self):
        """The resolved bases: PythonClassModel, a Java TypeDescription, or None for an unknown base."""
        if self._bases is None:
            self._bases = []
            for base in self.class_def.bases():
                name = base.name()
                if name in TRANSPARENT_BASES:
                    continue
                model = self.classes.by_qualified.get(name)
                if model is not None:
                    self._bases.append(model)
                    continue
                description = self.classes.describe_base(self.module, base) if "." in name else None
                self._bases.append(description)
        return self._bases

    def java_bases(self, seen=None):
        """The descriptions of the Java bases of the class, its own before those of its Python bases."""
        seen = seen or set()
        if self.qualified in seen:
            return []
        seen.add(self.qualified)
        found = []
        for base in self.bases:
            if isinstance(base, PythonClassModel):
                found.extend(base.java_bases(seen))
            elif base is not None:
                found.append(base)
        return found

    def is_lookup_dynamic(self, seen=None):
        """Whether a __getattribute__ of the class or a base decides every lookup: nothing about a member is known."""
        seen = seen or set()
        if self.qualified in seen:
            return False
        seen.add(self.qualified)
        return self.lookup_dynamic or any(isinstance(base, PythonClassModel) and base.is_lookup_dynamic(seen) for base in self.bases)

    def is_open(self, seen=None):
        seen = seen or set()
        if self.qualified in seen:
            return False
        seen.add(self.qualified)
        if self.dynamic or self.lookup_dynamic or self.metaclass or self._decorated_by_unknown():
            return True
        for base in self.bases:
            if base is None:
                return True
            if isinstance(base, PythonClassModel) and base.is_open(seen):
                return True
        return False

    def find(self, name, seen=None):
        """
        The member of the given name: ("method", function_def, node), ("constructor", ...),
        ("property", property_def), ("attribute", attribute_def), ("instance", None) or
        ("java", description) for a member of a Java base; None when the class has no such member.
        """
        seen = seen or set()
        if self.qualified in seen:
            return None
        seen.add(self.qualified)
        if self.is_lookup_dynamic():
            return None  # a __getattribute__ may hand out anything for the name
        if name in self.methods:
            return ("method",) + self.methods[name]
        if name == "__init__" and self.constructor is not None:
            return ("constructor",) + self.constructor
        if name in self.properties:
            return ("property", self.properties[name])
        if name in self.attributes:
            return ("attribute", self.attributes[name])
        if name in self.instance_attributes:
            return ("instance", None)
        if name in self.nested:
            return ("class", self.nested[name])
        if any(target in self.properties or target in self.attributes or target in self.instance_attributes for target in _accessor_targets(name)):
            return ("accessor", None)  # the value may be the generated Java object, with accessors
        for base in self.bases:
            if isinstance(base, PythonClassModel):
                found = base.find(name, seen)
                if found is not None:
                    return found
            elif base is not None:
                if base.methods().containsKey(name) or base.fields().containsKey(name) or base.nestedTypes().containsKey(name) or name in OBJECT_METHODS:
                    return ("java", base)
                if base.protectedMethods().contains(name):
                    return ("inherited", None)  # a protected method of the Java base: not judged further
        return None

    def constructor_of(self, seen=None):
        """
        The __init__ a construction of the class calls, as (function_def, node): its own or the
        nearest inherited one; None for the implicit no-argument constructor; UNKNOWN when the
        arguments go somewhere the checker cannot see (a __new__, a base outside the compilation,
        a Java base, or a decorator that may generate the constructor).
        """
        seen = seen or set()
        if self.qualified in seen:
            return None
        seen.add(self.qualified)
        if self.constructor is not None and not self.is_lookup_dynamic():
            return self.constructor
        if "__new__" in self.methods or self.is_open():
            return UNKNOWN
        for base in self.bases:
            if isinstance(base, PythonClassModel):
                found = base.constructor_of(seen)
                if found is not None:
                    return found
            elif base is not None:
                return UNKNOWN  # a Java base: its constructors take the arguments
        return None

    def _decorated_by_unknown(self):
        """Whether a decorator of the class is neither a Java annotation nor dataclass: it may generate an __init__."""
        facts = self.classes.checker.facts
        for decorator in self.class_def.decorators():
            name = decorator.annotationName()
            if name.rsplit(".", 1)[-1] == "dataclass":
                continue  # its constructor is in the model already
            annotation = facts.describeAnnotation(name) if "." in name else None
            if annotation is None or not annotation.annotation():
                return True
        return False

    @property
    def subclasses(self):
        """The classes of the compilation extending this one: a value of the class may be any of them."""
        if self._subclasses is None:
            self._subclasses = [model for model in self.classes.all() if model is not self and model.is_subclass_of(self)]
        return self._subclasses

    def subclass_defines(self, name):
        return any(subclass.find(name) is not None for subclass in self.subclasses)

    def member_names(self, seen=None):
        seen = seen or set()
        if self.qualified in seen:
            return []
        seen.add(self.qualified)
        names = list(self.methods) + list(self.properties) + list(self.attributes) + sorted(self.instance_attributes) + list(self.nested)
        for base in self.bases:
            if isinstance(base, PythonClassModel):
                names.extend(base.member_names(seen))
            elif base is not None:
                names.extend(base.methods().keySet())
                names.extend(base.fields().keySet())
        return [name for name in names if not name.startswith("_")]

    def is_subclass_of(self, other, seen=None):
        if self is other:
            return True
        seen = seen or set()
        if self.qualified in seen:
            return False
        seen.add(self.qualified)
        return any(isinstance(base, PythonClassModel) and base.is_subclass_of(other, seen) for base in self.bases)

    def instance_attribute_type(self, name, bindings):
        """
        The type of self.<name> from the assignments of __init__: a hinted parameter assigned
        directly, or a Java call or construction the checker types; None when the assignments
        disagree, or when another method assigns the attribute too.
        """
        if self.constructor is None or self.constructor[1] is None:
            return None
        function_def, node = self.constructor
        hints = {argument.name(): argument.typeAnnotation() for argument in function_def.arguments().arguments()}
        assigned = [statement for statement in node.body
                    if isinstance(statement, ast.Assign) and len(statement.targets) == 1
                    and isinstance(statement.targets[0], ast.Attribute) and isinstance(statement.targets[0].value, ast.Name)
                    and statement.targets[0].value.id == "self" and statement.targets[0].attr == name]
        if not assigned:
            return None
        found = None
        for statement in assigned:
            if isinstance(statement.value, ast.Name):
                hint = hints.get(statement.value.id)
                typed = bindings.of_hint(hint) if hint is not None else None
            elif isinstance(statement.value, ast.Call):
                rules = self.classes.checker.rules_of(self, function_def, node)
                typed = rules.node_types.get(id(statement.value)) if rules is not None else None
                if typed is not None and typed.kind != JAVA:
                    typed = None
            else:
                typed = None
            if typed is None or (found is not None and (found.kind, found.name) != (typed.kind, typed.name)):
                return None
            found = typed
        if self._assigned_outside_constructor(name):
            return None
        return found

    def _assigned_outside_constructor(self, name):
        """Whether a method other than __init__ assigns self.<name>: its type is then not the constructor's to say."""
        for statement in getattr(self.node, "body", ()):
            if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef)) and statement.name != "__init__" and statement.args.args:
                receiver = statement.args.args[0].arg
                for child in ast.walk(statement):
                    if isinstance(child, (ast.Assign, ast.AnnAssign, ast.AugAssign)):
                        targets = child.targets if isinstance(child, ast.Assign) else [child.target]
                        for target in targets:
                            if isinstance(target, ast.Attribute) and isinstance(target.value, ast.Name) and target.value.id == receiver and target.attr == name:
                                return True
        return False


# the constructor of a class whose arguments the checker cannot follow
UNKNOWN = object()


class PythonClasses:
    """The Python classes of the compilation, by qualified name and by ClassDef."""

    def __init__(self, checker):
        self.checker = checker
        self.by_qualified = {}
        self.by_def = {}
        self.by_node = {}
        self.models = []
        for module in checker._modules.values():
            for class_def, node in module.classes:
                model = PythonClassModel(self, class_def, node, module)
                self.models.append(model)
                self.by_qualified[model.qualified] = model
                self.by_def[id(class_def)] = model
                self.by_node[id(node)] = model
                # a class of the root package is also known by its bare name
                self.by_qualified.setdefault(model.name, model)

    def all(self):
        return self.models

    def describe_base(self, module, base):
        """
        The description of a Java base of a class of the module, with its type arguments substituted
        when every one of them is known (CrudRepository[Owner, int] is a CrudRepository<Owner, Integer>),
        else the raw type.
        """
        facts = self.checker.facts
        arguments = self.java_type_names(module, base.typeArguments())
        return facts.describe(base.name(), arguments) if arguments else facts.describe(base.name())

    def java_type_names(self, module, type_refs):
        """
        The qualified Java names the hints denote, boxed, as type arguments: a builtin, a class of
        the compilation (its generated class), or a Java class; None when any of them is unknown.
        """
        names = []
        for type_ref in type_refs or ():
            if type_ref is None:
                return None
            if type_ref.isUnion():
                members = list(type_ref.nonNoneMembers())
                if len(members) != 1:
                    return None
                type_ref = members[0]
            name = type_ref.name()
            if name in BOXED_JAVA_NAMES:
                names.append(BOXED_JAVA_NAMES[name])
                continue
            class_def = module.class_index().get(name)
            model = self.of(class_def) if class_def is not None else self.by_qualified.get(name)
            if model is not None:
                names.append(model.qualified)
                continue
            visitor = module.visitor
            qualified = (visitor.java_type_assignments.get(name) or visitor.imported_types.get(name) or name) if visitor is not None else name
            if "." not in qualified:
                return None
            description = self.checker.facts.describe(qualified)
            if description is None:
                return None
            names.append(description.name())
        return names

    def of(self, class_def):
        model = self.by_def.get(id(class_def))
        if model is None:
            model = self.by_qualified.get(class_def.qualifiedName())
        return model


class PythonReceiverMixin:
    """The checks of calls, attribute reads and constructions whose receiver is a Python class of the compilation."""

    def _python_model(self, receiver):
        return self.checker.python_classes.of(receiver.name)

    def _python_member(self, receiver, name, node, calling):
        model = self._python_model(receiver)
        if model is None:
            return None
        found = model.find(name)
        if found is None:
            if not model.is_open() and not name.startswith("_") and not model.subclass_defines(name):
                suggestions = suggest(name, model.member_names())
                self._report("unknown-python-member",
                             f"class [{model.name}] has no member [{name}]{_did_you_mean(suggestions)}",
                             node, suggestions)
            return None
        kind = found[0]
        if kind in ("method", "constructor", "accessor", "inherited"):
            return Typed(CALLABLE, name) if not calling else None
        if kind == "class":
            nested = self.checker.python_classes.by_node.get(id(found[1]))
            return Typed(PY_REF, nested.class_def) if nested is not None else None
        if kind == "property":
            getter = found[1].getter()
            return self._hinted_return(getter) if getter is not None else None
        if kind == "attribute":
            return self.bindings.of_hint(found[1].typeName())
        if kind == "instance":
            return model.instance_attribute_type(name, self.bindings)
        if kind == "java":
            description = found[1]
            # the base as the class extends it, with its type arguments
            return self._java_member(Typed(JAVA if receiver.kind == PY else JAVA_REF, description.name()), name, node, calling, description=description)
        return None

    def _hinted_return(self, function_def):
        return_def = function_def.returnType()
        type_ref = return_def.typeAnnotation() if return_def is not None else None
        return self.bindings.of_hint(type_ref) if type_ref is not None else None

    def _python_call(self, receiver, name, node, argument_types, star_args, keyword_types):
        model = self._python_model(receiver)
        if model is None:
            return None
        found = model.find(name)
        if found is None:
            self._python_member(receiver, name, node, calling=True)
            return None
        if found[0] == "java":
            # the base as the class extends it, with its type arguments
            return self._java_call(Typed(JAVA if receiver.kind == PY else JAVA_REF, found[1].name()), name, node, argument_types, star_args, bool(node.keywords), description=found[1])
        if found[0] == "class":
            nested = self.checker.python_classes.by_node.get(id(found[1]))
            return self._python_construction(Typed(PY_REF, nested.class_def), node, argument_types, star_args, keyword_types) if nested is not None else None
        if found[0] not in ("method", "constructor"):
            return None  # calling a property's, an attribute's or an accessor's value: not judged
        function_def, function_node = found[1], found[2]
        if function_node is not None and not star_args:
            # on an instance every method is bound; on the class only a class or static method is
            bound = receiver.kind == PY or _bound_on_class(function_node)
            self._check_python_arguments(model, function_def, Signature(function_node, bound), node, argument_types, keyword_types)
        adopted = self._adopted_return(model, name, function_def)
        if adopted is not None:
            return adopted
        hinted = self._hinted_return(function_def)
        if hinted is None and function_def.returnType() is not None and function_def.returnType().typeAnnotation() is None and function_def.hasReturnValue():
            # an unhinted method: the one type its body returns, when the checker infers one
            return self.checker.inferred_return(model, function_def, function_node)
        return hinted

    def _adopted_return(self, model, name, function_def):
        """
        The return type of the Java method a method of the class implements, which the generated
        class adopts (a Java return other than Object wins over the hint, as the stub declares it):
        findById -> Item | None on a CrudRepository[Item, UUID] is an Optional.
        """
        arity = sum(1 for argument in function_def.arguments().arguments() if argument.name() not in ("self", "cls"))
        for description in model.java_bases():
            if not description.methods().containsKey(name):
                continue
            signatures = [signature for signature in description.methods().get(name)
                          if len(signature.parameterTypes()) == arity and not signature.varargs()]
            if signatures:
                return self._return_type(signatures)
        return None

    def _python_construction(self, target, node, argument_types, star_args, keyword_types):
        model = self._python_model(target)
        if model is None or star_args:
            return Typed(PY, target.name)
        constructor = model.constructor_of()
        if constructor is None:
            arguments = len(argument_types) + len(node.keywords)
            if arguments and not any(kw.arg is None for kw in node.keywords):
                self._report("python-arity", f"[{model.name}] takes no arguments; got {arguments}", node)
        elif constructor is not UNKNOWN:
            function_def, function_node = constructor
            signature = Signature(function_node, True) if function_node is not None else Signature.of_model(function_def)
            self._check_python_arguments(model, function_def, signature, node, argument_types, keyword_types)
        return Typed(PY, target.name)

    def _check_python_arguments(self, model, function_def, signature, call, argument_types, keyword_types):
        label = f"{model.name}.{function_def.name()}"
        positional = len(argument_types)
        keywords = [(kw, typed) for kw, typed in zip(call.keywords, keyword_types) if kw.arg is not None]
        if any(kw.arg is None for kw in call.keywords):
            return  # **expansion: the argument layout is unknown
        if positional > len(signature.positional) and not signature.vararg:
            self._report("python-arity",
                         f"[{label}] takes {len(signature.positional)} positional arguments ({signature.render()}); got {positional}",
                         call.args[len(signature.positional)] if len(call.args) > len(signature.positional) else call)
            return
        provided = set(signature.positional[:positional])
        for kw, _ in keywords:
            if signature.accepts_keyword(kw.arg):
                if kw.arg in provided:
                    self._report("python-arity", f"[{label}] got multiple values for parameter [{kw.arg}]", kw)
                provided.add(kw.arg)
            elif signature.kwarg:
                continue  # collected by **kwargs, whatever its name
            elif kw.arg in signature.positional:
                self._report("python-arity", f"[{label}] parameter [{kw.arg}] is positional-only ({signature.render()})", kw)
                provided.add(kw.arg)  # reported once: not missing as well
            else:
                suggestions = suggest(kw.arg, signature.positional[signature.positional_only:] + signature.keyword_only)
                self._report("python-arity", f"[{label}] has no parameter [{kw.arg}]{_did_you_mean(suggestions)}", kw, suggestions)
        missing = [name for name in signature.positional[:signature.required] if name not in provided]
        missing += [name for name in signature.keyword_only if name in signature.keyword_required and name not in provided]
        if missing:
            self._report("python-arity", f"[{label}] is missing the arguments [{', '.join(missing)}]", call)
        # the argument types the checker knows against the hinted parameters
        hints = {argument.name(): argument.typeAnnotation() for argument in function_def.arguments().arguments()}
        for index, argument in enumerate(argument_types[:len(signature.positional)]):
            self._check_python_argument(label, signature.positional[index], hints, signature, argument, call.args[index])
        for kw, typed in keywords:
            if signature.accepts_keyword(kw.arg):
                self._check_python_argument(label, kw.arg, hints, signature, typed, kw.value)

    def _parameter_type(self, parameter, hints, signature):
        """The type a parameter's hint denotes: from the model, or from the AST for a keyword-only parameter the model omits."""
        hint = hints.get(parameter)
        if hint is not None:
            return self.bindings.of_hint(hint)
        annotation = signature.annotations.get(parameter)
        return self._hint_type(annotation) if annotation is not None else None

    def _check_python_argument(self, label, parameter, hints, signature, argument, node):
        if argument is None:
            return
        expected = self._parameter_type(parameter, hints, signature)
        if expected is None:
            return
        if not self._fits(argument, expected):
            self._report("argument-type",
                         f"argument [{parameter}] of [{label}] expects [{self._describe(expected)}]; got {self._describe(argument)}",
                         node)

    def _fits(self, argument, expected):
        if expected.kind == BUILTIN:
            if argument.kind == BUILTIN:
                return argument.name == expected.name or (argument.name == "int" and expected.name == "float") or argument.name == "none" or expected.name in ("list", "tuple") and argument.name in ("list", "tuple")
            if argument.kind == JAVA:
                converted = BUILTIN_JAVA_TYPES.get(argument.name)
                return converted is None or converted == expected.name or (converted == "int" and expected.name == "float")
            return True
        if expected.kind == JAVA:
            source = argument.java_name()
            return source is None or self.facts.isAssignable(source, expected.name)
        if expected.kind == PY:
            if argument.kind == PY:
                mine = self.checker.python_classes.of(argument.name)
                theirs = self.checker.python_classes.of(expected.name)
                return mine is None or theirs is None or mine.is_subclass_of(theirs)
            return argument.kind != BUILTIN or argument.name == "none"
        return True

    @staticmethod
    def _describe(typed):
        return typed.label()


# the Python receiver checks join the rules of the function bodies
for _name, _member in list(vars(PythonReceiverMixin).items()):
    if not _name.startswith("__"):
        setattr(JavaReceiverRules, _name, _member)
