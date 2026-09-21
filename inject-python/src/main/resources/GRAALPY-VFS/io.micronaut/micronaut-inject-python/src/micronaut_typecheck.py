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
import java

PythonDiagnostic = java.type("io.micronaut.python.processing.diagnostic.PythonDiagnostic")

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
    A function whose body may be checked: the model of the function, the AST node its body is
    in, the class it belongs to (None for a module-level function) and the severity resolved for
    it (``warn``, ``error`` or None when it is not checked).
    """

    def __init__(self, source_path, qualified_name, function_def, node, class_def, severity):
        self.source_path = source_path
        self.qualified_name = qualified_name
        self.function_def = function_def
        self.node = node
        self.class_def = class_def
        self.severity = severity

    def __repr__(self):
        return f"CheckUnit({self.qualified_name}, {self.severity})"


class _ModuleRecord:
    def __init__(self, source_path):
        self.source_path = source_path
        self.decorators = []
        self.classes = []      # (class_def, node)
        self.functions = []    # (function_def, node)


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
                units.append(CheckUnit(module.source_path, function_def.name(), function_def, node, None, severity))
            for class_def, node in module.classes:
                class_severity = self.scope.severity(class_def.decorators(), module_severity)
                members = list(class_def.functions())
                if class_def.constructor() is not None:
                    members.append(class_def.constructor())
                for function_def in members:
                    severity = self.scope.severity(function_def.decorators(), class_severity)
                    qualified_name = f"{class_def.name()}.{function_def.name()}"
                    units.append(CheckUnit(module.source_path, qualified_name, function_def, _function_node(node, function_def.name()), class_def, severity))
        return units

    def check(self, visitor_context):
        """
        Run the checks over the checked functions and return the diagnostics found, as Java
        PythonDiagnostic values. No rule is implemented yet: the units are resolved so callers can
        already see what would be checked.
        """
        for unit in self.units():
            if unit.severity is None:
                continue
            # the rules of later changes run here
        return list(self.diagnostics)

    def report(self, unit, rule, message, span):
        """Record a finding of a rule at the severity of the unit it was found in."""
        factory = PythonDiagnostic.error if unit.severity == MODE_ERROR else PythonDiagnostic.warning
        self.diagnostics.append(factory(rule, message, span))


def _function_node(class_node, name):
    """The AST node of the method of the given name in a class body, or None."""
    if class_node is None:
        return None
    for statement in getattr(class_node, "body", ()):
        if getattr(statement, "name", None) == name and hasattr(statement, "args"):
            return statement
    return None
