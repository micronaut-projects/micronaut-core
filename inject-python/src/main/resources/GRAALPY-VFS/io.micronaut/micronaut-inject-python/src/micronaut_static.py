"""
Static compilation planning: which function bodies could be compiled to Java in the generated
stubs, and why the others cannot.

The planner runs once the type checker has modelled every source of the compilation
(:class:`micronaut_typecheck.TypeChecker`), over the same records, and produces one decision per
function of the compilation as Java ``StaticCompilationDecision`` values:

* ``EXCLUDED`` when the nearest ``CompileStatic`` declaration switches the function, its class or
  its module off (or the mode is ``off`` and nothing switches it on);
* ``NOT_CANDIDATE`` when the function can never be compiled: a module-level function, a function of
  a class that generates no class stub (an enum, an interface, a pooled class, a class with
  ``__slots__`` or ``__getattr__``), an ``async`` or generator function or a special method;
  property accessors are properties of the model, not functions, and get a decision only when a
  function-level ``CompileStatic`` switches one on, which is never honoured (``property-accessor``);
* ``SKIPPED`` with every reason the author can address: a signature without a fixed Java layout
  (``*args``, ``**kwargs``, keyword-only parameters, an unhinted or unresolvable parameter, a return
  hint that resolves to no type), a statement or expression kind that has no lowering. A missing
  return hint is no reason: the stub declares an ``Object`` return;
* ``COMPILED`` when every check passes and the body lowers to the IR the Java side generates code
  from (:mod:`micronaut_lowering`); a body the checker's inference flags, or that uses a construct
  without a lowering, is ``SKIPPED`` with those reasons.

An explicit ``@CompileStatic`` that cannot be honoured is reported as a warning (an error when the
planner is strict): a function-level switch warns whenever its function is not compiled, a class- or
module-level one for each method of its scope that is ``SKIPPED``, except in mode ``all``, where
skipping is the expected common case.
"""
import ast

import java

from micronaut_typecheck import (Bindings, CheckUnit, JavaReceiverRules, TypeFacts, PythonClasses, PythonClassModel, ScopeModes,
                                ScopeResolver, SCOPE_CLASS, SCOPE_FUNCTION, SCOPE_MODE, class_key, function_node, TRANSPARENT_BASES)
from micronaut_lowering import JAVA_RESERVED_NAMES, Lowering, stub_type_name, _decorated_with

PythonDiagnostic = java.type("io.micronaut.python.processing.diagnostic.PythonDiagnostic")
Decision = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision")
Outcome = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Outcome")
Scope = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Scope")
Reason = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Reason")
Stats = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Stats")
ScriptDef = java.type("io.micronaut.python.processing.model.ScriptDef")
MICRONAUT_TEST = "io.micronaut.test.extensions.junit5.annotation.MicronautTest"

MODE_OFF = "off"
# decides exactly as MODE_OFF does; the processor runs the planner for it over every compilation and always writes the report
MODE_ANNOTATED = "annotated"
MODE_ALL = "all"
MODES = (MODE_OFF, MODE_ANNOTATED, MODE_ALL)

# the rule of the diagnostics reporting an explicit switch that is not honoured
RULE = "compile-static"

# the statement kinds a lowering exists for; every other kind is unsupported-statement
SUPPORTED_STATEMENTS = (ast.Assign, ast.AnnAssign, ast.AugAssign, ast.If, ast.While, ast.For, ast.Return, ast.Raise,
                        ast.Try, ast.Expr, ast.Pass, ast.Break, ast.Continue, ast.Assert)
# the expression kinds a lowering exists for; every other kind is unsupported-expression
SUPPORTED_EXPRESSIONS = (ast.Constant, ast.Name, ast.Attribute, ast.Call, ast.BinOp, ast.UnaryOp, ast.BoolOp,
                         ast.Compare, ast.JoinedStr, ast.FormattedValue, ast.List, ast.Dict, ast.Set, ast.Tuple,
                         ast.Subscript, ast.IfExp, ast.keyword, ast.ListComp, ast.SetComp, ast.DictComp, ast.GeneratorExp)
# the reasons a class generates no class stub, by the decorator or base that says so
POOLED_DECORATOR = "ContextPooled"
PROTOCOL_BASES = {"Protocol", "typing.Protocol"}

# attributes fixed at construction (StaticPlanner.fixed_attribute): the members through which a class
# takes over the access to the attributes of its objects
ATTRIBUTE_PROTOCOL = {"__setattr__", "__delattr__", "__getattr__", "__getattribute__", "__new__", "__slots__",
                      "__dict__", "__class__", "__init_subclass__", "__class_getitem__", "__set_name__"}
# the Java types whose value a Python value converts to anew every time, equal each time: numbers,
# booleans, characters and strings
FIXED_SCALARS = {"long", "int", "short", "byte", "double", "float", "boolean", "char",
                 "java.lang.Long", "java.lang.Integer", "java.lang.Short", "java.lang.Byte", "java.lang.Double",
                 "java.lang.Float", "java.lang.Boolean", "java.lang.Character", "java.lang.String"}
# the types any Python value converts to, a container among them
LOOSE_TYPES = {"java.lang.Object", "java.lang.Number", "java.io.Serializable", "java.lang.Comparable", "java.lang.CharSequence"}
# the types of java.util whose values are immutable
IMMUTABLE_UTIL_TYPES = {"java.util.UUID", "java.util.Locale", "java.util.Currency"}
# the types a Python container converts to, by a copy of its elements
CONTAINER_TYPES = ("java.lang.Iterable", "java.util.Map", "java.util.Iterator", "java.util.Optional", "java.util.stream.BaseStream")
# the calls writing the attribute their string argument names: setattr(o, "x", v), delattr(o, "x"),
# object.__setattr__(o, "x", v), monkeypatch.setattr(o, "x", v), patch.object(o, "x"), patch("m.C.x"), patch.multiple(o, x=v)
NAMED_WRITERS = {"setattr", "delattr", "__setattr__", "__delattr__", "object", "multiple", "patch"}
# the attributes whose assignment changes what reading any attribute of an object gives: its class,
# the bases of a class, the members of the attribute protocol
PROTOCOL_CHANGES = ATTRIBUTE_PROTOCOL | {"__bases__", "__mro__"}
# the calls among them that write the attribute a computed name says when no string names it
COMPUTED_WRITERS = {"setattr", "delattr", "__setattr__", "__delattr__"}


# static compilation: a function is compiled under mode all or when switched on; mode annotated
# decides as mode off does
COMPILE_MODES = ScopeModes(
    "static compilation", MODES,
    default=lambda mode: mode == MODE_ALL,
    on=lambda mode: True,
    off=False)


def CompileScope(mode, annotation_names):
    """
    The :class:`micronaut_typecheck.ScopeResolver` of static compilation: resolves whether a
    definition is compiled and which declaration decided, the nearest ``CompileStatic`` switch, else
    the compilation's mode.
    """
    return ScopeResolver(mode, annotation_names, COMPILE_MODES)


