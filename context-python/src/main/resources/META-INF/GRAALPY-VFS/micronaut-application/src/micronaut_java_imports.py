"""
The Java packages, types and annotations the compiled Python sources import, served as modules.

A Python module importing a Java class (``from micronaut.context import ApplicationContext``) or a Java
annotation (``from jakarta.inject import Singleton``) runs unchanged at run time, so the Java packages it
imports must be importable as Python modules. No module file exists for them: the compiler records the
Java packages, types and annotations every module imports in a manifest
(``__micronaut_java_imports_<hash>.py`` next to the sources of its compilation), and the meta path finder
installed here serves them from the manifests of every compilation on the path. A package is a module
object without a file whose attributes resolve to host classes on first access; an annotation is a
callable that carries the annotation type and returns its target, as the compiler-generated decorators
did; a class the run time class path lacks is a facade resolving it on first use.

Loaded by PythonContextRuntime before the application modules import, and imported by the package
initialisers of the application packages that share their name with a Java package. Kept apart from
micronaut_runtime, whose import (asyncio and the bridge helpers) is deferred to the first bridge call.
"""
import importlib
import importlib.machinery as _micronaut_importlib_machinery
import keyword
import os as _micronaut_os
import sys
import threading as _micronaut_threading
import types as _micronaut_types

_MICRONAUT_JAVA_IMPORTS_MANIFEST_PREFIX = "__micronaut_java_imports_"
_MICRONAUT_JAVA_IMPORTS_MANIFEST_SUFFIX = ".py"


class _MicronautJavaType:
    """A Java class absent from the runtime class path, resolved on first use."""

    def __init__(self, target, interface=False):
        self._target = target
        self._interface = interface

    def _resolved(self):
        if isinstance(self._target, str):
            import java
            self._target = java.type(self._target)
        return self._target

    def __getattr__(self, name):
        if name.endswith('_') and keyword.iskeyword(name[:-1]):
            name = name[:-1]
        return getattr(self._resolved(), name)

    def __call__(self, *args, **kwargs):
        return self._resolved()(*args, **kwargs)

    def __getitem__(self, item):
        if self._interface:
            return self
        return self._resolved()[item]

    def __mro_entries__(self, bases):
        if self._interface:
            return ()
        return (self._resolved(),)

    def __repr__(self):
        return f"<Java type {self._target if isinstance(self._target, str) else self._target.__name__}>"


def _micronaut_host_class(name):
    """The host class of a binary name, or None when the run time class path lacks it."""
    import java
    try:
        return java.type(name)
    except Exception:
        return None


class _MicronautJavaAnnotation:
    """
    The decorator standing for a Java annotation type at run time. Every argument is an annotation value
    (the compiler rewrote a bare ``@Ann`` to ``@Ann()``), so it returns the decorator applying it, which
    returns its target unchanged: the annotation metadata was compiled into the generated Java class.
    ``java_class`` is the annotation type when it is on the class path, and ``java_class_name`` is
    always its name, which the Java side loads when a decorator is passed where a ``Class`` is expected.
    A nested type of the annotation (``Requires.Sdk``) is an attribute of the decorator.
    """

    def __init__(self, name, host_class, value_holds_class=None):
        self.java_class_name = name
        self.java_class = getattr(host_class, 'class') if host_class is not None else None
        self.__name__ = name.rsplit('.', 1)[-1].split('$')[-1]
        self.__qualname__ = self.__name__
        self.__module__ = _micronaut_python_module_name(name.rsplit('.', 1)[0]) if '.' in name else ''
        self.__doc__ = f"Micronaut annotation decorator for {name}."
        # whether the value member holds a class: recorded by the compiler (the annotation may be absent at
        # run time), else found by reflection on first need
        self._value_holds_class = value_holds_class

    def __call__(self, *args, **kwargs):
        if len(args) == 1 and not kwargs and (isinstance(args[0], type) or (
                hasattr(args[0], '__code__') and not getattr(args[0], '__qualname__', '').endswith('.decorator'))):
            # applied bare through a name the compiler did not recognise as the annotation (``Bean = Singleton``
            # in another module, ``getattr``): the target would silently become the inner decorator, unless
            # the annotation's value member holds a class. A nested annotation value, the inner decorator of
            # another factory, is not a target.
            if not self._value_member_holds_class():
                raise TypeError(
                    f"@{self.__name__} was applied bare to {args[0]!r} through a name the compiler did not recognise "
                    f"as the annotation; write @{self.__name__}() or apply it under the name it is imported as")

        def decorator(target):
            return target

        return decorator

    def _value_member_holds_class(self):
        if self._value_holds_class is None:
            holds_class = False
            if self.java_class is not None:
                try:
                    for method in self.java_class.getDeclaredMethods():
                        if method.getName() == 'value':
                            return_type = method.getReturnType()
                            if return_type.isArray():
                                return_type = return_type.getComponentType()
                            holds_class = return_type.getName() == 'java.lang.Class'
                            break
                except Exception:
                    holds_class = False
            self._value_holds_class = holds_class
        return self._value_holds_class

    def __getattr__(self, name):
        if name.startswith('__') or name.startswith('_'):
            raise AttributeError(name)
        member = _micronaut_java_type_member(self.java_class_name, name)
        if member is None:
            raise AttributeError(f"annotation '{self.java_class_name}' has no nested type '{name}'")
        setattr(self, name, member)
        return member

    def __repr__(self):
        return f"<Java annotation {self.java_class_name}>"


