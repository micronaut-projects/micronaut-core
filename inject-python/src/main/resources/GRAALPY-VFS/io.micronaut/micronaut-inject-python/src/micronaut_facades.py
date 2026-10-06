"""
Curated Python modules (facades) at compile time.

A facade gathers the names of several Java packages behind one import (``from pyronaut import http``,
then ``@http.Get``, ``http.HttpResponse``, ``http.ok(...)``, ``http.CREATED``). It exists only in the
Python namespace: the compiler resolves every facade name to the Java type, static method or enum constant
it stands for. ``FacadeImportRewriter`` rewrites the compile-time tree of a module so its facade imports
and references become the equivalent Java imports and references, which the transformer and the processor
already understand: the annotation metadata and the generated classes are those a direct import of the Java
packages gives. The source that runs keeps its facade imports, which the Java import finder serves.

A facade is resolved by the Java side (``io.micronaut.python.imports.PythonImportMappings``):
``callback_get_facade(name)`` returns the resolved module, an error message, or None for a name that is
no facade.
"""
import ast
import keyword
from typing import Dict, List, Optional, Set

TYPE_KINDS = frozenset({'ANNOTATION', 'INTERFACE', 'CLASS', 'ENUM'})
GENERATED_PREFIX = '_facade_'


class FacadeMember:
    """A name a facade exports: a Java type, a static method or enum constant of one, or a nested facade."""

    __slots__ = ('name', 'kind', 'binary_name', 'member_name')

    def __init__(self, name: str, kind: str, binary_name: str, member_name: Optional[str]):
        self.name = name
        self.kind = kind
        self.binary_name = binary_name
        self.member_name = member_name

    def target(self) -> str:
        return self.binary_name if self.member_name is None else f'{self.binary_name}#{self.member_name}'


class Facade:
    """A resolved facade."""

    def __init__(self, name: str, active: bool, inactive_reason: Optional[str], members: Dict[str, FacadeMember]):
        self.name = name
        self.active = active
        self.inactive_reason = inactive_reason
        self.members = members

    def annotation_names(self) -> Set[str]:
        return {name for name, member in self.members.items() if member.kind == 'ANNOTATION'}

    def nested_modules(self) -> List[FacadeMember]:
        return [member for member in self.members.values() if member.kind == 'MODULE']


class FacadeRegistry:
    """The facades the Java side resolves, cached per module name."""

    def __init__(self, callback_get_facade, facade_names=None):
        self.callback_get_facade = callback_get_facade
        # the mapped module names: a name outside them is no facade, without asking the Java side
        self.names = {str(name) for name in facade_names} if facade_names is not None else None
        self._cache: Dict[str, object] = {}

    def get(self, name: Optional[str]):
        """The facade of a module name, an error message, or None when the name is no facade."""
        if not name or self.callback_get_facade is None or (self.names is not None and name not in self.names):
            return None
        if name in self._cache:
            return self._cache[name]
        result = self.callback_get_facade(name)
        if result is None or isinstance(result, str):
            facade = result
        else:
            members = {}
            for member in result.members().values():
                member_name = member.memberName()
                members[str(member.name())] = FacadeMember(
                    str(member.name()), str(member.kind().name()), str(member.binaryName()),
                    str(member_name) if member_name is not None else None)
            inactive_reason = result.inactiveReason()
            facade = Facade(name, bool(result.active()), str(inactive_reason) if inactive_reason is not None else None, members)
        self._cache[name] = facade
        return facade

    def facade(self, name: Optional[str]) -> Optional[Facade]:
        """The facade of a module name when it is one and resolves, else None."""
        facade = self.get(name)
        return facade if isinstance(facade, Facade) else None


def python_module_of(java_package: str) -> str:
    """The Python module a Java package imports as: without ``io.`` for Micronaut, keyword-safe."""
    name = java_package[3:] if java_package.startswith('io.micronaut.') else java_package
    return '.'.join(f'{part}_' if keyword.iskeyword(part) else part for part in name.split('.'))


def import_of_type(binary_name: str, local_name: str) -> ast.ImportFrom:
    """``from <package> import <Type> as <local>`` for a Java type; a nested type imports from its outer type."""
    outer, _, nested = binary_name.partition('$')
    package, _, simple_name = outer.rpartition('.')
    module = python_module_of(package)
    if nested:
        module = f'{module}.{simple_name}'
        simple_name = nested.split('$')[-1]
    return ast.ImportFrom(
        module=module,
        names=[ast.alias(name=simple_name, asname=None if local_name == simple_name else local_name)],
        level=0
    )