class StaticPlanner:
    """
    Classifies every function of a compilation, see the module documentation.
    """

    def __init__(self, mode, annotation_names, strict=False):
        self.scope = CompileScope(mode, annotation_names)
        self.strict = strict
        self.decisions = []
        self.bodies = []
        self.scripts = []         # the ScriptDefs of the modules that generate a class for their compiled functions only
        self.diagnostics = []
        self._ineligibility = {}  # id(class_def) -> why the class generates no stub, or None: asked once per class
        self._decided = {}        # id(node) -> the decision, or None while it is being made
        self._bodies = {}         # id(node) -> the compiled body
        self._class_scopes = {}   # id(module) -> the resolution of each of its classes
        # the classes already reported for a class-level switch they can never honour
        self._reported_classes = set()
        self._fixed = {}          # id(class model) -> the names of its attributes fixed at construction
        self._writes = None       # the attribute writes of the compilation, see AttributeWrites

    def plan(self, checker, visitor_context=None):
        """
        Decide every function recorded by the type checker; returns the decisions. The checker's
        facts are created from the visitor context when its own check did not run. Every plan starts
        afresh: the decisions and diagnostics of an earlier plan are dropped, so a planner that
        outlives one compilation element never reports a decision twice.
        """
        self.decisions = []
        self.diagnostics = []
        self._decided = {}
        self._bodies = {}
        self._class_scopes = {}
        self._reported_classes = set()
        self._fixed = {}
        self._writes = None
        if getattr(checker, "facts", None) is None and visitor_context is not None:
            checker.facts = TypeFacts(visitor_context)
            checker.python_classes = PythonClasses(checker)
        self.checker = checker
        for module in checker._modules.values():
            for function_def, node in module.functions:
                self._decide(module, None, None, function_def, node)
            for class_def, class_node in module.classes:
                members = [(function_def, None) for function_def in class_def.functions()]
                if class_def.constructor() is not None:
                    members.append((class_def.constructor(), None))
                members.extend(self._switched_accessors(class_def))
                members.sort(key=lambda member: member[0].span().line() if member[0].span() is not None else 0)
                for function_def, accessor in members:
                    if accessor is None:
                        self._decide(module, class_def, class_node, function_def, function_node(class_node, function_def.name()))
                    else:
                        self._decide_accessor(class_def, function_def, accessor)
        return list(self.decisions)

    def module_function(self, module, name):
        """The (function_def, node) of a top-level function of the module, or None."""
        for function_def, node in module.functions:
            if function_def.name() == name:
                return function_def, node
        return None

    def decide_function(self, module, function_def, node):
        """
        The decision of a module-level function a compiled body calls, made now when the planner has
        not reached it yet; None while the function's own decision is under way (a cycle of calls).
        """
        if id(node) in self._decided:
            return self._decided[id(node)]
        return self._decide(module, None, None, function_def, node)

    def decide_method(self, module, class_def, function_def, node):
        """
        The decision of a method of the class a compiled body calls, made now when the planner has
        not reached it yet; None while the method's own decision is under way (a cycle of calls).
        """
        if id(node) in self._decided:
            return self._decided[id(node)]
        class_node = next((candidate for defined, candidate in module.classes if defined is class_def), None)
        return self._decide(module, class_def, class_node, function_def, node)

    def body_of(self, node):
        """The compiled body of a decided function, or None."""
        return self._bodies.get(id(node))

    # ---------------------------------------------------------------- attributes fixed at construction

    def fixed_attribute(self, class_model, name, type_name):
        """
        Whether a compiled body of the class reads ``self.<name>`` from a Java field of its stub
        instead of the Python object (``Ir.SelfProperty#fixed``): the attribute is fixed at
        construction. ``__init__`` assigns it; no method of the class writes an attribute of
        ``self`` after construction; no code of the compilation assigns the name on another object
        (which may be the class itself) or names it in a call writing attributes by name, nor reaches
        attributes behind the attribute protocol (``__dict__``, ``vars()``, a computed
        ``object.__setattr__``); the class takes over no part of the protocol, so ``self.<name>`` is
        the entry of the object's ``__dict__``; and the value converts to the same Java value every
        time (a number, a string, a Java object, a plain object of the compilation, which its
        generated class wraps). The stub reads the attribute through the Python object once per
        object; ``micronaut_runtime`` then makes the class's ``__setattr__`` and ``__delattr__``
        report every later change of an attribute an object has, so a write the compilation does not
        see (a test's ``monkeypatch.setattr``, code outside the compilation, a method of another
        class called with the object) makes the stub read it again. See ``PythonFixedAttributes``.
        """
        if class_model is None or not self._fixable_type(type_name):
            return False
        key = id(class_model)
        if key not in self._fixed:
            self._fixed[key] = self._fixed_attributes(class_model)
        return name in self._fixed[key]

    def _fixed_attributes(self, model):
        """The names of the attributes of the class fixed at construction, see fixed_attribute."""
        if not self._fixable_class(model):
            return frozenset()
        writes = self._attribute_writes()
        if writes.everything or id(model.node) in writes.classes_written:
            return frozenset()
        names = set()
        for name in model.instance_attributes:
            if name.startswith("__") or name in writes.named or name in writes.stores:
                # a dunder or a name-mangled private attribute; one a call writes by its name, or one
                # assigned on an object other than the receiver of a method (an object of the class,
                # or the class itself)
                continue
            if model.find(name) != ("instance", None):
                continue  # the class or a base declares the name: a descriptor may serve it
            names.add(name)
        return frozenset(names)

    def _fixable_class(self, model):
        """
        Whether the objects of the class keep their attributes in their ``__dict__`` and write them
        through ``object.__setattr__`` only while ``__init__`` runs: no base but ``object`` (or ``ABC``,
        ``Generic``), no subclass of the compilation, no metaclass or decorator of its own, no part
        of the attribute protocol, a constructor of its own, and no method other than ``__init__``
        (nor a function ``__init__`` defines) writing an attribute of ``self``.
        """
        if model.constructor is None or model.constructor[1] is None or not model.constructor[1].args.args:
            return False
        if any(base.name() not in TRANSPARENT_BASES for base in model.class_def.bases()) or model.bases:
            return False
        if model.is_open() or model.is_lookup_dynamic() or model.subclasses:
            return False
        if any(decorator.annotationName().rsplit(".", 1)[-1] == "dataclass" for decorator in model.class_def.decorators()):
            return False
        if self._introspected(model.qualified) or model.is_throwable():
            return False
        node = model.node
        for statement in getattr(node, "body", ()):
            for target in _declared_names(statement):
                if target in ATTRIBUTE_PROTOCOL:
                    return False
        for statement in getattr(node, "body", ()):
            if not isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef)) or not statement.args.args:
                continue
            receiver = statement.args.args[0].arg
            if statement.name == "__init__":
                # a function __init__ defines runs later: its writes of self are no construction
                nested = [child for child in ast.walk(statement) if child is not statement and isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda))]
                if any(_receiver_store(grandchild, receiver) for child in nested for grandchild in ast.walk(child)):
                    return False
                continue
            if any(_receiver_store(child, receiver) for child in ast.walk(statement)):
                return False
        return True

    def _fixable_type(self, type_name):
        """
        Whether a value of the Java type a compiled body reads converts to the same Java value every
        time the Python value is the same object: a number, a boolean, a string, a Java object (the
        boundary hands it over as itself), a plain object of the compilation (its generated class
        wraps the Python object). A collection is copied, and an introspected object of the
        compilation is a copy of its state, which changes in place.
        """
        if type_name in FIXED_SCALARS:
            return True
        erased = type_name.split("<", 1)[0]
        if erased.endswith("[]") or erased in LOOSE_TYPES or "." not in erased:
            return False
        if erased.startswith("java.util.") and erased not in IMMUTABLE_UTIL_TYPES:
            return False
        facts = getattr(self.checker, "facts", None)
        if facts is None:
            return False
        if any(facts.isAssignable(erased, container) for container in CONTAINER_TYPES):
            return False
        classes = getattr(self.checker, "python_classes", None)
        model = classes.by_qualified.get(erased) if classes is not None else None
        if model is not None:
            return not model.is_throwable() and not any(self._introspected(other.qualified) for other in [model] + list(model.subclasses))
        return True

    def _introspected(self, qualified_name):
        """Whether the generated class of the class is introspected; unknown facts count as introspected."""
        facts = getattr(self.checker, "facts", None)
        introspected = getattr(facts, "isIntrospected", None) if facts is not None else None
        return True if introspected is None else bool(introspected(qualified_name))

    def _attribute_writes(self):
        if self._writes is None:
            trees = []
            for module in self.checker._modules.values():
                tree = getattr(getattr(module, "visitor", None), "module_node", None)
                trees.append(tree)
            self._writes = AttributeWrites(trees)
        return self._writes

    def script_class_name(self, module):
        """The name of the module's generated class: the script's when it has one, else the class its compiled functions get."""
        script = getattr(module, "script", None)
        if script is not None:
            return script.qualifiedName()
        visitor = module.visitor
        script_def = ScriptDef(visitor.script_name, visitor.package_name, [], [], None, [])
        simple = script_def.javaSimpleName()
        return f"{visitor.package_name}.{simple}" if visitor.package_name else simple

    # ---------------------------------------------------------------- one function

    def _decide(self, module, class_def, class_node, function_def, node):
        if node is not None:
            if id(node) in self._decided:
                return self._decided[id(node)]
            self._decided[id(node)] = None
        decision = self._decision(module, class_def, class_node, function_def, node)
        if node is not None:
            self._decided[id(node)] = decision
        return decision

    def _decision(self, module, class_def, class_node, function_def, node):
        qualified = f"{class_def.name()}.{function_def.name()}" if class_def is not None else function_def.name()
        # a method is declared within its class, which is declared within the class around it or the module
        enclosing = self._class_scope(module, class_def, class_node) if class_def is not None else self.scope.module(module)
        compiled, scope = self.scope.within(function_def.decorators(), SCOPE_FUNCTION, enclosing)
        span = function_def.span()
        if not compiled:
            return self._record(qualified, span, "EXCLUDED", scope, [], 0)
        bindings = None
        if getattr(self.checker, "facts", None) is not None and node is not None:
            unit = CheckUnit(module.source_path, function_def.name(), function_def, node, class_def, None, module)
            bindings = Bindings(self.checker, unit)
        java_layout = self._java_layout(module, class_def, function_def, node, bindings) if class_def is not None else None
        reasons = self._candidate_reasons(module, class_def, class_node, function_def, node, span, java_layout)
        if reasons:
            # a class-level switch on a class that can never be compiled is flagged once for the class
            ineligible_class = scope == SCOPE_CLASS and reasons[0][0] == "class-not-eligible" and self._first_report(class_def)
            return self._record(qualified, span, "NOT_CANDIDATE", scope, reasons, 0, explicit=scope == SCOPE_FUNCTION or ineligible_class)
        reasons = self._signature_reasons(module, class_def, function_def, node, span, java_layout, bindings)
        statements = 0
        if node is not None:
            statements = len(node.body)
            reasons.extend(self._body_reasons(module, node))
        if reasons or node is None or getattr(self.checker, "facts", None) is None:
            outcome = "SKIPPED" if reasons else "CANDIDATE"
            return self._record(qualified, span, outcome, scope, reasons, statements, explicit=scope != SCOPE_MODE)
        body, reasons = self._lower(module, class_def, function_def, node, java_layout)
        if body is None:
            return self._record(qualified, span, "SKIPPED", scope, reasons, statements, explicit=scope != SCOPE_MODE)
        if class_def is None and body.stats().bridgeCalls() > 0 and self._pooled_module(module):
            # a module served by a context pool: a body of its generated class runs outside any
            # Python context, so it cannot reach a Python object
            reason = ("pooled-module", "the module is served by a context pool; a body reaching a Python object has no context to reach it in", span)
            return self._record(qualified, span, "SKIPPED", scope, [reason], statements, explicit=scope != SCOPE_MODE)
        if class_def is None and body.staticMethod() and not _generates_class(getattr(module, "script", None)):
            # a module without a generated class gets one for its compiled functions
            self._add_script(module)
        self.bodies.append(body)
        if node is not None:
            self._bodies[id(node)] = body
        return self._record(qualified, span, "COMPILED", scope, [], statements, stats=body.stats())

    def _class_scope(self, module, class_def, class_node):
        """The resolution of a class of the module, the classes around it included: resolved once per module and plan."""
        resolutions = self._class_scopes.get(id(module))
        if resolutions is None:
            resolutions = self._class_scopes[id(module)] = self.scope.classes(module)
        resolved = resolutions.get(class_key(class_def, class_node))
        # a class the module does not record is declared within the module only
        return resolved if resolved is not None else self.scope.within(class_def.decorators(), SCOPE_CLASS, self.scope.module(module))

    def _add_script(self, module):
        visitor = module.visitor
        if visitor is None:
            return
        if any(script.name() == visitor.script_name and script.packageName() == visitor.package_name for script in self.scripts):
            return
        self.scripts.append(ScriptDef(visitor.script_name, visitor.package_name, [], [], None, []).withSpan(visitor._module_span()))

    def _lower(self, module, class_def, function_def, node, java_layout=None):
        """The compiled body of a candidate, or None with the reasons: what the inference flags, then what the lowering refuses."""
        # the type checker's own inference of the function when it ran, else a silent run of it
        rules = getattr(self.checker, "inference", {}).get(id(node))
        if rules is None:
            unit = CheckUnit(module.source_path, f"{class_def.name()}.{function_def.name()}" if class_def is not None else function_def.name(), function_def, node, class_def, None, module)
            rules = JavaReceiverRules(self.checker, unit, silent=True)
            rules.check()
        if rules.problems:
            return None, list(rules.problems)
        class_model = self.checker.python_classes.of(class_def) if class_def is not None else None
        # a plain module-level function and a static method compile into static Java methods
        static = function_def.isStatic() if class_def is not None else not self._bridged(function_def)
        lowering = Lowering(self.checker, module, class_def, function_def, node, rules, class_model,
                            advised=lambda sibling: self._advice(class_def, sibling) is not None,
                            advised_method=class_def is not None and self._advice(class_def, function_def) is not None,
                            java_layout=java_layout, planner=self, static=static,
                            compiled=lambda sibling, sibling_node: self._compiled_method(module, class_def, sibling, sibling_node))
        body = lowering.lower()
        return body, lowering.reasons

    def _compiled_method(self, module, class_def, function_def, node):
        """
        Whether a method of the class is compiled, decided now when the planner has not reached it
        yet; a method whose decision is under way (a cycle of calls) counts as not compiled, which
        only costs the call its Java dispatch.
        """
        decision = self.decide_method(module, class_def, function_def, node) if node is not None else None
        return decision is not None and decision.outcome().name() == "COMPILED" and self.body_of(node) is not None

    def _switched_accessors(self, class_def):
        """The property accessors a function-level switch turns on, as (accessor, kind) members."""
        accessors = []
        for prop in class_def.properties():
            for kind, accessor in (("getter", prop.getter()), ("setter", prop.setter()), ("deleter", prop.deleter())):
                if accessor is not None and self.scope.switch(accessor.decorators()):
                    accessors.append((accessor, kind))
        return accessors

    def _decide_accessor(self, class_def, function_def, kind):
        """A property accessor switched on explicitly: never compiled, and the switch is reported."""
        qualified = f"{class_def.name()}.{function_def.name()}" + ("" if kind == "getter" else f".{kind}")
        span = function_def.span()
        reasons = [("property-accessor", "a property accessor is called through the Python object's attribute protocol", span)]
        return self._record(qualified, span, "NOT_CANDIDATE", "FUNCTION", reasons, 0, explicit=True)

    def _first_report(self, class_def):
        """Whether the class is reported for the first time in this plan."""
        name = class_def.qualifiedName()
        if name in self._reported_classes:
            return False
        self._reported_classes.add(name)
        return True

    def _record(self, qualified, span, outcome, scope, reasons, statements, explicit=False, stats=None):
        java_reasons = [Reason(rule, message, reason_span) for rule, message, reason_span in reasons]
        decision = Decision(qualified, span, Outcome.valueOf(outcome), Scope.valueOf(scope), java_reasons, stats or Stats(statements, 0, 0, 0))
        self.decisions.append(decision)
        if explicit and reasons and (self.scope.mode != MODE_ALL or scope == SCOPE_FUNCTION):
            rule, message, reason_span = reasons[0]
            rest = f" (and {len(reasons) - 1} more, see the report)" if len(reasons) > 1 else ""
            text = f"[{qualified}] cannot be compiled statically: [{rule}] {message}{rest}"
            factory = PythonDiagnostic.error if self.strict else PythonDiagnostic.warning
            self.diagnostics.append(factory(RULE, text, reason_span or span))
        return decision

    # ---------------------------------------------------------------- the checks

    def _candidate_reasons(self, module, class_def, class_node, function_def, node, span, java_layout=None):
        """Why the function can never be compiled, whatever its body."""
        reasons = []
        if class_def is None:
            # a module-level function is a method of the module's generated class when a decorator
            # makes the stub generator bridge it (a route, an executable, an advised or scoped
            # function), else a static method of it
            if any(decorator.annotationName() == MICRONAUT_TEST for decorator in module.decorators):
                reasons.append(("class-not-eligible", "the functions of a test module are its tests", span))
                return reasons
            if sum(1 for other, _ in module.functions if other.name() == function_def.name()) > 1:
                # the generated class holds one static method of the name, and the Python callers delegating by it could reach the other body
                reasons.append(("unsupported-statement", f"the module defines [{function_def.name()}] more than once; only one static Java method of the name can be generated", span))
                return reasons
            advice = self._advice(None, function_def, module.decorators)
            if advice is not None:
                # the generated class of a module binds no compiled advice: a compiled body would run without its chain
                reasons.append(("intercepted-method", f"the function is advised by [{advice}]; the generated class of a module does not run the interceptor chain of a compiled body; not compiled yet", span))
        else:
            if id(class_def) not in self._ineligibility:
                self._ineligibility[id(class_def)] = self._class_ineligibility(class_def, class_node)
            ineligible = self._ineligibility[id(class_def)]
            if ineligible is not None:
                reasons.append(("class-not-eligible", ineligible, class_def.span() or span))
                return reasons
        name = function_def.name()
        if function_def.isStatic() and (node is None or _decorated_with(node, ("classmethod",))):
            reasons.append(("static-method", "a class method takes the class; it is bridged as a static Java method, not compiled", span))
        if function_def.isAsync():
            reasons.append(("async-function", "an async function runs on the Python event loop", span))
        if function_def.isGenerator() or (node is not None and _yields(node)):
            reasons.append(("generator-function", "a generator keeps its frame alive between calls", span))
        if name.startswith("__") and name.endswith("__"):
            what = "the constructor runs in Python so the instance keeps its Python semantics" if name == "__init__" else f"the special method {name} is called by the Python runtime"
            reasons.append(("special-method", what, span))
        if function_def.isAbstract() or function_def.hasPlaceholderBody():
            reasons.append(("abstract-method", "an abstract method has no body to compile; a call of it runs the implementation of the object", span))
        implemented = self._java_method_implemented(class_def, name) if class_def is not None else None
        if implemented is not None and java_layout is None:
            reasons.append(("overriding-java-method", f"the method implements [{implemented}], whose bridge keeps the Java signature; the hints must spell that signature (a parameter hinted with its Java type, a return hinted with a type the Java method returns or left unhinted)", span))
        overriding = self._overridden_without_java_override(class_def, function_def) if class_def is not None else None
        if overriding is not None:
            reasons.append(("overridden-method", f"[{overriding}] overrides the method with a signature its generated class cannot declare in Java; the bridge of this class dispatches to the Python object for both", span))
        advice = self._advice(class_def, function_def)
        if advice is not None and class_def is not None and self._introduced(class_def, function_def):
            reasons.append(("intercepted-method", f"the method is advised by [{advice}] of an introduction; the introduction proxy runs its chain on the Python object; not compiled yet", span))
        return reasons

    def _overridden_without_java_override(self, class_def, function_def):
        """
        The subclass of the compilation overriding the method with a signature the subclass's generated
        class cannot declare as a Java override (another return hint, or another parameter list), or None.
        A compiled body in this class would then serve the subclass's instances too, where the bridge,
        dispatching on the Python object, runs the override.
        """
        classes = getattr(self.checker, "python_classes", None)
        model = classes.of(class_def) if classes is not None else None
        if model is None:
            return None
        name = function_def.name()
        own_node = model.methods[name][1] if name in model.methods else None
        own = self._signature_shape(model, function_def, own_node)
        for subclass in model.subclasses:
            found = subclass.find(name)
            if found is None or found[0] != "method" or found[1] is function_def:
                continue
            if self._signature_shape(subclass, found[1], found[2]) != own:
                return subclass.name
        return None

    def _signature_shape(self, model, function_def, node):
        """
        What a Java override must repeat of the method the generated class of the class declares: the
        types the parameter hints and the return hint denote, as the stub declares them, and which
        parameters take the remaining arguments; not the parameter names, nor how a hint spells its
        type (List[int] and list[int] are one type). A hint that denotes no type the checker knows
        counts as spelled, which can only keep a base method dynamic.
        """
        bindings = None
        if getattr(self.checker, "facts", None) is not None and node is not None:
            unit = CheckUnit(model.module.source_path, function_def.name(), function_def, node, model.class_def, None, model.module)
            bindings = Bindings(self.checker, unit)

        def denoted(hint):
            if hint is None:
                return None
            if hint.name() == "None":
                return ("void",)
            typed = bindings.of_hint(hint) if bindings is not None else None
            return _typed_shape(typed) if typed is not None else ("hint", hint.name())

        return_def = function_def.returnType()
        parameters = tuple(
            (denoted(argument.typeAnnotation()), bool(argument.variadic()))
            for argument in function_def.arguments().arguments() if argument.name() not in ("self", "cls")
        )
        return (denoted(return_def.typeAnnotation() if return_def is not None else None), parameters)

    def _introduced(self, class_def, function_def):
        """Whether the method or its class carries an introduction binding."""
        facts = getattr(self.checker, "facts", None)
        if facts is None:
            return False
        for decorator in list(function_def.decorators()) + list(class_def.decorators()):
            description = self._annotation(facts, decorator)
            if description is not None and description.introduction():
                return True
        return False

    def _advice(self, class_def, function_def, module_decorators=()):
        """
        The annotation advising the method, or None: an around or introduction binding on the method,
        its class or, for a module-level function, its module, or a validation constraint on a
        parameter or the return, which validates the call. The Java method of an advised method runs the interceptor chain its proxy binds before
        the body; a Python caller reaches the chain on the Python object, which runs the body through
        the delegate.
        """
        facts = getattr(self.checker, "facts", None)
        if facts is None:
            return None
        enclosing = list(class_def.decorators()) if class_def is not None else list(module_decorators or ())
        for decorator in list(function_def.decorators()) + enclosing:
            description = self._annotation(facts, decorator)
            if description is not None and description.interceptorBinding():
                return decorator.annotationName().rsplit(".", 1)[-1]
        constrained = list(function_def.arguments().arguments())
        decorators = [d for argument in constrained for d in argument.decorators()]
        if function_def.returnType() is not None:
            decorators.extend(function_def.returnType().decorators())
        for decorator in decorators:
            description = self._annotation(facts, decorator)
            if description is not None and description.validationConstraint():
                return decorator.annotationName().rsplit(".", 1)[-1]
        return None

    def _pooled_module(self, module):
        """
        Whether the generated class of the module is served by a context pool, as the script element
        decides: unless a module-level annotation declares a scope (``@ContextPooled`` itself being
        one that pools). A module-level annotation that is no scope (``@Controller``, ``@Requires``)
        keeps the module pooled; an annotation without facts counts as no scope, so the planner errs
        on the side of the refusal.
        """
        facts = getattr(self.checker, "facts", None)
        for decorator in module.decorators or ():
            if decorator.annotationName().rsplit(".", 1)[-1] == POOLED_DECORATOR:
                return True
            description = self._annotation(facts, decorator) if facts is not None else None
            if description is not None and description.scope():
                return False
        return True

    def _bridged(self, function_def):
        """Whether the stub generator gives a module-level function a Java method: a decorator of an executable kind."""
        facts = getattr(self.checker, "facts", None)
        decorators = list(function_def.decorators())
        if facts is None or not hasattr(facts, "describeAnnotation"):
            return bool(decorators)
        descriptions = [self._annotation(facts, decorator) for decorator in decorators]
        if all(description is None for description in descriptions):
            return bool(decorators)  # a compilation modelled without Java facts
        return any(description is not None and description.executable() for description in descriptions)

    @staticmethod
    def _annotation(facts, decorator):
        name = decorator.annotationName()
        return facts.describeAnnotation(name) if "." in name else None

    def _java_layout(self, module, class_def, function_def, node, bindings=None):
        """
        The (parameter types, return type) of the Java method the function implements, when the
        hints spell it: each parameter hinted with the Java parameter type (or the Java parameter
        is an Object), the return unhinted or hinted with a type the Java method returns. None
        when the function implements no Java method, or the hints do not fit it.
        """
        facts = getattr(self.checker, "facts", None)
        if facts is None or node is None:
            return None
        parameters = [argument for argument in function_def.arguments().arguments() if argument.name() not in ("self", "cls")]
        layouts = self._java_signatures(class_def, function_def.name(), len(parameters))
        if not layouts:
            return None
        if bindings is None:
            unit = CheckUnit(module.source_path, function_def.name(), function_def, node, class_def, None, module)
            bindings = Bindings(self.checker, unit)
        hinted = []
        for argument in parameters:
            hint = argument.typeAnnotation()
            hinted.append(stub_type_name(bindings.of_hint(hint)) if hint is not None else None)
        spelled = [layout for layout in layouts
                   if all(hint == java_type or java_type == "java.lang.Object" for hint, java_type in zip(hinted, layout[0]))]
        if len(spelled) != 1:
            return None
        # the stub bridges every overload of the arity to the one Python function: another abstract
        # overload would hand the compiled body values of another type, a default one is the
        # interface's own entry to the spelled method (MethodInterceptor.intercept)
        if any(not layouts[layout] for layout in layouts if layout != spelled[0]):
            return None
        parameter_types, return_type = spelled[0]
        return_def = function_def.returnType()
        hint = return_def.typeAnnotation() if return_def is not None else None
        if hint is not None and hint.name() not in ("object", "Any", "typing.Any"):
            hinted = stub_type_name(bindings.of_hint(hint))
            if hint.name() == "None":
                hinted = "void"
            if hinted != return_type and return_type != "java.lang.Object" and not (hinted is not None and return_type != "void" and facts.isAssignable(hinted, return_type)):
                return None
        return list(parameter_types), return_type

    def _java_signatures(self, class_def, name, arity):
        """
        The Java methods of the name and arity the bases of the class declare: a dict of
        (parameter types, return type) to whether every declaration of that signature is a default method.
        """
        classes = getattr(self.checker, "python_classes", None)
        model = classes.of(class_def) if classes is not None else None
        seen = set()
        stack = [model] if model is not None else []
        found = {}
        while stack:
            current = stack.pop()
            if current is None or current.qualified in seen:
                continue
            seen.add(current.qualified)
            for base in current.bases:
                if base is None:
                    continue
                if isinstance(base, PythonClassModel):
                    stack.append(base)
                elif base.methods().containsKey(name):
                    for signature in base.methods().get(name):
                        if len(signature.parameterTypes()) == arity and not signature.varargs():
                            layout = (tuple(signature.parameterTypes()), signature.returnType())
                            found[layout] = found.get(layout, True) and signature.isDefault()
        return found

    def _java_method_implemented(self, class_def, name):
        """The Java base or interface declaring a method of the name the class implements, or None."""
        classes = getattr(self.checker, "python_classes", None)
        model = classes.of(class_def) if classes is not None else None
        seen = set()
        stack = [model] if model is not None else []
        while stack:
            current = stack.pop()
            if current is None or current.qualified in seen:
                continue
            seen.add(current.qualified)
            for base in current.bases:
                if base is None:
                    continue
                if isinstance(base, PythonClassModel):
                    stack.append(base)
                elif base.methods().containsKey(name):
                    return f"{base.name()}.{name}"
        return None

    def _class_ineligibility(self, class_def, class_node):
        """Why the class generates no class stub a compiled body could live in, or None."""
        if class_def.isEnum():
            return f"[{class_def.name()}] is an enum"
        for base in class_def.bases():
            if base.name() in PROTOCOL_BASES:
                return f"[{class_def.name()}] is a protocol"
        for decorator in class_def.decorators():
            if decorator.annotationName().rsplit(".", 1)[-1] == POOLED_DECORATOR:
                return f"[{class_def.name()}] is served by a context pool"
        for function_def in class_def.functions():
            if function_def.name() in ("__getattr__", "__getattribute__"):
                return f"[{class_def.name()}] defines {function_def.name()}"
        for statement in getattr(class_node, "body", ()):
            targets = statement.targets if isinstance(statement, ast.Assign) else [statement.target] if isinstance(statement, ast.AnnAssign) else []
            if any(isinstance(target, ast.Name) and target.id == "__slots__" for target in targets):
                return f"[{class_def.name()}] declares __slots__"
        facts = getattr(self.checker, "facts", None)
        if facts is not None:
            # the one fact needed of the class, asked without describing its members
            is_interface = facts.isInterface(class_def.qualifiedName()) if hasattr(facts, "isInterface") else \
                (facts.describe(class_def.qualifiedName()) is not None and facts.describe(class_def.qualifiedName()).anInterface())
            if is_interface:
                return f"[{class_def.name()}] compiles to an interface"
        return None

    def _signature_reasons(self, module, class_def, function_def, node, span, java_layout=None, bindings=None):
        """Why the signature has no fixed Java layout, and which hints resolve to no type."""
        reasons = []
        if function_def.name() in JAVA_RESERVED_NAMES:
            reasons.append(("java-reserved-name", f"method name [{function_def.name()}] cannot be declared in Java", span))
        if bindings is None and getattr(self.checker, "facts", None) is not None and node is not None:
            unit = CheckUnit(module.source_path, function_def.name(), function_def, node, class_def, None, module)
            bindings = Bindings(self.checker, unit)
        for argument in function_def.arguments().arguments():
            argument_span = argument.span() or span
            if argument.name() in JAVA_RESERVED_NAMES:
                reasons.append(("java-reserved-name", f"parameter [{argument.name()}] cannot be declared in Java", argument_span))
            if argument.variadic():
                if node is None:
                    reasons.append(("varargs-signature", f"parameter [{argument.name()}] collects a variable number of arguments", argument_span))
            elif argument.typeAnnotation() is None:
                reasons.append(("unhinted-parameter", f"parameter [{argument.name()}] has no type hint", argument_span))
            elif bindings is not None and argument.name() in bindings.unknown_locals:
                reasons.append(("unhinted-parameter", f"the hint of parameter [{argument.name()}] resolves to no Java type or class of the compilation", argument_span))
        if node is not None:
            # the AST is complete where the model keeps one variadic parameter at most
            if node.args.vararg is not None:
                reasons.append(("varargs-signature", f"parameter [*{node.args.vararg.arg}] collects the positional arguments", module.span_of(node.args.vararg) or span))
            for argument in getattr(node.args, "kwonlyargs", ()):
                reasons.append(("varargs-signature", f"keyword-only parameter [{argument.arg}] has no Java layout", module.span_of(argument) or span))
            if node.args.kwarg is not None:
                reasons.append(("varargs-signature", f"parameter [**{node.args.kwarg.arg}] collects the keyword arguments", module.span_of(node.args.kwarg) or span))
        return_type = function_def.returnType()
        hint = return_type.typeAnnotation() if return_type is not None else None
        if java_layout is not None:
            pass  # the Java method fixes the return type
        elif hint is None:
            pass  # the stub declares an Object return: the body returns its values boxed
        elif bindings is not None and hint.name() not in ("None",) and bindings.of_hint(hint) is None:
            reasons.append(("unhinted-return", f"the return hint [{hint.name()}] resolves to no Java type or class of the compilation", span))
        return reasons

    def _body_reasons(self, module, node):
        """Every statement or expression of the body whose kind has no lowering."""
        reasons = []
        for statement in node.body:
            self._walk(module, statement, reasons)
        return reasons

    def _walk(self, module, node, reasons):
        if isinstance(node, ast.stmt):
            if not isinstance(node, SUPPORTED_STATEMENTS):
                reasons.append(("unsupported-statement", f"{_describe(node)} has no static lowering", module.span_of(node)))
                return
            if isinstance(node, (ast.While, ast.For)) and node.orelse:
                reasons.append(("unsupported-statement", "the else clause of a loop has no static lowering", module.span_of(node.orelse[0])))
            if isinstance(node, ast.For) and not isinstance(node.target, ast.Name) and not _unpacks_items(node):
                reasons.append(("unsupported-statement", "unpacking the loop variable has no static lowering", module.span_of(node.target)))
            if isinstance(node, ast.Try):
                if node.orelse:
                    reasons.append(("unsupported-statement", "the else clause of a try statement has no static lowering", module.span_of(node.orelse[0])))
                for handler in node.handlers:
                    if handler.type is None:
                        reasons.append(("python-exception", "a bare except clause catches Python exceptions", module.span_of(handler)))
            if isinstance(node, ast.Assign) and (len(node.targets) != 1 or isinstance(node.targets[0], (ast.Tuple, ast.List, ast.Starred))):
                reasons.append(("unsupported-statement", "unpacking or chained assignment has no static lowering", module.span_of(node)))
        elif isinstance(node, ast.expr):
            if not isinstance(node, SUPPORTED_EXPRESSIONS):
                reasons.append(("unsupported-expression", f"{_describe(node)} has no static lowering", module.span_of(node)))
                return
            if isinstance(node, ast.Subscript) and isinstance(node.slice, ast.Slice):
                reasons.append(("unsupported-expression", "a slice has no static lowering", module.span_of(node)))
                return
        for child in ast.iter_child_nodes(node):
            if isinstance(child, (ast.stmt, ast.expr, ast.excepthandler, ast.keyword)):
                self._walk(module, child, reasons)