_micronaut_java_annotations = {}
_micronaut_java_annotations_lock = _micronaut_threading.Lock()


def _micronaut_java_annotation(name, host_class=None, value_holds_class=None):
    """The one decorator of an annotation type, by binary name."""
    decorator = _micronaut_java_annotations.get(name)
    if decorator is None:
        with _micronaut_java_annotations_lock:
            decorator = _micronaut_java_annotations.get(name)
            if decorator is None:
                decorator = _MicronautJavaAnnotation(name, host_class if host_class is not None else _micronaut_host_class(name), value_holds_class)
                _micronaut_java_annotations[name] = decorator
    return decorator


def _micronaut_java_member(name, kind=None, flags=()):
    """
    The Python value of a Java type: the decorator of an annotation, the host class of any other type,
    or the facade of a type absent from the run time class path. ``kind`` is what the compiler recorded
    ('annotation', 'interface' or 'class'); None when the type is looked up beyond the manifest, in which
    case a type absent at run time is None.
    """
    host_class = _micronaut_host_class(name)
    if kind is None:
        if host_class is None:
            return None
        java_class = getattr(host_class, 'class')
        kind = 'annotation' if java_class.isAnnotation() else 'interface' if java_class.isInterface() else 'class'
    if kind == 'annotation':
        return _micronaut_java_annotation(name, host_class, True if 'class-value' in flags else None)
    if host_class is None:
        return _MicronautJavaType(name, kind == 'interface')
    return host_class


def _micronaut_java_type_member(binary_name, name):
    """A nested type of a Java type: recorded by the compiler or on the class path, else None."""
    recorded = _micronaut_java_imports().members.get(_micronaut_python_module_name(binary_name), {}).get(name)
    if recorded is not None:
        return _micronaut_java_member(recorded[0], recorded[1], recorded[2:])
    return _micronaut_java_member(binary_name + '$' + name)


def _micronaut_python_module_name(java_name):
    """The Python module name of a Java package or type: keyword-safe, nested types as sub-modules, without ``io.``."""
    name = java_name[3:] if java_name.startswith('io.') else java_name
    return '.'.join(f'{part}_' if keyword.iskeyword(part) else part for part in name.replace('$', '.').split('.'))


class _MicronautJavaImports:
    """The Java packages, types and their members the compilations on the path import, merged."""

    def __init__(self):
        # Python module name -> Java package name
        self.packages = {}
        # Python module name -> binary name of the Java type it stands for (from a.b.Outer import Inner)
        self.types = {}
        # Python module name -> simple name -> (binary name, kind)
        self.members = {}
        self.manifests = []

    def merge(self, manifest):
        for python_name, java_name in manifest.get('PACKAGES', {}).items():
            self.packages.setdefault(python_name, java_name)
        for python_name, java_name in manifest.get('TYPES', {}).items():
            self.types.setdefault(python_name, java_name)
        for python_name, members in manifest.get('MEMBERS', {}).items():
            merged = self.members.setdefault(python_name, {})
            for simple_name, member in members.items():
                # the first compilation defining a name wins, unless it lacks the class a later one has
                existing = merged.get(simple_name)
                if existing is None or (existing[1] != 'annotation' and _micronaut_host_class(existing[0]) is None):
                    merged[simple_name] = tuple(member)

    def java_name(self, python_name):
        return self.types.get(python_name) or self.packages.get(python_name)

    def resolve_type(self, python_name):
        """
        The Java type a module name not recorded as a module stands for: a member of a recorded module
        imported as a module itself (from jakarta.inject.Qualifier import Qualifier, as the compiler-generated
        code imports a meta-annotation), or a class of a recorded package on the class path. Remembered as a
        type module once found; None when the name is no Java type.
        """
        parent, _, name = python_name.rpartition('.')
        if not parent:
            return None
        parent_java_name = self.java_name(parent)
        if parent_java_name is None:
            return None
        recorded = self.members.get(parent, {}).get(name)
        if recorded is not None:
            java_name = recorded[0]
        else:
            java_name = parent_java_name + ('$' if parent in self.types else '.') + name
            if _micronaut_host_class(java_name) is None:
                return None
        self.types[python_name] = java_name
        return java_name

    def subpackages(self, python_name):
        prefix = python_name + '.'
        return sorted({
            name[len(prefix):]
            for name in [*self.packages, *self.types]
            if name.startswith(prefix) and '.' not in name[len(prefix):]
        })