def simple_name_of(binary_name: str) -> str:
    return binary_name.rsplit('.', 1)[-1].split('$')[-1]


def module_bound_names(tree: ast.Module) -> Set[str]:
    """Every name the module binds somewhere, so a generated alias never shadows one."""
    names = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Name) and isinstance(node.ctx, (ast.Store, ast.Del)):
            names.add(node.id)
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names.add(node.name)
        elif isinstance(node, ast.arg):
            names.add(node.arg)
        elif isinstance(node, (ast.Import, ast.ImportFrom)):
            for alias in node.names:
                if alias.name != '*':
                    names.add(alias.asname or alias.name.split('.')[0])
    return names


class FacadeImportRewriter:
    """
    Rewrites the facade imports and references of a module's compile-time tree to Java imports and references.

    * ``from pyronaut.http import Get, HttpResponse`` becomes ``from micronaut.http.annotation import Get`` and
      ``from micronaut.http import HttpResponse``; a star import imports every member.
    * ``from pyronaut import http``, ``import pyronaut.http as http`` and ``from pyronaut.http import client``
      bind a facade: ``http.Get`` becomes a reference to an imported ``Get``, ``http.HttpStatus.OK`` to the
      imported ``HttpStatus``'s ``OK``, and ``import pyronaut.http`` lets ``pyronaut.http.Get`` resolve likewise.
    * A static method or an enum constant (``http.ok``, ``CREATED``) becomes an attribute of its imported
      declaring type, so ``@http.Status(http.CREATED)`` carries the enum value.

    Unknown members, inactive facades and a bare ``import pyronaut.http`` used as ``http`` are reported.
    """

    def __init__(self, facades: FacadeRegistry):
        self.facades = facades
        self.validation_errors: List[str] = []
        # the facades the module imports, nested ones included: the run time serves them
        self.imported_facades: List[str] = []
        # bound name -> facade name, for a name bound to a facade (``http``) or to a namespace (``pyronaut``)
        self.module_bindings: Dict[str, str] = {}
        # bound name -> member, for a static method or an enum constant imported by name
        self.member_bindings: Dict[str, FacadeMember] = {}
        # the last segment of ``import pyronaut.http`` -> the facade, to report ``http.Get`` without a binding
        self.unbound_facade_names: Dict[str, str] = {}
        # binary name of a Java type -> the local name a reference to it uses
        self.type_locals: Dict[str, str] = {}
        self.generated_imports: List[ast.stmt] = []
        self.bound_names: Set[str] = set()
        self.star_imports_java = False

    # ----------------------------------------------------------------------------------------------------
    # entry point

    def rewrite(self, tree: ast.Module) -> ast.Module:
        if not self._imports_facades(tree):
            return tree
        self.bound_names = module_bound_names(tree)
        _ImportPass(self).visit(tree)
        _ReferencePass(self).visit(tree)
        if self.generated_imports:
            insert_at = 0
            for index, statement in enumerate(tree.body):
                if index == 0 and isinstance(statement, ast.Expr) and isinstance(getattr(statement, 'value', None), ast.Constant) \
                        and isinstance(statement.value.value, str):
                    insert_at = 1
                elif isinstance(statement, ast.ImportFrom) and statement.module == '__future__':
                    insert_at = index + 1
            tree.body[insert_at:insert_at] = self.generated_imports
        ast.fix_missing_locations(tree)
        return tree

    def _imports_facades(self, tree: ast.Module) -> bool:
        """Whether the module imports a facade at all, so modules without one are left untouched cheaply."""
        found = False
        for node in ast.walk(tree):
            if isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
                if node.module == '__future__':
                    continue
                if any(alias.name == '*' for alias in node.names) and self.facades.get(node.module) is None:
                    self.star_imports_java = True
                if self.facades.get(node.module) is not None:
                    found = True
                elif any(self.facades.get(f'{node.module}.{alias.name}') is not None for alias in node.names if alias.name != '*'):
                    found = True
            elif isinstance(node, ast.Import):
                if any(self.facades.get(alias.name) is not None for alias in node.names):
                    found = True
        return found

    # ----------------------------------------------------------------------------------------------------
    # resolution

    def record_facade(self, facade: Facade):
        if facade.name in self.imported_facades:
            return
        self.imported_facades.append(facade.name)
        for nested in facade.nested_modules():
            nested_facade = self.facades.facade(nested.binary_name)
            if nested_facade is not None and nested_facade.active:
                self.record_facade(nested_facade)

    def usable_facade(self, name: str, statement: str) -> Optional[Facade]:
        """The facade a statement imports, reporting one that fails to resolve or whose sources are missing."""
        facade = self.facades.get(name)
        if isinstance(facade, str):
            self.validation_errors.append(f"Cannot import [{name}] in [{statement}]: {facade}")
            return None
        if facade is None:
            return None
        if not facade.active:
            self.validation_errors.append(f"Cannot import [{name}] in [{statement}]: {facade.inactive_reason}")
            return None
        self.record_facade(facade)
        return facade

    def unknown_member(self, facade: Facade, name: str) -> str:
        message = f"Python module [{facade.name}] has no member [{name}]"
        # imported on first use: the transformer module loads from its bytecode cache, without compiling
        import difflib
        suggestions = difflib.get_close_matches(name, list(facade.members), n=3)
        if suggestions:
            message += ". Did you mean " + ", ".join(f"[{s}]" for s in suggestions) + "?"
        return message

    def local_for_type(self, binary_name: str, preferred: Optional[str] = None) -> str:
        """The local name a reference to a Java type uses, importing the type the first time."""
        local = self.type_locals.get(binary_name)
        if local is not None:
            return local
        simple_name = simple_name_of(binary_name)
        candidates = [preferred] if preferred else []
        if not self.star_imports_java and simple_name not in self.bound_names:
            candidates.append(simple_name)
        candidates.append(GENERATED_PREFIX + simple_name)
        taken = set(self.type_locals.values())
        local = next((c for c in candidates if c not in taken), None)
        counter = 2
        while local is None:
            candidate = f'{GENERATED_PREFIX}{simple_name}_{counter}'
            if candidate not in taken and candidate not in self.bound_names:
                local = candidate
            counter += 1
        self.type_locals[binary_name] = local
        self.generated_imports.append(import_of_type(binary_name, local))
        return local

    def reference_to(self, member: FacadeMember) -> ast.expr:
        """The expression standing for a type, static method or enum constant member."""
        if member.kind in TYPE_KINDS:
            return ast.Name(id=self.local_for_type(member.binary_name), ctx=ast.Load())
        owner = ast.Name(id=self.local_for_type(member.binary_name), ctx=ast.Load())
        return ast.Attribute(value=owner, attr=member.member_name, ctx=ast.Load())


