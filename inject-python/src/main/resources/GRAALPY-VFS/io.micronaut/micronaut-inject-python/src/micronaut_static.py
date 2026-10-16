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
  ``__slots__`` or ``__getattr__``), an ``async`` or generator function or a special method
  (property accessors are properties of the model, not functions, and get no decision);
* ``SKIPPED`` with every reason the author can address: a signature without a fixed Java layout
  (``*args``, ``**kwargs``, keyword-only parameters, an unhinted or unresolvable parameter or
  return), a statement or expression kind that has no lowering;
* ``CANDIDATE`` when every check passes.

An explicit ``@CompileStatic`` that cannot be honoured is reported as a warning (an error when the
planner is strict); mode ``all`` never warns, since skipping is the expected common case there.
"""
import ast

import java

from micronaut_typecheck import Bindings, CheckUnit, TypeFacts, PythonClasses, _function_node, _switch_value

PythonDiagnostic = java.type("io.micronaut.python.processing.diagnostic.PythonDiagnostic")
Decision = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision")
Outcome = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Outcome")
Scope = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Scope")
Reason = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Reason")
Stats = java.type("io.micronaut.python.processing.staticcompile.StaticCompilationDecision$Stats")

MODE_OFF = "off"
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
                         ast.Subscript, ast.IfExp, ast.keyword)
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
                return True if value is None else _switch_value(value)
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
        self.diagnostics = []

    def plan(self, checker, visitor_context=None):
        """
        Decide every function recorded by the type checker; returns the decisions. The checker's
        facts are created from the visitor context when its own check did not run.
        """
        if getattr(checker, "facts", None) is None and visitor_context is not None:
            checker.facts = TypeFacts(visitor_context)
            checker.python_classes = PythonClasses(checker)
        self.checker = checker
        for module in checker._modules.values():
            for function_def, node in module.functions:
                self._decide(module, None, None, function_def, node)
            for class_def, class_node in module.classes:
                members = list(class_def.functions())
                if class_def.constructor() is not None:
                    members.append(class_def.constructor())
                members.sort(key=lambda function_def: function_def.span().line() if function_def.span() is not None else 0)
                for function_def in members:
                    self._decide(module, class_def, class_node, function_def, _function_node(class_node, function_def.name()))
        return list(self.decisions)

    # ---------------------------------------------------------------- one function

    def _decide(self, module, class_def, class_node, function_def, node):
        qualified = f"{class_def.name()}.{function_def.name()}" if class_def is not None else function_def.name()
        compiled, scope = self.scope.resolve(module.decorators, class_def.decorators() if class_def is not None else None, function_def.decorators())
        span = function_def.span()
        if not compiled:
            return self._record(qualified, span, "EXCLUDED", scope, [], 0)
        reasons = self._candidate_reasons(class_def, class_node, function_def, node, span)
        if reasons:
            return self._record(qualified, span, "NOT_CANDIDATE", scope, reasons, 0, explicit=scope == "FUNCTION")
        reasons = self._signature_reasons(module, class_def, function_def, node, span)
        statements = 0
        if node is not None:
            statements = len(node.body)
            reasons.extend(self._body_reasons(module, node))
        outcome = "SKIPPED" if reasons else "CANDIDATE"
        return self._record(qualified, span, outcome, scope, reasons, statements, explicit=scope != "MODE")

    def _record(self, qualified, span, outcome, scope, reasons, statements, explicit=False):
        java_reasons = [Reason(rule, message, reason_span) for rule, message, reason_span in reasons]
        decision = Decision(qualified, span, Outcome.valueOf(outcome), Scope.valueOf(scope), java_reasons, Stats(statements, 0, 0, 0))
        self.decisions.append(decision)
        if explicit and reasons and (self.scope.mode != MODE_ALL or scope == "FUNCTION"):
            rule, message, reason_span = reasons[0]
            rest = f" (and {len(reasons) - 1} more, see the report)" if len(reasons) > 1 else ""
            text = f"[{qualified}] cannot be compiled statically: [{rule}] {message}{rest}"
            factory = PythonDiagnostic.error if self.strict else PythonDiagnostic.warning
            self.diagnostics.append(factory(RULE, text, reason_span or span))
        return decision

    # ---------------------------------------------------------------- the checks

    def _candidate_reasons(self, class_def, class_node, function_def, node, span):
        """Why the function can never be compiled, whatever its body."""
        reasons = []
        if class_def is None:
            reasons.append(("class-not-eligible", "a module-level function has no class stub to be compiled into", span))
            return reasons
        ineligible = self._class_ineligibility(class_def, class_node)
        if ineligible is not None:
            reasons.append(("class-not-eligible", ineligible, class_def.span() or span))
            return reasons
        name = function_def.name()
        if function_def.isAsync():
            reasons.append(("async-function", "an async function runs on the Python event loop", span))
        if function_def.isGenerator() or (node is not None and _yields(node)):
            reasons.append(("generator-function", "a generator keeps its frame alive between calls", span))
        if name.startswith("__") and name.endswith("__"):
            what = "the constructor runs in Python so the instance keeps its Python semantics" if name == "__init__" else f"the special method {name} is called by the Python runtime"
            reasons.append(("special-method", what, span))
        if function_def.isAbstract() or function_def.hasPlaceholderBody():
            reasons.append(("abstract-method", "an abstract method has no body to compile; a call of it runs the implementation of the object", span))
        return reasons

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
        description = self.checker.facts.describe(class_def.qualifiedName()) if getattr(self.checker, "facts", None) is not None else None
        if description is not None and description.anInterface():
            return f"[{class_def.name()}] compiles to an interface"
        return None

    def _signature_reasons(self, module, class_def, function_def, node, span):
        """Why the signature has no fixed Java layout, and which hints resolve to no type."""
        reasons = []
        bindings = None
        if getattr(self.checker, "facts", None) is not None and node is not None:
            unit = CheckUnit(module.source_path, function_def.name(), function_def, node, class_def, None, module)
            bindings = Bindings(self.checker, unit)
        for argument in function_def.arguments().arguments():
            argument_span = argument.span() or span
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
        if hint is None:
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
            if isinstance(node, ast.For) and not isinstance(node.target, ast.Name):
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


def _decorator_name(decorator):
    if isinstance(decorator, ast.Name):
        return decorator.id
    if isinstance(decorator, ast.Attribute):
        return decorator.attr
    if isinstance(decorator, ast.Call):
        return _decorator_name(decorator.func)
    return None


__all__ = ["StaticPlanner", "CompileScope", "MODE_OFF", "MODE_ANNOTATED", "MODE_ALL", "MODES", "RULE"]