_micronaut_java_imports_cache = None
_micronaut_java_imports_lock = _micronaut_threading.Lock()


def _micronaut_java_imports():
    """The merged manifests of the path entries, read once per context on the first Java import."""
    global _micronaut_java_imports_cache
    imports = _micronaut_java_imports_cache
    if imports is None:
        with _micronaut_java_imports_lock:
            imports = _micronaut_java_imports_cache
            if imports is None:
                imports = _MicronautJavaImports()
                for entry in list(sys.path):
                    try:
                        files = _micronaut_os.listdir(entry) if entry else []
                    except OSError:
                        continue
                    for file in sorted(files):
                        if file.startswith(_MICRONAUT_JAVA_IMPORTS_MANIFEST_PREFIX) and file.endswith(_MICRONAUT_JAVA_IMPORTS_MANIFEST_SUFFIX):
                            # a module of dict literals, run in a namespace of its own like a members module: the
                            # loader uses the bytecode cache the compiler may have written next to it
                            path = _micronaut_os.path.join(entry, file)
                            name = file[:-3]
                            namespace = {'__name__': name, '__file__': path}
                            exec(_micronaut_importlib_machinery.SourceFileLoader(name, path).get_code(name), namespace)
                            imports.merge(namespace)
                            imports.manifests.append(path)
                _micronaut_java_imports_cache = imports
    return imports


def __micronaut_reset_java_imports():
    """Forget the merged manifests, so the next Java import reads them again (after the path changed)."""
    global _micronaut_java_imports_cache
    with _micronaut_java_imports_lock:
        _micronaut_java_imports_cache = None


class _MicronautJavaAwareModule(_micronaut_types.ModuleType):
    """
    A module the Java types of a Java package are imported through: a Java package served from the
    manifests, or an application package sharing its name with one. The import system binds an imported
    sub-module on its package; for the module of a Java type (import a.b.Outer, from a.b.Outer import
    Inner) the package keeps the type itself under its name.
    """

    def __setattr__(self, name, value):
        if isinstance(value, _MicronautJavaPackage):
            java_type = value.__dict__.get('__micronaut_java_type__')
            if java_type is not None:
                value = java_type
        super().__setattr__(name, value)


class _MicronautJavaPackage(_MicronautJavaAwareModule):
    """
    A Java package, or a Java type whose nested types are imported from it, as a module. Its members
    resolve on first access: a sub-package or a nested type imports as a module, a class binds to the
    host class, an annotation to its decorator. ``__all__`` lists the members the compiler recorded, then
    the direct sub-packages, so a star import binds them as it did when the packages were generated files.
    """

    def __getattr__(self, name):
        if name == '__all__':
            return self._micronaut_all()
        if name.startswith('__'):
            raise AttributeError(name)
        value = _micronaut_java_package_member(self.__name__, name)
        if value is None:
            raise AttributeError(f"module '{self.__name__}' has no attribute '{name}'")
        return value

    def __dir__(self):
        return sorted({*self.__dict__, *self._micronaut_all()})

    def _micronaut_all(self):
        return _micronaut_java_package_names(self.__name__, self.__dict__.get('__micronaut_java_type_name__'))


def _micronaut_java_package_names(module_name, type_name=None):
    """The names a Java package exports: the recorded members, the type of a type module, then the direct sub-packages."""
    imports = _micronaut_java_imports()
    names = [name for name in imports.members.get(module_name, {}) if not name.startswith('_')]
    if type_name is not None:
        simple_name = type_name.rsplit('.', 1)[-1].split('$')[-1]
        if simple_name not in names:
            names.insert(0, simple_name)
    names.extend(name for name in imports.subpackages(module_name) if name not in names)
    return names


def __micronaut_java_package_initialised(module):
    """
    Completes the initialiser of an application package that shares its name with a Java package: the
    Java members and sub-packages join ``__all__``, so a star import binds them through the initialiser's
    ``__getattr__`` fallback, and the module keeps a Java type on its name when the type's module is imported.
    """
    if _micronaut_java_imports().java_name(module.__name__) is None:
        return
    exported = module.__dict__.setdefault('__all__', [])
    for name in _micronaut_java_package_names(module.__name__):
        if name not in exported:
            exported.append(name)
    if not isinstance(module, _MicronautJavaAwareModule):
        module.__class__ = _MicronautJavaAwareModule