_NAMES = {
    ast.With: "a with statement", ast.AsyncWith: "an async with statement", ast.AsyncFor: "an async for loop",
    ast.FunctionDef: "a nested function", ast.AsyncFunctionDef: "a nested async function", ast.ClassDef: "a nested class",
    ast.Global: "a global declaration", ast.Nonlocal: "a nonlocal declaration", ast.Delete: "a del statement",
    ast.Import: "an import", ast.ImportFrom: "an import", ast.Lambda: "a lambda", ast.ListComp: "a list comprehension",
    ast.SetComp: "a set comprehension", ast.DictComp: "a dict comprehension", ast.GeneratorExp: "a generator expression",
    ast.Await: "an await expression", ast.Yield: "a yield expression", ast.YieldFrom: "a yield from expression",
    ast.NamedExpr: "an assignment expression", ast.Starred: "a starred expression", ast.Slice: "a slice",
}
if hasattr(ast, "Match"):
    _NAMES[ast.Match] = "a match statement"
if hasattr(ast, "TryStar"):
    _NAMES[ast.TryStar] = "a try statement with except*"


def _typed_shape(typed):
    """A type the checker inferred, as a comparable value: its kind, its name, whether it may be None and its type arguments."""
    if typed is None:
        return None
    name = typed.name if isinstance(typed.name, str) else typed.name.qualifiedName()
    return (typed.kind, name, bool(typed.nullable), tuple(_typed_shape(argument) for argument in typed.args))