class _ImportPass(ast.NodeTransformer):
    """Rewrites the facade imports, recording what each binds."""

    def __init__(self, rewriter: FacadeImportRewriter):
        self.rewriter = rewriter

    def visit_ImportFrom(self, node: ast.ImportFrom):
        if node.level != 0 or not node.module or node.module == '__future__':
            return node
        rewriter = self.rewriter
        statement = f"from {node.module} import {', '.join(a.name for a in node.names)}"
        facade_or_error = rewriter.facades.get(node.module)
        if facade_or_error is not None:
            facade = rewriter.usable_facade(node.module, statement)
            if facade is None:
                return None
            replacements = []
            for alias in node.names:
                if alias.name == '*':
                    for member in facade.members.values():
                        replacements.extend(self._bind(member.name, member, node))
                    continue
                member = facade.members.get(alias.name)
                if member is None:
                    rewriter.validation_errors.append(f"Cannot import [{alias.name}] from [{node.module}]: {rewriter.unknown_member(facade, alias.name)}")
                    continue
                replacements.extend(self._bind(alias.asname or alias.name, member, node))
            return replacements or None
        # from pyronaut import http: the module of a facade imported by name
        kept = []
        for alias in node.names:
            full_name = f'{node.module}.{alias.name}'
            if alias.name != '*' and rewriter.facades.get(full_name) is not None:
                facade = rewriter.usable_facade(full_name, statement)
                if facade is not None:
                    rewriter.module_bindings[alias.asname or alias.name] = facade.name
            else:
                kept.append(alias)
        if len(kept) == len(node.names):
            return node
        if not kept:
            return None
        return ast.copy_location(ast.ImportFrom(module=node.module, names=kept, level=0), node)

    def visit_Import(self, node: ast.Import):
        rewriter = self.rewriter
        kept = []
        for alias in node.names:
            if rewriter.facades.get(alias.name) is None:
                kept.append(alias)
                continue
            facade = rewriter.usable_facade(alias.name, f"import {alias.name}")
            if facade is None:
                continue
            if alias.asname:
                rewriter.module_bindings[alias.asname] = facade.name
            else:
                # import pyronaut.http binds pyronaut, whose attributes lead to the facade
                root = alias.name.split('.')[0]
                rewriter.module_bindings.setdefault(root, root)
                rewriter.unbound_facade_names[alias.name.rsplit('.', 1)[-1]] = alias.name
        if len(kept) == len(node.names):
            return node
        if not kept:
            return None
        return ast.copy_location(ast.Import(names=kept), node)

    def _bind(self, local_name: str, member: FacadeMember, node: ast.AST) -> List[ast.stmt]:
        rewriter = self.rewriter
        if member.kind in TYPE_KINDS:
            rewriter.type_locals.setdefault(member.binary_name, local_name)
            return [ast.copy_location(import_of_type(member.binary_name, local_name), node)]
        if member.kind == 'MODULE':
            facade = rewriter.facades.facade(member.binary_name)
            if facade is not None:
                rewriter.module_bindings[local_name] = facade.name
            return []
        # a static method or enum constant: references become attributes of the imported declaring type
        rewriter.member_bindings[local_name] = member
        return []


