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