def _unpacks_items(node):
    """Whether the loop is `for key, value in mapping.items()`, which the lowering unpacks."""
    target, iterable = node.target, node.iter
    return (isinstance(target, ast.Tuple) and len(target.elts) == 2
            and all(isinstance(element, ast.Name) for element in target.elts)
            and isinstance(iterable, ast.Call) and isinstance(iterable.func, ast.Attribute)
            and iterable.func.attr == "items" and not iterable.args and not iterable.keywords)


def _yields(function_node):
    """Whether the function body yields anywhere outside a nested function or lambda."""
    stack = list(function_node.body)
    while stack:
        node = stack.pop()
        if isinstance(node, (ast.Yield, ast.YieldFrom)):
            return True
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda, ast.ClassDef)):
            continue
        stack.extend(ast.iter_child_nodes(node))
    return False


def _describe(node):
    return _NAMES.get(type(node), f"a {type(node).__name__} node")


class AttributeWrites:
    """
    The writes of attributes of objects in the sources of a compilation, by the name of the attribute
    (StaticPlanner.fixed_attribute): the nodes assigning or deleting ``<anything>.<name>`` but an
    attribute of the receiver of the method they are in (``self.<name>``: an object of that class,
    whose own writes the class answers for, and which the reported changes cover anyway); the names
    a call writes by a string (``setattr(o, "x", v)``, ``monkeypatch.setattr(o, "x", v)``,
    ``patch.object(C, "x")``, ``patch("app.m.C.x")``, ``patch.multiple(o, x=v)``); the classes whose
    methods write attributes of ``self`` by a computed name (``setattr(self, name, value)``); and
    ``everything`` when a write may reach any attribute: ``__dict__`` (or the string), ``vars()``, a computed
    ``setattr`` on another object, an assignment of ``__class__``, ``__bases__`` or a member of the
    attribute protocol (``Cls.__getattribute__ = f``, ``setattr(Cls, "__setattr__", f)``), a reference
    to ``__setattr__`` that is not called, or a source whose tree is not known.
    """

    def __init__(self, trees):
        self.stores = {}              # attribute name -> ids of the Attribute nodes storing or deleting it on another object than self
        self.named = set()            # the names calls write by a string
        self.classes_written = set()  # id(class node) of the classes whose methods setattr(self, name, ...)
        self.everything = False
        for tree in trees:
            if tree is None:
                self.everything = True
                continue
            self._scan(tree)

    def _scan(self, tree):
        receivers = {}  # id(node) -> (class node, receiver) for the nodes of a method's own scope
        for class_node in ast.walk(tree):
            if isinstance(class_node, ast.ClassDef):
                for statement in class_node.body:
                    if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef)) and statement.args.args:
                        for child in _scope_nodes(statement):
                            receivers[id(child)] = (class_node, statement.args.args[0].arg)
        callees = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Call):
                self._call(node, receivers)
                callees.add(id(node.func))
        for node in ast.walk(tree):
            if isinstance(node, ast.Constant) and node.value == "__dict__":
                # getattr(o, "__dict__"), object.__getattribute__(o, "__dict__"): the dictionary itself
                self.everything = True
            if not isinstance(node, ast.Attribute):
                continue
            if node.attr == "__dict__" or (node.attr in PROTOCOL_CHANGES and isinstance(node.ctx, (ast.Store, ast.Del))):
                # the class or the attribute protocol of objects changed: what an attribute reads may change with it
                self.everything = True
            elif node.attr in ("__setattr__", "__delattr__") and id(node) not in callees:
                self.everything = True  # a reference kept to write with later
            if isinstance(node.ctx, (ast.Store, ast.Del)):
                owner = receivers.get(id(node))
                if owner is None or not isinstance(node.value, ast.Name) or node.value.id != owner[1]:
                    self.stores.setdefault(node.attr, []).append(id(node))

    def _call(self, call, receivers):
        function = call.func
        name = function.id if isinstance(function, ast.Name) else function.attr if isinstance(function, ast.Attribute) else None
        if name == "vars" and isinstance(function, ast.Name) and (call.args or call.keywords):
            self.everything = True
            return
        if name not in NAMED_WRITERS:
            return
        strings = [argument.value for argument in list(call.args) + [keyword.value for keyword in call.keywords]
                   if isinstance(argument, ast.Constant) and isinstance(argument.value, str)]
        for string in strings:
            self.named.add(string.rsplit(".", 1)[-1])
            if string.rsplit(".", 1)[-1] in PROTOCOL_CHANGES:
                self.everything = True  # setattr(Cls, "__getattribute__", f), patch.object(Cls, "__setattr__")
        if name == "multiple":
            self.named.update(keyword.arg for keyword in call.keywords if keyword.arg is not None)
            if any(keyword.arg in PROTOCOL_CHANGES for keyword in call.keywords):
                self.everything = True
        if _is_patch(function, name):
            # mock.patch(target, ...), patch.object(target, attribute, ...): a computed name may be any attribute
            named = call.args[1 if name == "object" else 0] if len(call.args) > (1 if name == "object" else 0) else None
            if named is not None and not (isinstance(named, ast.Constant) and isinstance(named.value, str)):
                self.everything = True
            return
        if name not in COMPUTED_WRITERS:
            return
        # the object written and the name it is written by: setattr(o, name, v), monkeypatch.setattr(o, name, v),
        # object.__setattr__(o, name, v), or o.__setattr__(name, v) bound; a string anywhere else is a value
        if name in ("__setattr__", "__delattr__") and isinstance(function, ast.Attribute) and not (isinstance(function.value, ast.Name) and function.value.id in ("object", "type")):
            target, named = function.value, call.args[0] if call.args else None
        else:
            target, named = (call.args[0] if call.args else None), (call.args[1] if len(call.args) > 1 else None)
            if isinstance(target, ast.Constant) and isinstance(target.value, str) and isinstance(function, ast.Attribute):
                return  # monkeypatch.setattr("app.m.Cls.attribute", v): the name is the string's last part
        if isinstance(named, ast.Constant) and isinstance(named.value, str):
            return
        if isinstance(target, ast.Call) and isinstance(target.func, ast.Name) and target.func.id == "super":
            # super().__setattr__(name, value) in a method: object's write of an attribute of self, past
            # the __setattr__ that reports changes
            owner = receivers.get(id(call))
            if owner is not None:
                self.classes_written.add(id(owner[0]))
                return
        owner = receivers.get(id(call))
        if owner is not None and isinstance(target, ast.Name) and owner[1] == target.id:
            # setattr(self, name, value) in a method: an attribute of an object of that class
            self.classes_written.add(id(owner[0]))
            return
        self.everything = True