def _micronaut_java_package_member(module_name, name):
    """
    A member of a Java package module by its Python module name: the sub-package or nested type
    module (imported and bound on the package by the import), the class or annotation the compiler
    recorded or the class path has, else None. Serves the package modules and the initialisers of the
    application packages that share their name with a Java package.
    """
    imports = _micronaut_java_imports()
    java_name = imports.java_name(module_name)
    if java_name is None:
        return None
    type_name = imports.types.get(module_name)
    if type_name is not None and name == type_name.rsplit('.', 1)[-1].split('$')[-1]:
        # from a.b.Outer import Outer: the module of a type exports the type itself
        recorded = imports.members.get(module_name.rsplit('.', 1)[0], {}).get(name)
        return _micronaut_java_member(type_name, recorded[1] if recorded is not None else None) or _MicronautJavaType(type_name)
    recorded = imports.members.get(module_name, {}).get(name)
    if recorded is not None:
        return _micronaut_java_member(recorded[0], recorded[1], recorded[2:])
    child = module_name + '.' + name
    if child in imports.packages or child in imports.types:
        module = importlib.import_module(child)
        # the module of a type stands for the type on its package (from a.b import Outer)
        return module.__dict__.get('__micronaut_java_type__', module)
    separator = '$' if type_name is not None else '.'
    member = _micronaut_java_member(java_name + separator + name)
    if member is None and type_name is not None:
        host_class = _micronaut_host_class(type_name)
        member = getattr(host_class, name, None) if host_class is not None else None
    if member is None and type_name is None:
        # a sub-package the sources do not import (java.util.concurrent): GraalPy's own finder serves the
        # packages it knows
        try:
            member = importlib.import_module(child)
        except ImportError:
            member = None
    return member


class _MicronautJavaImportFinder:
    """
    Serves the Java packages and types the manifests record as modules. An application package of the
    same name (a directory with an initialiser on the path) is left to the path finder: its generated
    initialiser falls back to the Java members. A directory without an initialiser becomes part of the
    Java package's path, so the Python modules in it import as usual.
    """

    def find_spec(self, fullname, path=None, target=None):
        imports = _micronaut_java_imports()
        java_name = imports.java_name(fullname)
        if java_name is None:
            java_name = imports.resolve_type(fullname)
            if java_name is None:
                return None
        directories = []
        segment = fullname.rsplit('.', 1)[-1]
        for entry in (path if path is not None else sys.path):
            if not isinstance(entry, str):
                continue
            directory = _micronaut_os.path.join(entry, segment) if entry else segment
            if _micronaut_os.path.isdir(directory):
                if _micronaut_os.path.isfile(_micronaut_os.path.join(directory, '__init__.py')):
                    return None
                directories.append(directory)
        spec = _micronaut_importlib_machinery.ModuleSpec(fullname, self, origin='java:' + java_name, is_package=True)
        spec.submodule_search_locations = directories
        return spec

    def create_module(self, spec):
        module = _MicronautJavaPackage(spec.name, f"Java {'type' if spec.name in _micronaut_java_imports().types else 'package'} {spec.origin[5:]}")
        type_name = _micronaut_java_imports().types.get(spec.name)
        if type_name is not None:
            module.__dict__['__micronaut_java_type_name__'] = type_name
            recorded = _micronaut_java_imports().members.get(spec.name.rsplit('.', 1)[0], {}).get(type_name.rsplit('.', 1)[-1].split('$')[-1])
            module.__dict__['__micronaut_java_type__'] = _micronaut_java_member(type_name, recorded[1] if recorded is not None else None) or _MicronautJavaType(type_name)
        return module

    def exec_module(self, module):
        pass


def __micronaut_install_java_import_finder():
    """Installs the Java import finder ahead of the path finder, once per context."""
    for finder in sys.meta_path:
        if isinstance(finder, _MicronautJavaImportFinder):
            return
    index = len(sys.meta_path)
    for i, finder in enumerate(sys.meta_path):
        if getattr(finder, '__name__', None) == 'PathFinder' or type(finder).__name__ == 'PathFinder':
            index = i
            break
    sys.meta_path.insert(index, _MicronautJavaImportFinder())


__micronaut_install_java_import_finder()

# the helpers the Java side and the generated package initialisers look up by name
__micronaut_java_package_member = _micronaut_java_package_member
__micronaut_java_package_names = _micronaut_java_package_names
__micronaut_java_annotation = _micronaut_java_annotation
__micronaut_java_imports = _micronaut_java_imports
