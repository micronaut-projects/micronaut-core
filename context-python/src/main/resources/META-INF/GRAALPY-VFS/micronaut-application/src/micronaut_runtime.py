"""
Python-side helpers of the Micronaut GraalPy runtime.

Loaded once per context by PythonContextRuntime and looked up by name; each function is a small piece of
Python semantics that host interop does not provide (object.__new__ without __init__, descriptor binding,
datetime construction, async member adaptation).
"""
import asyncio
import datetime
import importlib
import inspect
import keyword
import pkgutil
import sys
import uuid


def __micronaut_new_uninitialized_instance(cls):
    return object.__new__(cls)


def __micronaut_set_instance_property(instance, name, value):
    object.__setattr__(instance, name, value)
    return instance


def __micronaut_set_instance_properties(instance, names, values):
    for i in range(len(names)):
        setattr(instance, names[i], values[i])
    return instance


def __micronaut_find_class_in_package_modules(package_name, class_name):
    package = importlib.import_module(package_name)
    package_path = getattr(package, "__path__", None)
    if package_path is None:
        return None
    for module_info in pkgutil.iter_modules(package_path):
        try:
            module = importlib.import_module(package_name + "." + module_info.name)
        except Exception:
            continue
        member = getattr(module, class_name, None)
        if inspect.isclass(member):
            return member
    return None


__micronaut_inspect_isclass = inspect.isclass


__micronaut_import_module = importlib.import_module


def __micronaut_loaded_module(name):
    """The imported and initialized module, or None: not imported, or another thread is executing it."""
    module = sys.modules.get(name)
    if module is None:
        return None
    spec = getattr(module, "__spec__", None)
    if spec is not None and getattr(spec, "_initializing", False):
        return None
    return module


def __micronaut_put_member(target, name, value):
    setattr(target, name, value)


def __micronaut_python_list(items):
    return list(items)


def __micronaut_python_dict(keys, values):
    return dict(zip(keys, values))