def _is_patch(function, name):
    """Whether a call is mock.patch(...) or patch.object(...): patch, mock.patch, unittest.mock.patch, and their object."""
    if name == "patch":
        return isinstance(function, ast.Name) or (isinstance(function, ast.Attribute) and _names(function.value, "mock"))
    return name == "object" and isinstance(function, ast.Attribute) and _names(function.value, "patch")


def _names(node, name):
    """Whether the expression is the name, or an attribute of that name (mock, unittest.mock)."""
    return (isinstance(node, ast.Name) and node.id == name) or (isinstance(node, ast.Attribute) and node.attr == name)


def _scope_nodes(function):
    """The nodes of a function's own scope: those of the functions, lambdas and classes it defines excluded."""
    stack = list(function.body)
    while stack:
        node = stack.pop()
        yield node
        for child in ast.iter_child_nodes(node):
            if not isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda, ast.ClassDef)):
                stack.append(child)


def _receiver_store(node, receiver):
    """Whether the node assigns or deletes an attribute of the receiver, or setattr()s or delattr()s one."""
    if isinstance(node, ast.Attribute) and isinstance(node.ctx, (ast.Store, ast.Del)):
        return isinstance(node.value, ast.Name) and node.value.id == receiver
    if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id in ("setattr", "delattr"):
        return bool(node.args) and isinstance(node.args[0], ast.Name) and node.args[0].id == receiver
    return False