class _ReferencePass(ast.NodeTransformer):
    """Rewrites the references to facade members."""

    def __init__(self, rewriter: FacadeImportRewriter):
        self.rewriter = rewriter

    def visit_Name(self, node: ast.Name):
        if isinstance(node.ctx, ast.Load):
            member = self.rewriter.member_bindings.get(node.id)
            if member is not None:
                return ast.copy_location(self.rewriter.reference_to(member), node)
        return node

    def visit_Attribute(self, node: ast.Attribute):
        if not isinstance(node.ctx, ast.Load):
            return self.generic_visit(node)
        chain = []
        current = node
        while isinstance(current, ast.Attribute):
            chain.insert(0, current.attr)
            current = current.value
        if not isinstance(current, ast.Name):
            return self.generic_visit(node)
        rewriter = self.rewriter
        root = current.id
        bound = rewriter.module_bindings.get(root)
        if bound is None:
            if root in rewriter.unbound_facade_names and root not in rewriter.bound_names:
                facade_name = rewriter.unbound_facade_names[root]
                rewriter.validation_errors.append(
                    f"[import {facade_name}] binds the name [{facade_name.split('.')[0]}], not [{root}]: "
                    f"write [from {facade_name.rsplit('.', 1)[0]} import {root}] to use [{root}.{chain[0]}]")
                rewriter.unbound_facade_names.pop(root)
            return self.generic_visit(node)
        replacement = self._resolve(bound, chain, node)
        return replacement if replacement is not None else node

    def _resolve(self, module_name: str, chain: List[str], node: ast.Attribute) -> Optional[ast.expr]:
        rewriter = self.rewriter
        index = 0
        facade = rewriter.facades.facade(module_name)
        # a namespace binding (import pyronaut.http binds pyronaut): walk to the facade
        while facade is None and index < len(chain):
            module_name = f'{module_name}.{chain[index]}'
            index += 1
            facade = rewriter.facades.facade(module_name)
        if facade is None or index >= len(chain):
            return None
        while index < len(chain):
            if chain[index].startswith('__') and chain[index].endswith('__'):
                # a module attribute (http.__all__, http.__doc__): resolved by the module at run time
                return None
            member = facade.members.get(chain[index])
            if member is None:
                rewriter.validation_errors.append(rewriter.unknown_member(facade, chain[index]))
                return None
            index += 1
            if member.kind != 'MODULE':
                expression = rewriter.reference_to(member)
                for attribute in chain[index:]:
                    expression = ast.Attribute(value=expression, attr=attribute, ctx=ast.Load())
                return ast.copy_location(expression, node)
            nested = rewriter.facades.facade(member.binary_name)
            if nested is None:
                return None
            facade = nested
        # the chain names a facade itself (x = http.client): left as written
        return None


def runtime_facade_annotation_names(facades: FacadeRegistry, facade_name: str, prefix: str, into: Dict[str, Set[str]]):
    """
    The annotation names of a facade bound at run time under ``prefix`` (``http``), and of its nested facades
    under ``prefix.name``: the runtime transformer applies them as ``@http.Get()`` when written bare.
    """
    facade = facades.facade(facade_name)
    if facade is None or not facade.active:
        return
    names = facade.annotation_names()
    if names:
        into.setdefault(prefix, set()).update(names)
    for nested in facade.nested_modules():
        runtime_facade_annotation_names(facades, nested.binary_name, f'{prefix}.{nested.name}', into)