def __micronaut_to_python_standard_type(kind, value, nanos=0):
    if kind == "date":
        return datetime.date.fromisoformat(value)
    if kind == "time":
        return datetime.time.fromisoformat(value)
    if kind == "datetime":
        return datetime.datetime.fromisoformat(value)
    if kind == "duration":
        return datetime.timedelta(seconds=value, microseconds=nanos // 1000)
    if kind == "zone_offset":
        if value == "Z":
            return datetime.timezone.utc
        return datetime.time.fromisoformat("00:00:00" + value).tzinfo
    if kind == "uuid":
        return uuid.UUID(value)
    raise ValueError("Unsupported Micronaut Python standard type: " + kind)


def _micronaut_reactive_context():
    # the reactive context of the running coroutine lives in the asyncio bridge module; a coroutine
    # that awaits Java values was scheduled through that module, so it is loaded whenever one exists
    asyncio_bridge = sys.modules.get("micronaut_asyncio")
    if asyncio_bridge is None:
        return None
    return asyncio_bridge.__micronaut_current_reactive_context()


def __micronaut_async_member_value(target, adapter, context):
    def adapt(value):
        try:
            if inspect.isawaitable(value) or asyncio.isfuture(value):
                return value
        except Exception:
            pass
        adapted = adapter.adaptAwaitable(context, value, _micronaut_reactive_context())
        if adapted is not None:
            return adapted
        return value

    class _MicronautAsyncMember:
        def __init__(self, target, adapter, context):
            self._target = target
            self._adapter = adapter
            self._context = context

        def __getattr__(self, name):
            member = getattr(self._target, name)
            if callable(member):
                def invoke(*args, **kwargs):
                    return adapt(member(*args, **kwargs))
                return invoke
            return adapt(member)

    return _MicronautAsyncMember(target, adapter, context)


def __micronaut_has_coroutine_methods(cls):
    for base in getattr(cls, "__mro__", (cls,)):
        if base is object:
            continue
        for member in vars(base).values():
            if isinstance(member, (staticmethod, classmethod)):
                member = member.__func__
            if inspect.iscoroutinefunction(member):
                return True
    return False


def __micronaut_is_plain_bean_instance(obj, qualname):
    """Whether obj is an instance of the bean class itself, not an introduction or a scoped proxy."""
    cls = type(obj)
    if cls.__qualname__ != qualname:
        return False
    return not cls.__dict__.get("__micronaut_introduction__", False) and not inspect.isabstract(cls)


def __micronaut_transferable_member_names(obj):
    try:
        return [name for name in vars(obj).keys() if not name.startswith("__micronaut_")]
    except TypeError:
        return []


def __micronaut_utc_offset(value):
    return value.utcoffset(None)


def __micronaut_invoke_method(receiver, name, arguments):
    member = getattr(receiver, name, None)
    if callable(member):
        return member(*arguments)
    cls = getattr(receiver, "__class__", None)
    if cls is not None:
        for base in getattr(cls, "__mro__", (cls,)):
            namespace = getattr(base, "__dict__", {})
            if name in namespace:
                raw_member = namespace[name]
                getter = getattr(raw_member, "__get__", None)
                if getter is not None:
                    raw_member = getter(receiver, cls)
                if callable(raw_member):
                    return raw_member(*arguments)
                break
    if member is None:
        raise AttributeError(name)
    return member(*arguments)


def __micronaut_get_raw_class_member(cls, name):
    for base in getattr(cls, "__mro__", (cls,)):
        namespace = getattr(base, "__dict__", {})
        if name in namespace:
            return namespace[name]
    return None


def __micronaut_prepare_introduction(cls):
    """Make an abstract class or Protocol instantiable for a Micronaut introduction.

    Every abstract method gets a stub that the Java proxy replaces; the class is
    marked so the work happens once per class and context.
    """
    if cls.__dict__.get("__micronaut_introduction__", False):
        # the marker is not inherited: a subclass that adds abstract methods is prepared on its own
        return cls
    from abc import update_abstractmethods
    for name in list(getattr(cls, "__abstractmethods__", ())):
        setattr(cls, name, lambda *args, **kwargs: None)
    update_abstractmethods(cls)
    if getattr(cls, "_is_protocol", False):
        cls._is_protocol = False
    cls.__micronaut_introduction__ = True
    return cls


def __micronaut_install_java_interface_defaults(cls, default_methods):
    """Add the default methods of the implemented Java interfaces that the class does not define.

    Each added method invokes the Java default implementation on the Java view of the instance
    (PythonInterfaceDefaults); a method the class or a Python base defines is left alone.
    """
    for default_method in default_methods:
        name = default_method.name()
        if keyword.iskeyword(name):
            # a Java method named like a Python keyword is called through its keyword-safe alias
            name = name + "_"
        if hasattr(cls, name):
            continue

        def method(self, *args, _default_method=default_method):
            return _default_method.invoke(self, args)

        method.__name__ = name
        method.__qualname__ = cls.__qualname__ + "." + name
        setattr(cls, name, method)


__micronaut_java_base_classes = {}


def __micronaut_java_base_class(java_class_name, method_names):
    """The Python base class standing in for a Java class a Python class extends.

    The generated Java class extends the Java class and is its only instance; this base records
    the arguments of super().__init__(...) for the Java super constructor and forwards every
    inherited Java method to that instance (PythonJavaBases.invoke, which creates the instance for
    an object constructed in Python code). One class per Java class and context.
    """
    cls = __micronaut_java_base_classes.get(java_class_name)
    if cls is not None:
        return cls
    import java
    import keyword
    invoker = java.type("io.micronaut.context.python.PythonJavaBases")

    def __init__(self, *args, **kwargs):
        if kwargs:
            raise TypeError(
                f"super().__init__() of a Python class extending the Java class {java_class_name} "
                "takes positional arguments only"
            )
        object.__setattr__(self, "__micronaut_super_args__", args)

    def base_method(name):
        def method(self, *args):
            return invoker.invoke(self, name, args)
        method.__name__ = name
        method.__qualname__ = java_class_name + "." + name
        return method

    package, _, simple_name = java_class_name.rpartition(".")
    namespace = {
        "__module__": package or "java",
        "__qualname__": simple_name.replace("$", "."),
        "__micronaut_java_base__": java_class_name,
        "__init__": __init__,
    }
    for name in method_names:
        method = base_method(name)
        namespace[name] = method
        if keyword.iskeyword(name):
            namespace[name + "_"] = method
    cls = type(simple_name.rpartition("$")[2], (object,), namespace)
    __micronaut_java_base_classes[java_class_name] = cls
    return cls


def __micronaut_create_raw_instance(cls):
    return cls.__new__(cls)


class _MicronautSelfInvocation:
    """Instance attribute that routes ``self.method(...)`` of a proxied bean through the interceptor chain.

    The override was created for the bean object that carries the attribute, so the chain runs on the
    calling object; it binds the class function to that object, so the attribute is never re-entered.
    Keyword and omitted defaulted arguments are laid out positionally the way the generated Java method
    declares them.
    """

    __slots__ = ("_function", "_override", "_parameters")

    def __init__(self, function, override, parameters):
        self._function = function
        self._override = override
        self._parameters = parameters

    def __call__(self, *args, **kwargs):
        parameters = self._parameters
        if kwargs or len(args) < len(parameters):
            values = list(args)
            for parameter in parameters[len(args):]:
                if parameter.name in kwargs:
                    values.append(kwargs.pop(parameter.name))
                elif parameter.default is not parameter.empty:
                    values.append(parameter.default)
                else:
                    raise TypeError(f"{self._function.__qualname__}() missing required argument: '{parameter.name}'")
            if kwargs:
                raise TypeError(f"{self._function.__qualname__}() got an unexpected keyword argument '{next(iter(kwargs))}'")
            args = values
        return self._override(*args)

    def __getattr__(self, name):
        if name in _MicronautSelfInvocation.__slots__:
            raise AttributeError(name)
        return getattr(self._function, name)

    def __repr__(self):
        return repr(self._function)


class _MicronautStageAwaitable:
    """Awaitable over the Java stage an intercepted ``async def`` produced.

    The interceptor chain of an async method sees a CompletionStage, as it does for a Java bean. A Python
    caller awaits this object, which turns the stage into an asyncio future on the loop that awaits it;
    the Java bridge reads the stage back from ``_micronaut_java_stage`` and hands it to a Java caller as
    it is.
    """

    __slots__ = ("_micronaut_java_stage", "_to_awaitable")

    def __init__(self, stage, to_awaitable):
        self._micronaut_java_stage = stage
        self._to_awaitable = to_awaitable

    def __await__(self):
        return self._to_awaitable().__await__()


# the parameter layout of a class function, computed once per function: a bean of a prototype-like scope
# is bound on every instantiation
_micronaut_self_invocation_layouts = {}


def _micronaut_self_invocation_layout(function):
    """The parameters after ``self``, or ``None`` when the layout cannot be mapped onto the Java method.

    A method taking ``*args`` or ``**kwargs`` has no positional layout, and the generated Java method
    has no parameter for a keyword-only one; the self-invocations of such methods stay direct rather
    than dropping or misplacing arguments.
    """
    try:
        return _micronaut_self_invocation_layouts[function]
    except KeyError:
        pass
    except TypeError:
        return None
    parameters = None
    try:
        parameters = tuple(inspect.signature(function).parameters.values())[1:]
        for parameter in parameters:
            if parameter.kind in (parameter.VAR_POSITIONAL, parameter.VAR_KEYWORD, parameter.KEYWORD_ONLY):
                parameters = None
                break
    except (TypeError, ValueError):
        pass
    _micronaut_self_invocation_layouts[function] = parameters
    return parameters


def __micronaut_is_coroutine_function(function):
    return inspect.iscoroutinefunction(function)


def __micronaut_await_stage(stage, to_awaitable):
    return _MicronautStageAwaitable(stage, to_awaitable)


def __micronaut_bind_self_invocations(target, names, overrides):
    """Make the intercepted methods of a proxied bean dispatch through the interceptor chain when called on ``self``.

    A Java bean is its own proxy, so ``this.method()`` from inside the bean runs the interceptor chain.
    A Python bean is a plain object behind the scoped proxy; an instance attribute per intercepted method
    gives ``self.method()`` the same semantics while ``self`` stays the bean object. ``overrides`` are the
    chains created for this target, one per name.
    """
    try:
        attributes = vars(target)
    except TypeError:
        # an object without __dict__ cannot carry the attributes; its self-invocations stay direct
        return
    cls = type(target)
    for i in range(len(names)):
        name = names[i]
        if isinstance(attributes.get(name), _MicronautSelfInvocation):
            # already bound: the same target resolved again
            continue
        function = __micronaut_get_raw_class_member(cls, name)
        if function is None or not callable(function):
            continue
        parameters = _micronaut_self_invocation_layout(function)
        if parameters is None:
            continue
        object.__setattr__(target, name, _MicronautSelfInvocation(function, overrides[i], parameters))


def __micronaut_create_scoped_proxy(cls, target_supplier, java_proxy_reference=None):
    """A subclass of cls that forwards every attribute to the bean the supplier returns.

    Method and setter overrides registered by the Java proxy creator run the interceptor chain
    before the target is reached. The proxy of an abstract class (a scoped proxy standing in for an
    implementation of it) is instantiable: every attribute is served by the target. The host object
    reference of the proxy (given here, or bound once the Java side exists) is the Java AOP proxy it
    stands in for, so the proxy, not the bean it currently resolves to, is what Java receives when
    Python returns it, without resolving the target.
    """
    class _MicronautScopedProxy(cls):
        def __init__(self, supplier, java_proxy):
            object.__setattr__(self, "_micronaut_target_supplier", supplier)
            object.__setattr__(self, "_micronaut_java_proxy", None)
            object.__setattr__(self, "_micronaut_overrides", {})
            object.__setattr__(self, "_micronaut_setter_overrides", {})
            if java_proxy is not None:
                object.__getattribute__(self, "_micronaut_bind_java_proxy")(java_proxy)

        def _micronaut_bind_java_proxy(self, java_proxy):
            object.__setattr__(self, "_micronaut_java_proxy", java_proxy)
            # visible to a plain member lookup as well as through __getattribute__
            object.__setattr__(self, "__micronaut_value_coercible_host__", java_proxy)

        def _micronaut_target(self):
            target = object.__getattribute__(self, "_micronaut_target_supplier")()
            object.__getattribute__(self, "_micronaut_sync_target_attributes")(target)
            return target

        def _micronaut_put_override(self, name, value):
            object.__getattribute__(self, "_micronaut_overrides")[name] = value

        def _micronaut_put_setter_override(self, name, value):
            object.__getattribute__(self, "_micronaut_setter_overrides")[name] = value

        def _micronaut_register_member(self, name):
            if isinstance(name, str) and not name.startswith("_"):
                object.__setattr__(self, name, None)

        def _micronaut_sync_target_attributes(self, target):
            try:
                attributes = getattr(target, "__dict__", {})
                names = attributes.keys()
            except Exception:
                return
            overrides = object.__getattribute__(self, "_micronaut_overrides")
            for name in names:
                if name not in overrides:
                    object.__getattribute__(self, "_micronaut_register_member")(name)

        def __getattribute__(self, name):
            if name in ("_micronaut_target_supplier", "_micronaut_java_proxy", "_micronaut_bind_java_proxy", "_micronaut_overrides", "_micronaut_setter_overrides", "_micronaut_target", "_micronaut_put_override", "_micronaut_put_setter_override", "_micronaut_register_member", "_micronaut_sync_target_attributes"):
                return object.__getattribute__(self, name)
            if name == "__micronaut_value_coercible_host__":
                java_proxy = object.__getattribute__(self, "_micronaut_java_proxy")
                if java_proxy is not None:
                    return java_proxy
            overrides = object.__getattribute__(self, "_micronaut_overrides")
            if name in overrides:
                return overrides[name]
            if name.startswith("org.graalvm.python.embedding."):
                # GraalPy probes every argument of a host call for its keyword/positional argument
                # markers: not an attribute of the target, which a lazy target must not be resolved for
                raise AttributeError(name)
            target = object.__getattribute__(self, "_micronaut_target")()
            return getattr(target, name)

        def __setattr__(self, name, value):
            setter_overrides = object.__getattribute__(self, "_micronaut_setter_overrides")
            if name in setter_overrides:
                # Attribute-backed Python beans expose PropertyElement setters, not
                # Python methods. Route assignments through the precomputed setter
                # chain so around advice can mutate parameters before the target write.
                setter_overrides[name](value)
            else:
                target = object.__getattribute__(self, "_micronaut_target")()
                setattr(target, name, value)
            object.__getattribute__(self, "_micronaut_register_member")(name)

        def __repr__(self):
            target = object.__getattribute__(self, "_micronaut_target")()
            return repr(target)

    if getattr(_MicronautScopedProxy, "__abstractmethods__", None):
        _MicronautScopedProxy.__abstractmethods__ = frozenset()
    return _MicronautScopedProxy(target_supplier, java_proxy_reference)


# ---------------------------------------------------------------------------------------------------
# Java packages and types imported by the application
#
# A Python module importing a Java class (``from micronaut.context import ApplicationContext``) or a
# Java annotation (``from jakarta.inject import Singleton``) runs unchanged at run time, so the Java
# packages it imports must be importable as Python modules. No module file exists for them: the
# compiler records the Java packages, types and annotations every module imports in a manifest
# (``__micronaut_java_imports_<hash>.py`` next to the sources of its compilation), and the meta path
# finder installed here serves them from the manifests of every compilation on the path. A package is a
# module object without a file whose attributes resolve to host classes on first access; an annotation
# is a callable that carries the annotation type and returns its target, as the compiler-generated
# decorators did; a class the run time class path lacks is a facade resolving it on first use.
# ---------------------------------------------------------------------------------------------------
import importlib.machinery as _micronaut_importlib_machinery
import os as _micronaut_os
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

    def __init__(self, name, host_class):
        self.java_class_name = name
        self.java_class = getattr(host_class, 'class') if host_class is not None else None
        self.__name__ = name.rsplit('.', 1)[-1].split('$')[-1]
        self.__qualname__ = self.__name__
        self.__module__ = _micronaut_python_module_name(name.rsplit('.', 1)[0]) if '.' in name else ''
        self.__doc__ = f"Micronaut annotation decorator for {name}."
        self._value_holds_class = None

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


def _micronaut_java_annotation(name, host_class=None):
    """The one decorator of an annotation type, by binary name."""
    decorator = _micronaut_java_annotations.get(name)
    if decorator is None:
        with _micronaut_java_annotations_lock:
            decorator = _micronaut_java_annotations.get(name)
            if decorator is None:
                decorator = _MicronautJavaAnnotation(name, host_class if host_class is not None else _micronaut_host_class(name))
                _micronaut_java_annotations[name] = decorator
    return decorator


def _micronaut_java_member(name, kind=None):
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
        return _micronaut_java_annotation(name, host_class)
    if host_class is None:
        return _MicronautJavaType(name, kind == 'interface')
    return host_class


def _micronaut_java_type_member(binary_name, name):
    """A nested type of a Java type: recorded by the compiler or on the class path, else None."""
    recorded = _micronaut_java_imports().members.get(_micronaut_python_module_name(binary_name), {}).get(name)
    if recorded is not None:
        return _micronaut_java_member(recorded[0], recorded[1])
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


class _MicronautJavaPackage(_micronaut_types.ModuleType):
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

    def __setattr__(self, name, value):
        # the import system binds an imported sub-module on its package: for the module of a Java type
        # (import a.b.Outer, from a.b.Outer import Inner) the package keeps the type itself under its name
        if isinstance(value, _MicronautJavaPackage):
            java_type = value.__dict__.get('__micronaut_java_type__')
            if java_type is not None:
                value = java_type
        super().__setattr__(name, value)

    def __dir__(self):
        return sorted({*self.__dict__, *self._micronaut_all()})

    def _micronaut_all(self):
        imports = _micronaut_java_imports()
        members = imports.members.get(self.__name__, {})
        names = [name for name in members if not name.startswith('_')]
        java_type = self.__dict__.get('__micronaut_java_type_name__')
        if java_type is not None:
            simple_name = java_type.rsplit('.', 1)[-1].split('$')[-1]
            if simple_name not in names:
                names.insert(0, simple_name)
        names.extend(name for name in imports.subpackages(self.__name__) if name not in names)
        return names


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
        return _micronaut_java_member(recorded[0], recorded[1])
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
__micronaut_java_annotation = _micronaut_java_annotation
__micronaut_java_imports = _micronaut_java_imports