def _declared_names(statement):
    """The names a statement of a class body binds: a function, a class, an assignment or an import."""
    if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
        return [statement.name]
    if isinstance(statement, ast.Assign):
        return [name.id for target in statement.targets for name in ast.walk(target) if isinstance(name, ast.Name)]
    if isinstance(statement, (ast.AnnAssign, ast.AugAssign)):
        return [name.id for name in ast.walk(statement.target) if isinstance(name, ast.Name)]
    if isinstance(statement, (ast.Import, ast.ImportFrom)):
        return [(alias.asname or alias.name).split(".")[0] for alias in statement.names]
    return []


def _generates_class(script):
    """Whether the script modelled for a module generates a class: one with functions, attributes or module annotations (an empty script is dropped)."""
    return script is not None and (len(script.functions()) > 0 or len(script.attributes()) > 0 or len(script.decorators()) > 0)


# the class attribute through which the rewritten methods of a class reach the Java bodies of its bound
# objects (PythonStatic.COMPILED_MEMBER): None until an object of the class is bound
JAVA_INSTANCE_MEMBER = "__micronaut_compiled__"


def apply_delegation(tree, targets):
    """
    Rewrite the compiled functions of a runtime module tree so that, on an object bound to its
    Java stub, the Java body runs instead of the Python one::

        class Calc:
            __micronaut_compiled__ = None

            def total(self, quantity, unit_price):
                __mn_java = __class__.__micronaut_compiled__
                if __mn_java is not None:
                    __mn_java_p = None
                    try:
                        __mn_java = __mn_java.call(self, 'app.Calc', 'total', (quantity, unit_price), ('int', 'float'), True)
                    except BaseException as __mn_java_e:
                        __mn_java_p = _mn_python_exception(__mn_java_e)
                        if __mn_java_p is None:
                            raise
                    if __mn_java_p is not None:
                        raise __mn_java_p
                    if __mn_java is not None:
                        return __mn_java[0]
                ...the original body, for objects created in Python...

    The rewritten function names the class it is defined in (``__class__``) and the generated
    class of that class, so ``super().total()`` from a subclass runs the base's Java body, not the
    override. The binding of an object lives outside it, in the registry the class attribute
    holds once an object of the class is bound (``micronaut_runtime``): a copy of the object is
    not bound and runs the Python body on its own attributes. The registry runs the Java body only
    when every argument has the exact Python type and range of its Java parameter, and runs the
    Python body when a body that may be run again fails, see ``StaticDelegation``.

    A Python exception raised by the compilation (a compiled ``raise``, the Python code a compiled
    body calls) reaches the Java body as the generated Java class of its exception class, which a
    Python caller of the Python body would catch as the Python class. A target carrying
    ``#python-exceptions`` (the body calls no Java code outside the compilation, see
    ``Ir.ExceptionCrossing``) has the prologue above: it raises the Python exception the Java one
    carries (see ``_mn_python_exception``), and any other exception as it is. The Python exception
    is raised after the handler, so it keeps its own cause and context: inside the handler Python
    would chain the Java exception as its context. Without it the call is made as is, and a Java
    exception reaches the caller as the Java exception, as one that crossed Java code does in Python.

    ``targets`` are ``Class#method#owner=<generated class>#kinds=<kind,...>#retry`` (``#once``
    for a body that is not run again) strings, a nested class as ``Outer$Inner``; a target without
    its kinds is not delegated. ``#list``, ``#set`` or ``#dict`` is appended when the Java body
    returns a collection, which a Python caller receives as a Python one.

    A static method carries ``#static=<the generated class>`` in place of the owner, and a
    module-level function an empty class part: they call the static Java method of the generated
    class, looked up once per module, under the same checks of the arguments. Without a bound
    object a failure cannot reach the caller as Python's exception, so only a body that may be run
    again (``#retry``) delegates, and the Python body runs when it fails, raising its own
    exceptions::

        def user_public(user):
            __mn_java = _mn_static_call('app.Mappers', 'user_public', (user,), ('java:app.User',))
            if __mn_java is not None:
                return __mn_java[0]
            ...the original body, when the class is not on the class path, or the call cannot or did not run...

    Returns how many functions were rewritten.
    """
    wanted = {}
    for target in targets:
        class_name, method, *rest = target.split("#")
        options = dict(part.split("=", 1) if "=" in part else (part, True) for part in rest)
        if "kinds" not in options:
            continue
        kinds = tuple(kind for kind in options["kinds"].split(",") if kind)
        conversion = next((name for name in ("list", "set", "dict") if name in options), None)
        static_class = options.get("static")
        if static_class is not None and "retry" not in options:
            continue  # a static body that cannot be run again keeps the Python body, see above
        wanted.setdefault(class_name, {})[method] = (options.get("owner", class_name), kinds, "retry" in options, conversion, static_class, "python-exceptions" in options)
    count = 0
    for class_node, path in _classes(tree):
        methods = wanted.get("$".join(path))
        if not methods:
            continue
        rewritten = 0
        for statement in class_node.body:
            if isinstance(statement, ast.FunctionDef) and statement.name in methods and not _delegates(statement):
                owner, kinds, retry, conversion, static_class, translated = methods[statement.name]
                if static_class is not None:
                    if not _takes_exactly(statement, kinds):
                        continue
                    statement.body[_docstring_offset(statement):_docstring_offset(statement)] = _static_delegation(statement, static_class, kinds, conversion)
                    ast.fix_missing_locations(statement)
                    count += 1
                    continue
                if not _receives_self(statement, kinds):
                    continue
                statement.body[_docstring_offset(statement):_docstring_offset(statement)] = _delegation(statement, owner, kinds, retry, conversion, translated)
                ast.fix_missing_locations(statement)
                rewritten += 1
        if rewritten and not _declares_member(class_node):
            member = ast.Assign(targets=[ast.Name(id=JAVA_INSTANCE_MEMBER, ctx=ast.Store())], value=ast.Constant(value=None))
            _locate([member], class_node.lineno, class_node.col_offset)
            class_node.body.insert(_docstring_offset(class_node), member)
        count += rewritten
    functions = wanted.get("")
    if functions:
        for statement in tree.body:
            if isinstance(statement, ast.FunctionDef) and statement.name in functions and not _delegates(statement):
                _, kinds, _, conversion, static_class, _ = functions[statement.name]
                if static_class is not None and _takes_exactly(statement, kinds):
                    statement.body[_docstring_offset(statement):_docstring_offset(statement)] = _static_delegation(statement, static_class, kinds, conversion)
                    ast.fix_missing_locations(statement)
                    count += 1
    if count and not any(isinstance(statement, ast.FunctionDef) and statement.name == STATIC_LOOKUP for statement in tree.body):
        offset = _docstring_offset(tree)
        while offset < len(tree.body) and isinstance(tree.body[offset], ast.ImportFrom) and tree.body[offset].module == "__future__":
            offset += 1
        lookup = ast.parse(HELPERS_SOURCE).body
        for statement in lookup:
            for node in ast.walk(statement):
                if isinstance(node, (ast.stmt, ast.expr, ast.arg)):
                    node.lineno = node.end_lineno = 1
                    node.col_offset = node.end_col_offset = 0
        tree.body[offset:offset] = lookup
    return count


