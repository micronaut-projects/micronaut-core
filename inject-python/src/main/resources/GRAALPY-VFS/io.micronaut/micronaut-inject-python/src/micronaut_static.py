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

from micronaut_typecheck import Bindings, CheckUnit, JavaReceiverRules, TypeFacts, PythonClasses, PythonClassModel, function_node, switch_value
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


class CompileScope:
    """
    Resolves whether a definition is compiled from its decorators and its enclosing scopes, and
    which declaration decided: the nearest ``CompileStatic`` switch, else the compilation's mode.
    """

    def __init__(self, mode, annotation_names):
        if mode not in MODES:
            raise ValueError(f"Unknown static compilation mode [{mode}]; expected one of {', '.join(MODES)}")
        self.mode = mode
        self.annotation_names = frozenset(annotation_names or ())

    def switch(self, decorators):
        """True or False when one of the decorators is an accepted switch, None otherwise; a bare decorator switches on."""
        for decorator in decorators or ():
            if decorator.annotationName() in self.annotation_names:
                members = decorator.members()
                value = members.get("value") if members is not None else None
                return True if value is None else switch_value(value)
        return None

    def resolve(self, module_decorators, class_decorators, function_decorators):
        """(compiled, scope name) for a function under the given declarations."""
        for decorators, scope in ((function_decorators, "FUNCTION"), (class_decorators, "CLASS"), (module_decorators, "MODULE")):
            switch = self.switch(decorators)
            if switch is not None:
                return switch, scope
        return self.mode == MODE_ALL, "MODE"


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
        # the classes already reported for a class-level switch they can never honour
        self._reported_classes = set()

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
        self._reported_classes = set()
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

    def body_of(self, node):
        """The compiled body of a decided function, or None."""
        return self._bodies.get(id(node))

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
        compiled, scope = self.scope.resolve(module.decorators, class_def.decorators() if class_def is not None else None, function_def.decorators())
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
            ineligible_class = scope == "CLASS" and reasons[0][0] == "class-not-eligible" and self._first_report(class_def)
            return self._record(qualified, span, "NOT_CANDIDATE", scope, reasons, 0, explicit=scope == "FUNCTION" or ineligible_class)
        reasons = self._signature_reasons(module, class_def, function_def, node, span, java_layout, bindings)
        statements = 0
        if node is not None:
            statements = len(node.body)
            reasons.extend(self._body_reasons(module, node))
        if reasons or node is None or getattr(self.checker, "facts", None) is None:
            outcome = "SKIPPED" if reasons else "CANDIDATE"
            return self._record(qualified, span, outcome, scope, reasons, statements, explicit=scope != "MODE")
        body, reasons = self._lower(module, class_def, function_def, node, java_layout)
        if body is None:
            return self._record(qualified, span, "SKIPPED", scope, reasons, statements, explicit=scope != "MODE")
        if class_def is None and body.stats().bridgeCalls() > 0 and self._pooled_module(module):
            # a module served by a context pool: a body of its generated class runs outside any
            # Python context, so it cannot reach a Python object
            reason = ("pooled-module", "the module is served by a context pool; a body reaching a Python object has no context to reach it in", span)
            return self._record(qualified, span, "SKIPPED", scope, [reason], statements, explicit=scope != "MODE")
        if class_def is None and body.staticMethod() and not _generates_class(getattr(module, "script", None)):
            # a module without a generated class gets one for its compiled functions
            self._add_script(module)
        self.bodies.append(body)
        if node is not None:
            self._bodies[id(node)] = body
        return self._record(qualified, span, "COMPILED", scope, [], statements, stats=body.stats())

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
                            java_layout=java_layout, planner=self, static=static)
        body = lowering.lower()
        return body, lowering.reasons

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
        if explicit and reasons and (self.scope.mode != MODE_ALL or scope == "FUNCTION"):
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
                    __mn_java = __mn_java.call(self, 'app.Calc', 'total', (quantity, unit_price), ('int', 'float'), True)
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

    ``targets`` are ``Class#method#owner=<generated class>#kinds=<kind,...>#retry`` (``#once``
    for a body that is not run again) strings, a nested class as ``Outer$Inner``; a target without
    its kinds is not delegated. ``#list``, ``#set`` or ``#dict`` is appended when the Java body
    returns a collection, which a Python caller receives as a Python one.

    A static method carries ``#static=<the generated class>`` in place of the owner, and a
    module-level function an empty class part: they call the static Java method of the generated
    class, looked up once per module, under the same checks of the arguments. Without a bound
    object a failure cannot reach the caller as Python's exception, so only a body that may be run
    again (``#retry``) delegates, and the Python body runs when it fails::

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
        wanted.setdefault(class_name, {})[method] = (options.get("owner", class_name), kinds, "retry" in options, conversion, static_class)
    count = 0
    statics = False
    for class_node, path in _classes(tree):
        methods = wanted.get("$".join(path))
        if not methods:
            continue
        rewritten = 0
        for statement in class_node.body:
            if isinstance(statement, ast.FunctionDef) and statement.name in methods and not _delegates(statement):
                owner, kinds, retry, conversion, static_class = methods[statement.name]
                if static_class is not None:
                    if not _takes_exactly(statement, kinds):
                        continue
                    statement.body[_docstring_offset(statement):_docstring_offset(statement)] = _static_delegation(statement, static_class, kinds, conversion)
                    ast.fix_missing_locations(statement)
                    statics = True
                    count += 1
                    continue
                if not _receives_self(statement, kinds):
                    continue
                statement.body[_docstring_offset(statement):_docstring_offset(statement)] = _delegation(statement, owner, kinds, retry, conversion)
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
                _, kinds, _, conversion, static_class = functions[statement.name]
                if static_class is not None and _takes_exactly(statement, kinds):
                    statement.body[_docstring_offset(statement):_docstring_offset(statement)] = _static_delegation(statement, static_class, kinds, conversion)
                    ast.fix_missing_locations(statement)
                    statics = True
                    count += 1
    if statics and not any(isinstance(statement, ast.FunctionDef) and statement.name == STATIC_LOOKUP for statement in tree.body):
        offset = _docstring_offset(tree)
        while offset < len(tree.body) and isinstance(tree.body[offset], ast.ImportFrom) and tree.body[offset].module == "__future__":
            offset += 1
        lookup = ast.parse(STATIC_LOOKUP_SOURCE).body
        for statement in lookup:
            for node in ast.walk(statement):
                if isinstance(node, (ast.stmt, ast.expr, ast.arg)):
                    node.lineno = node.end_lineno = 1
                    node.col_offset = node.end_col_offset = 0
        tree.body[offset:offset] = lookup
    return count


# the module-level lookup of the generated class a static Java method belongs to, cached per module, and
# the call of the static Java method: only when every argument has the exact Python type and range of its
# Java parameter (micronaut_runtime), else, or when the Java body fails, None, so the Python body runs
STATIC_LOOKUP = "_mn_static_class"
STATIC_CALL = "_mn_static_call"
STATIC_LOOKUP_SOURCE = """
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
    """A name for the delegate that no parameter or name of the function uses."""
    used = {node.arg for node in ast.walk(function.args) if isinstance(node, ast.arg)}
    used |= {node.id for node in ast.walk(function) if isinstance(node, ast.Name)}
    name = DELEGATE_LOCAL
    suffix = 0
    while name in used:
        suffix += 1
        name = f"{DELEGATE_LOCAL}_{suffix}"
    return name


def _locate(statements, line, column):
    for statement in statements:
        for node in ast.walk(statement):
            if isinstance(node, (ast.stmt, ast.expr)):
                node.lineno = node.end_lineno = line
                node.col_offset = node.end_col_offset = column


def _delegation(function, owner, kinds, retry, conversion=None):
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
        body=[call, returned],
        orelse=[],
    )
    statements = [lookup, guard]
    _locate(statements, function.lineno, function.col_offset)
    return statements


__all__ = ["StaticPlanner", "CompileScope", "MODE_OFF", "MODE_ANNOTATED", "MODE_ALL", "MODES", "RULE", "apply_delegation"]