# the helpers of the rewritten functions, added once to a module with a rewritten function: the lookup of
# the generated class a static Java method belongs to, cached per module; the call of the static Java
# method, only when every argument has the exact Python type and range of its Java parameter
# (micronaut_runtime), else, or when the Java body fails, None, so the Python body runs; and the Python
# exception a Java exception carries
STATIC_LOOKUP = "_mn_static_class"
STATIC_CALL = "_mn_static_call"
PYTHON_EXCEPTION = "_mn_python_exception"
HELPERS_SOURCE = """
_mn_static_classes = {}


def _mn_static_class(name):
    found = _mn_static_classes.get(name, False)
    if found is False:
        try:
            import java as __mn_java_module
            found = __mn_java_module.type(name)
        except Exception:
            found = None
        _mn_static_classes[name] = found
    return found


def _mn_static_call(name, method, arguments, kinds):
    found = _mn_static_class(name)
    if found is None:
        return None
    try:
        from micronaut_runtime import _micronaut_fits as fits
    except Exception:
        return None
    for argument, kind in zip(arguments, kinds):
        if not fits(argument, kind):
            return None
    try:
        return (getattr(found, method)(*arguments),)
    except Exception:
        return None


def _mn_python_exception(error):
    try:
        unwrap = error.asPolyglotValue
    except Exception:
        return None
    try:
        found = unwrap()
    except Exception:
        return None
    return found if isinstance(found, BaseException) else None
"""


def _result_holder():
    return ast.Name(id="__mn_m", ctx=ast.Load())


def _classes(tree, path=()):
    for node in getattr(tree, "body", ()):
        if isinstance(node, ast.ClassDef):
            here = path + (node.name,)
            yield node, here
            yield from _classes(node, here)


def _docstring_offset(function):
    body = function.body
    return 1 if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant) and isinstance(body[0].value.value, str) else 0


DELEGATE_LOCAL = "__mn_java"


def _declares_member(class_node):
    return any(isinstance(statement, ast.Assign) and any(isinstance(target, ast.Name) and target.id == JAVA_INSTANCE_MEMBER for target in statement.targets)
               for statement in class_node.body)


def _receives_self(function, kinds):
    """Whether the function takes the object and exactly the parameters the Java body does, positionally."""
    args = function.args
    positional = list(args.posonlyargs) + list(args.args)
    return bool(positional) and len(positional) - 1 == len(kinds) and args.vararg is None and args.kwarg is None and not args.kwonlyargs


def _delegates(function):
    """Whether the function is already rewritten: its first statement reads the registry of its class, or calls the static Java method."""
    body = function.body
    first = body[_docstring_offset(function)] if len(body) > _docstring_offset(function) else None
    if not isinstance(first, ast.Assign):
        return False
    value = first.value
    if isinstance(value, ast.Call) and isinstance(value.func, ast.Name) and value.func.id == STATIC_CALL:
        return True
    return (isinstance(value, ast.Attribute) and value.attr == JAVA_INSTANCE_MEMBER
            and isinstance(value.value, ast.Name) and value.value.id == "__class__")


def _takes_exactly(function, kinds):
    """Whether a static method or a module-level function takes exactly the parameters the Java body does, positionally."""
    args = function.args
    positional = list(args.posonlyargs) + list(args.args)
    return len(positional) == len(kinds) and args.vararg is None and args.kwarg is None and not args.kwonlyargs


def _static_delegation(function, static_class, kinds, conversion=None):
    """The prologue of a static method or a module-level function: the static Java method of the generated class, when it is loadable and the arguments fit it."""
    args = function.args
    names = [argument.arg for argument in list(args.posonlyargs) + list(args.args)]
    temporary = _temporary(function)
    call = ast.Assign(
        targets=[ast.Name(id=temporary, ctx=ast.Store())],
        value=ast.Call(
            func=ast.Name(id=STATIC_CALL, ctx=ast.Load()),
            args=[
                ast.Constant(value=static_class),
                ast.Constant(value=function.name),
                ast.Tuple(elts=[ast.Name(id=name, ctx=ast.Load()) for name in names], ctx=ast.Load()),
                ast.Tuple(elts=[ast.Constant(value=kind) for kind in kinds], ctx=ast.Load()),
            ],
            keywords=[],
        ),
    )
    result = _converted(ast.Subscript(value=ast.Name(id=temporary, ctx=ast.Load()), slice=ast.Constant(value=0), ctx=ast.Load()), conversion)
    guard = ast.If(
        test=ast.Compare(left=ast.Name(id=temporary, ctx=ast.Load()), ops=[ast.IsNot()], comparators=[ast.Constant(value=None)]),
        body=[ast.Return(value=result)],
        orelse=[],
    )
    statements = [call, guard]
    _locate(statements, function.lineno, function.col_offset)
    return statements


def _translating(temporary, call):
    """
    The call of the Java body, raising the Python exception a Java exception of the call carries::

        __mn_java_p = None
        try:
            __mn_java = __mn_java.call(...)
        except BaseException as __mn_java_e:
            __mn_java_p = _mn_python_exception(__mn_java_e)
            if __mn_java_p is None:
                raise
        if __mn_java_p is not None:
            raise __mn_java_p

    The Python exception is raised after the handler, so it keeps its own cause and context: inside
    the handler Python would chain the Java exception as its context, and ``raise ... from`` would
    suppress the context of the caller's own handler.
    """
    caught = temporary + "_e"
    found = temporary + "_p"
    handler = ast.ExceptHandler(
        type=ast.Name(id="BaseException", ctx=ast.Load()),
        name=caught,
        body=[
            ast.Assign(
                targets=[ast.Name(id=found, ctx=ast.Store())],
                value=ast.Call(func=ast.Name(id=PYTHON_EXCEPTION, ctx=ast.Load()), args=[ast.Name(id=caught, ctx=ast.Load())], keywords=[]),
            ),
            ast.If(
                test=ast.Compare(left=ast.Name(id=found, ctx=ast.Load()), ops=[ast.Is()], comparators=[ast.Constant(value=None)]),
                body=[ast.Raise(exc=None, cause=None)],
                orelse=[],
            ),
        ],
    )
    return [
        ast.Assign(targets=[ast.Name(id=found, ctx=ast.Store())], value=ast.Constant(value=None)),
        ast.Try(body=[call], handlers=[handler], orelse=[], finalbody=[]),
        ast.If(
            test=ast.Compare(left=ast.Name(id=found, ctx=ast.Load()), ops=[ast.IsNot()], comparators=[ast.Constant(value=None)]),
            body=[ast.Raise(exc=ast.Name(id=found, ctx=ast.Load()), cause=None)],
            orelse=[],
        ),
    ]


def _converted(result, conversion):
    """The result of a Java method with its collection converted for the Python caller."""
    if conversion in ("list", "set"):
        return ast.Call(func=ast.Name(id=conversion, ctx=ast.Load()), args=[result], keywords=[])
    if conversion == "dict":
        # dict((k, m.get(k)) for k in m.keySet()): a Java map read through its interop members
        converted = ast.parse("dict((__mn_k, __mn_m.get(__mn_k)) for __mn_k in __mn_m.keySet())").body[0].value
        converted.args[0].generators[0].iter.func.value = _result_holder()
        return ast.Call(
            func=ast.Lambda(args=ast.arguments(posonlyargs=[], args=[ast.arg(arg="__mn_m")], vararg=None, kwonlyargs=[], kw_defaults=[], kwarg=None, defaults=[]), body=converted),
            args=[result],
            keywords=[],
        )
    return result


def _temporary(function):
    """A name for the delegate, and with the _e and _p suffixes for the exception it translates, that no parameter or name of the function uses."""
    used = {node.arg for node in ast.walk(function.args) if isinstance(node, ast.arg)}
    used |= {node.id for node in ast.walk(function) if isinstance(node, ast.Name)}
    name = DELEGATE_LOCAL
    suffix = 0
    while name in used or f"{name}_e" in used or f"{name}_p" in used:
        suffix += 1
        name = f"{DELEGATE_LOCAL}_{suffix}"
    return name


def _locate(statements, line, column):
    for statement in statements:
        for node in ast.walk(statement):
            if isinstance(node, (ast.stmt, ast.expr)):
                node.lineno = node.end_lineno = line
                node.col_offset = node.end_col_offset = column


def _delegation(function, owner, kinds, retry, conversion=None, translated=False):
    args = function.args
    positional = list(args.posonlyargs) + list(args.args)
    receiver = positional[0].arg
    names = [argument.arg for argument in positional[1:]]
    temporary = _temporary(function)

    def load(name):
        return ast.Name(id=name, ctx=ast.Load())

    lookup = ast.Assign(
        targets=[ast.Name(id=temporary, ctx=ast.Store())],
        value=ast.Attribute(value=load("__class__"), attr=JAVA_INSTANCE_MEMBER, ctx=ast.Load()),
    )
    call = ast.Assign(
        targets=[ast.Name(id=temporary, ctx=ast.Store())],
        value=ast.Call(
            func=ast.Attribute(value=load(temporary), attr="call", ctx=ast.Load()),
            args=[
                load(receiver),
                ast.Constant(value=owner),
                ast.Constant(value=function.name),
                ast.Tuple(elts=[load(name) for name in names], ctx=ast.Load()),
                ast.Tuple(elts=[ast.Constant(value=kind) for kind in kinds], ctx=ast.Load()),
                ast.Constant(value=retry),
            ],
            keywords=[],
        ),
    )
    result = _converted(ast.Subscript(value=load(temporary), slice=ast.Constant(value=0), ctx=ast.Load()), conversion)
    returned = ast.If(
        test=ast.Compare(left=load(temporary), ops=[ast.IsNot()], comparators=[ast.Constant(value=None)]),
        body=[ast.Return(value=result)],
        orelse=[],
    )
    guard = ast.If(
        test=ast.Compare(left=load(temporary), ops=[ast.IsNot()], comparators=[ast.Constant(value=None)]),
        body=(_translating(temporary, call) if translated else [call]) + [returned],
        orelse=[],
    )
    statements = [lookup, guard]
    _locate(statements, function.lineno, function.col_offset)
    return statements


__all__ = ["StaticPlanner", "CompileScope", "MODE_OFF", "MODE_ANNOTATED", "MODE_ALL", "MODES", "RULE", "apply_delegation"]
