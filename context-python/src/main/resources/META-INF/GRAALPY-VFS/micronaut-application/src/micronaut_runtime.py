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


def __micronaut_import_package_of_member(name):
    """The package a member is resolved from: imported, or None while its import runs on any thread.

    A package that was never imported is imported here, before the module named after the member,
    so the import holds the lock of the package alone: importing the module first would take its
    lock and then wait for the package, which a second thread importing another module of the
    package the same way waits for while holding the lock of its module (_DeadlockError, as the
    initialiser of a generated package imports every module of the package). A package whose
    import is running is not waited for: the caller imports the module named after the member,
    which the import system executes without the lock of its package.
    """
    module = sys.modules.get(name)
    if module is None:
        return importlib.import_module(name)
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

    A name the object already holds is left alone: an instance attribute shadows the method of its class
    in plain Python, and it does here too, so a bean holding state under the name of one of its methods
    (``self.books`` next to a ``def books``) reads and writes its own value. The method keeps its
    interception for the callers that reach it as a method.
    """
    try:
        attributes = vars(target)
    except TypeError:
        # an object without __dict__ cannot carry the attributes; its self-invocations stay direct
        return
    cls = type(target)
    for i in range(len(names)):
        name = names[i]
        if name in attributes:
            # an attribute of the object itself: either a chain bound when the same target resolved
            # again, or a value the bean wrote, which shadows the method as it does in plain Python
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
                # The host object of the proxy is the Java AOP proxy it stands in for, never the
                # wrapper of the bean it currently resolves to. Until the Java side is bound the
                # proxy has no host object: answering the lookup from the target would create the
                # bean while the proxy is being created, which a bean of a custom scope that is not
                # active at that point cannot be.
                raise AttributeError(name)
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


# the Java packages, types and annotations the application imports, served without generated modules
from micronaut_java_imports import _MicronautJavaType, __micronaut_java_annotation, __micronaut_java_imports, \
    __micronaut_java_package_member, __micronaut_java_package_attribute, __micronaut_java_package_initialised, \
    __micronaut_reset_java_imports, __micronaut_install_java_import_finder


# ---------------------------------------------------------------------------------------------------------
# Patching application modules in place (development mode)
#
# A body-only edit of a Python module leaves the generated Java classes byte for byte the same: the
# development runtime then patches the module into every running context instead of starting a new
# generation. The generated classes, the class and module caches of the runtime and the AOP proxies hold
# the module, class and function objects of the running code, so those objects are kept and what they run
# is changed: a function gets the new code, a class gets the new members, and the module's other globals
# are rebound. What cannot be followed that way is refused, before anything is changed where possible, and
# the runtime restarts instead.
# ---------------------------------------------------------------------------------------------------------

class MicronautHotPatchRefused(Exception):
    """A change that cannot be patched in place: the development runtime restarts the application instead."""


# the flags that make a function a generator or a coroutine: callers handle the two kinds differently
_MICRONAUT_CODE_KIND_FLAGS = inspect.CO_GENERATOR | inspect.CO_COROUTINE | inspect.CO_ASYNC_GENERATOR | \
    inspect.CO_ITERABLE_COROUTINE

# class attributes a merge leaves to the class: its identity, and the state the class machinery keeps
_MICRONAUT_CLASS_KEPT = frozenset((
    "__dict__", "__weakref__", "__module__", "__qualname__", "__orig_bases__", "__parameters__",
    "__abstractmethods__", "_abc_impl", "__slots__", "__firstlineno__", "__static_attributes__",
))


def _micronaut_slot_descriptor_types():
    """The types of the descriptors of slots and of the instance dictionary, bound to the class that created them."""
    class Slotted:
        __slots__ = ("slot",)

    class Plain:
        pass
    return type(Slotted.__dict__["slot"]), type(Plain.__dict__["__dict__"])


_MICRONAUT_SLOT_DESCRIPTORS = _micronaut_slot_descriptor_types()


# the attributes of a function the merge sets itself, which an implementation may keep in the function's __dict__
_MICRONAUT_FUNCTION_KEPT = frozenset((
    "__annotations__", "__doc__", "__name__", "__qualname__", "__module__", "__defaults__", "__kwdefaults__",
    "__code__", "__globals__", "__closure__", "__dict__", "__type_params__", "__builtins__",
))

# module globals the import system owns
_MICRONAUT_MODULE_KEPT = frozenset((
    "__name__", "__file__", "__package__", "__spec__", "__loader__", "__builtins__", "__path__", "__cached__",
))


def _micronaut_vfs_source_roots():
    """The directories of the virtual file system the application modules are imported from."""
    roots = []
    for entry in sys.path:
        if isinstance(entry, str) and entry.replace("\\", "/").rstrip("/").endswith("graalpy_vfs/src"):
            if entry not in roots:
                roots.append(entry)
    return roots or ["/graalpy_vfs/src"]


def _micronaut_is_injected_global(annotations, name):
    """Whether a module global is declared Annotated, which marks the globals the Java side injects or binds.

    Their values were set from Java after the module ran (``Annotated[Repository, Inject]``); executing the
    module again would rebind them to their declared default, so they keep their current value.
    """
    annotation = annotations.get(name) if isinstance(annotations, dict) else None
    if annotation is None:
        return False
    if isinstance(annotation, str):
        return annotation.lstrip().split("[", 1)[0].rsplit(".", 1)[-1] == "Annotated"
    return getattr(annotation, "__metadata__", None) is not None


def _micronaut_same_class(a, b, mapping):
    """Whether class a, created by executing the module again, stands for class b."""
    if a is b or mapping.get(id(a)) is b:
        return True
    return isinstance(a, type) and isinstance(b, type) and a.__module__ == b.__module__ \
        and a.__qualname__ == b.__qualname__


def _micronaut_new_cell(value):
    import types
    return types.CellType(value)


class _MicronautModulePatch:
    """The patch of one module in one context: planned first, the module's own objects are changed after.

    The new code is executed into the module's own namespace, so that the functions it defines see the
    module's globals, as the old ones do; the names it rebinds are put back to the old objects as soon as the
    plan is made, so that a module patched after this one, which imports from it, sees the old objects too.
    """

    def __init__(self, module, path):
        self.module = module
        self.path = path
        self.saved = None
        self.actions = []
        self.classes = {}
        self.functions = set()

    def refuse(self, reason):
        raise MicronautHotPatchRefused(f"{self.module.__name__}: {reason}")

    def load_code(self):
        import importlib.util
        import marshal
        spec = getattr(self.module, "__spec__", None)
        loader = getattr(spec, "loader", None) or getattr(self.module, "__loader__", None)
        if loader is None or not hasattr(loader, "get_data"):
            self.refuse("the module has no loader that reads files")
        source = loader.get_data(self.path)
        data = None
        try:
            data = loader.get_data(importlib.util.cache_from_source(self.path))
        except (OSError, NotImplementedError, ValueError):
            data = None
        if data is not None and len(data) >= 16 and data[:4] == importlib.util.MAGIC_NUMBER:
            flags = int.from_bytes(data[4:8], "little")
            if flags & 0x1:
                # the checked-hash bytecode the compiler wrote for a module the runtime transformer rewrites:
                # its code is the transformed one, which compiling the source would not reproduce
                if (flags & 0x2) and data[8:16] != importlib.util.source_hash(source):
                    self.refuse("its bytecode was compiled from another version of the source")
                return marshal.loads(data[16:])
        return compile(source, self.path, "exec", dont_inherit=True)

    def prepare(self):
        code = self.load_code()
        namespace = self.module.__dict__
        self.saved = dict(namespace)
        try:
            exec(code, namespace)
            self.plan(code)
        except BaseException:
            self.rollback()
            raise

    def rollback(self):
        if self.saved is not None:
            namespace = self.module.__dict__
            namespace.clear()
            namespace.update(self.saved)

    def plan(self, code):
        namespace = self.module.__dict__
        saved = self.saved
        annotations = saved.get("__annotations__")
        # the classes first: a global holding an instance of a class the module defines is moved to the old class
        for name, new in list(namespace.items()):
            old = saved.get(name, namespace)
            if old is not namespace and isinstance(old, type) and isinstance(new, type) and old is not new:
                self.classes.setdefault(id(new), old)
        for name, new in list(namespace.items()):
            if name not in saved:
                continue
            old = saved[name]
            if old is new:
                continue
            if name in _MICRONAUT_MODULE_KEPT or name.startswith("__micronaut") \
                    or _micronaut_is_injected_global(annotations, name):
                namespace[name] = old
                continue
            bound_value = self.plan_value(old, new, f"global '{name}'")
            namespace[name] = bound_value
            if bound_value is new and _micronaut_member_kind(new) is None and old is not None and not isinstance(old, bool):
                self.refuse_if_imported(name, old)
        module_annotations = namespace.get("__annotations__")
        if isinstance(module_annotations, dict):
            # executing the module again filled its annotations, the same dictionary, with the new classes
            self.actions.append(lambda: module_annotations.update(self.remap_annotations(module_annotations)))
        bound = set(code.co_names)
        module_name = self.module.__name__
        for name, old in self.saved.items():
            if name in namespace and name not in bound and namespace[name] is old \
                    and _micronaut_member_kind(old) in ("function", "class") and getattr(old, "__module__", None) == module_name \
                    and getattr(old, "__qualname__", None) == name:
                # a function or class the module defined that the new code no longer does: gone, as after a restart.
                # A value the module computed cannot be told from one set on the module from outside, and stays
                del namespace[name]

    def plan_value(self, old, new, what):
        """Plans the merge of a new value into an old one; returns the value the name is bound to."""
        old_kind = _micronaut_member_kind(old)
        new_kind = _micronaut_member_kind(new)
        if old_kind != new_kind and (old_kind is not None or new_kind is not None):
            self.refuse(f"{what} changed from {old_kind or 'a value'} to {new_kind or 'a value'}")
        if new_kind == "function":
            self.plan_function(old, new, what)
            return old
        if new_kind == "class":
            self.plan_class(old, new, what)
            return old
        if new_kind in ("staticmethod", "classmethod"):
            if _micronaut_member_kind(old.__func__) == "function" and _micronaut_member_kind(new.__func__) == "function":
                self.plan_function(old.__func__, new.__func__, what)
                return old
            return new
        if new_kind == "property":
            return self.plan_property(old, new, what)
        moved = self.classes.get(id(type(new)))
        if moved is not None:
            self.actions.append(lambda: _micronaut_set_class(new, moved))
        return new

    def plan_property(self, old, new, what):
        parts = []
        replaced = False
        for accessor in ("fget", "fset", "fdel"):
            old_part = getattr(old, accessor)
            new_part = getattr(new, accessor)
            if old_part is not None and new_part is not None and _micronaut_member_kind(old_part) == "function" \
                    and _micronaut_member_kind(new_part) == "function":
                self.plan_function(old_part, new_part, f"{what}.{accessor}")
                parts.append(old_part)
            else:
                replaced = replaced or old_part is not new_part
                parts.append(new_part)
        if not replaced and old.__doc__ == new.__doc__:
            return old
        return property(parts[0], parts[1], parts[2], new.__doc__)

    def plan_function(self, old, new, what):
        if old is new or id(new) in self.functions:
            return
        self.functions.add(id(new))
        old_code = old.__code__
        new_code = new.__code__
        if old_code.co_freevars != new_code.co_freevars:
            self.refuse(f"the variables {what} closes over changed")
        if (old_code.co_flags ^ new_code.co_flags) & _MICRONAUT_CODE_KIND_FLAGS:
            self.refuse(f"{what} changed between a function, a generator and a coroutine")
        for index, variable in enumerate(old_code.co_freevars):
            old_cell = old.__closure__[index]
            new_cell = new.__closure__[index]
            try:
                old_value = old_cell.cell_contents
            except ValueError:
                old_value = old_cell
            try:
                new_value = new_cell.cell_contents
            except ValueError:
                new_value = new_cell
            if variable == "__class__":
                if not _micronaut_same_class(new_value, old_value, self.classes):
                    self.refuse(f"{what} is a method of another class")
                continue
            if old_value is new_value or old_value is old_cell or new_value is new_cell:
                continue
            kind = _micronaut_member_kind(new_value)
            if kind == _micronaut_member_kind(old_value) and kind in ("function", "class"):
                # a decorator's wrapper closes over the function it wraps
                self.plan_value(old_value, new_value, f"{what} (closure '{variable}')")
            else:
                self.actions.append(lambda cell=old_cell, value=new_value: setattr(cell, "cell_contents", value))
        attributes = {}
        for key, value in new.__dict__.items():
            old_value = old.__dict__.get(key)
            if key == "__wrapped__" and old_value is not None and _micronaut_member_kind(old_value) == "function" \
                    and _micronaut_member_kind(value) == "function":
                self.plan_function(old_value, value, f"{what}.__wrapped__")
                continue
            if key.startswith("__micronaut") or key in _MICRONAUT_FUNCTION_KEPT:
                # set from the new function below, or owned by the function; GraalPy keeps some of them in __dict__
                continue
            attributes[key] = value
        defaults = new.__defaults__
        kwdefaults = new.__kwdefaults__
        doc = new.__doc__
        annotations = new.__annotations__

        def apply():
            old.__code__ = new_code
            old.__defaults__ = None if defaults is None else tuple(self.adopt_value(value) for value in defaults)
            old.__kwdefaults__ = None if kwdefaults is None else {key: self.adopt_value(value) for key, value in kwdefaults.items()}
            old.__doc__ = doc
            old.__annotations__ = self.remap_annotations(annotations)
            for key in [key for key in old.__dict__ if key not in attributes and not key.startswith("__micronaut")
                        and key not in _MICRONAUT_FUNCTION_KEPT and key != "__wrapped__"]:
                # set by a decorator the edit removed
                del old.__dict__[key]
            old.__dict__.update(attributes)
        self.actions.append(apply)

    def plan_class(self, old, new, what):
        mapped = self.classes.get(id(new))
        if mapped is not None and mapped is not old:
            self.refuse(f"{what} stands for two classes")
        if id(new) in self.functions:
            return
        self.functions.add(id(new))
        self.classes[id(new)] = old
        if not _micronaut_same_class(type(new), type(old), self.classes):
            self.refuse(f"the metaclass of {what} changed")
        if len(old.__bases__) != len(new.__bases__) or not all(
                _micronaut_same_class(new_base, old_base, self.classes)
                for old_base, new_base in zip(old.__bases__, new.__bases__)):
            self.refuse(f"the bases of {what} changed")
        if old.__dict__.get("__slots__") != new.__dict__.get("__slots__"):
            self.refuse(f"the slots of {what} changed")
        old_members = old.__dict__
        new_members = new.__dict__
        enum_members = set()
        import enum
        is_enum = isinstance(old, enum.EnumMeta)
        if is_enum:
            old_values = [(member.name, member.value) for member in old]
            new_values = [(member.name, member.value) for member in new]
            if old_values != new_values:
                self.refuse(f"the members of the enum {what} changed")
            enum_members = set(old.__members__)
        introduction = old_members.get("__micronaut_introduction__", False)
        for name, new_value in new_members.items():
            if name in _MICRONAUT_CLASS_KEPT or name.startswith("__micronaut") or name in enum_members:
                continue
            if isinstance(new_value, _MICRONAUT_SLOT_DESCRIPTORS):
                # the descriptor of a slot belongs to the class that declared it: the old class keeps its own
                continue
            if is_enum and _micronaut_member_kind(new_value) not in ("function", "staticmethod", "classmethod", "property"):
                # the enum machinery's own state, created again with the class: the old class keeps its own
                continue
            if introduction and (name == "_is_protocol" or getattr(new_value, "__isabstractmethod__", False)):
                # the runtime made the introduction instantiable once: its stubs and flags stay
                continue
            if name == "__annotations__" and isinstance(new_value, dict):
                self.actions.append(lambda v=new_value: setattr(old, "__annotations__", self.remap_annotations(v)))
                continue
            member = f"{what}.{name}"
            if name in old_members:
                old_value = old_members[name]
                if old_value is new_value:
                    continue
                bound = self.plan_value(old_value, new_value, member)
                if bound is not old_value:
                    self.actions.append(lambda n=name, v=bound: setattr(old, n, self.adopt(v, old)))
            else:
                self.actions.append(lambda n=name, v=new_value: setattr(old, n, self.adopt(v, old)))
        for name, old_value in list(old_members.items()):
            if name in new_members or name in _MICRONAUT_CLASS_KEPT or name in enum_members:
                continue
            if isinstance(old_value, _MICRONAUT_SLOT_DESCRIPTORS):
                continue
            if is_enum and _micronaut_member_kind(old_value) not in ("function", "staticmethod", "classmethod", "property"):
                continue
            if name.startswith("__micronaut") or name.startswith("_micronaut"):
                continue
            if getattr(old_value, "__module__", old.__module__) != old.__module__:
                # added by the runtime, such as the default methods of the Java interfaces the class implements
                continue
            self.actions.append(lambda n=name: delattr(old, n))

    def refuse_if_imported(self, name, old):
        """A value another module imported by name keeps the old value there, which a restart would not."""
        for other_name, other in list(sys.modules.items()):
            if other is self.module:
                continue
            namespace = getattr(other, "__dict__", None)
            if isinstance(namespace, dict) and namespace.get(name, namespace) is old:
                self.refuse(f"the module {other_name} imported '{name}', whose value changed")

    def remap_annotations(self, annotations):
        """Annotations naming a class of the module name its old class, which the module keeps."""
        if not isinstance(annotations, dict):
            return annotations
        return {key: self.remap_annotation(value) for key, value in annotations.items()}

    def remap_annotation(self, annotation):
        """An annotation with the classes of the module it names, at any depth (list[Point], Point | None), mapped."""
        if isinstance(annotation, type) and not hasattr(annotation, "__origin__"):
            return self.classes.get(id(annotation), annotation)
        args = getattr(annotation, "__args__", None)
        if not isinstance(args, tuple) or not args:
            return annotation
        mapped = tuple(self.remap_annotation(arg) for arg in args)
        metadata = getattr(annotation, "__metadata__", None)
        if metadata is None and all(new is old for new, old in zip(mapped, args)):
            return annotation
        try:
            import types
            import typing
            if metadata is not None:
                return typing.Annotated[(self.remap_annotation(annotation.__origin__),) + tuple(metadata)]
            if isinstance(annotation, types.UnionType):
                import functools
                import operator
                return functools.reduce(operator.or_, mapped)
            if isinstance(annotation, types.GenericAlias):
                return types.GenericAlias(annotation.__origin__, mapped)
            copy_with = getattr(annotation, "copy_with", None)
            if copy_with is not None:
                return copy_with(mapped)
        except Exception:
            pass
        return annotation

    def adopt_value(self, value):
        """A value the new code created, such as a default argument: a class of the module stands for its old
        class, and an instance of one is moved to the old class."""
        if isinstance(value, type):
            return self.classes.get(id(value), value)
        moved = self.classes.get(id(type(value)))
        if moved is not None:
            _micronaut_set_class(value, moved)
        return value

    def adopt(self, value, owner):
        """A member the edit added to a class, whose methods calling super() would name the discarded class."""
        kind = _micronaut_member_kind(value)
        if kind == "function":
            return self.adopt_function(value, owner)
        if kind == "staticmethod":
            return staticmethod(self.adopt_function(value.__func__, owner)) \
                if _micronaut_member_kind(value.__func__) == "function" else value
        if kind == "classmethod":
            return classmethod(self.adopt_function(value.__func__, owner)) \
                if _micronaut_member_kind(value.__func__) == "function" else value
        if kind == "property":
            parts = [self.adopt_function(part, owner) if part is not None and _micronaut_member_kind(part) == "function"
                     else part for part in (value.fget, value.fset, value.fdel)]
            return property(parts[0], parts[1], parts[2], value.__doc__)
        return value

    def adopt_function(self, function, owner):
        import types
        code = function.__code__
        if "__class__" not in code.co_freevars:
            return function
        index = code.co_freevars.index("__class__")
        closure = list(function.__closure__)
        try:
            if closure[index].cell_contents is owner:
                return function
        except ValueError:
            pass
        closure[index] = _micronaut_new_cell(owner)
        adopted = types.FunctionType(code, function.__globals__, function.__name__, function.__defaults__, tuple(closure))
        adopted.__kwdefaults__ = function.__kwdefaults__
        adopted.__qualname__ = function.__qualname__
        adopted.__doc__ = function.__doc__
        adopted.__annotations__ = function.__annotations__
        adopted.__dict__.update(function.__dict__)
        return adopted

    def apply(self):
        for action in self.actions:
            action()


def _micronaut_member_kind(value):
    import types
    if isinstance(value, types.FunctionType):
        return "function"
    if isinstance(value, type):
        return "class"
    if isinstance(value, staticmethod):
        return "staticmethod"
    if isinstance(value, classmethod):
        return "classmethod"
    if isinstance(value, property):
        return "property"
    return None


def _micronaut_set_class(instance, cls):
    try:
        instance.__class__ = cls
    except TypeError as e:
        raise MicronautHotPatchRefused(f"an instance of {cls.__qualname__} cannot be moved to the patched class: {e}")


def __micronaut_hot_patch(relative_paths):
    """Patches the application modules of the given files in place, in this context.

    The paths are relative to the source root of the virtual file system: ``app/hello.py``, or the bytecode
    the compiler wrote for it, ``app/__pycache__/hello.graalpy253-313.pyc``. A module this context has not
    imported is left alone; it imports the new version when it is first needed. Every module is planned
    before any is changed: a refusal (``MicronautHotPatchRefused``) or any other error raised while planning
    leaves the modules as they were.
    Returns ``(names, None)`` with the names of the modules patched, or ``(None, reason)`` when the change was
    refused or failed: the failure is a value rather than an exception, so that the host reports it without
    walking the guest frames of the merge.
    """
    try:
        return (_micronaut_hot_patch(relative_paths), None)
    except MicronautHotPatchRefused as e:
        return (None, str(e))
    except BaseException as e:
        import traceback
        return (None, f"{type(e).__name__}: {e}\n{traceback.format_exc()}")


def _micronaut_hot_patch(relative_paths):
    import importlib.util
    import os
    importlib.invalidate_caches()
    files = set()
    for relative in relative_paths:
        relative = str(relative)
        for root in _micronaut_vfs_source_roots():
            path = os.path.join(root, *relative.split("/"))
            if path.endswith(".pyc"):
                try:
                    path = importlib.util.source_from_cache(path)
                except ValueError:
                    # bytecode of another implementation, which this one never reads
                    continue
            files.add(os.path.normpath(path))
    patches = []
    seen = set()
    for name, module in list(sys.modules.items()):
        file = getattr(module, "__file__", None)
        if not isinstance(file, str) or id(module) in seen:
            continue
        path = os.path.normpath(file)
        if path in files:
            seen.add(id(module))
            patches.append(_MicronautModulePatch(module, file))
    prepared = []
    try:
        for patch in patches:
            patch.prepare()
            prepared.append(patch)
    except BaseException:
        for patch in reversed(prepared):
            patch.rollback()
        raise
    for patch in patches:
        patch.apply()
    # parameter layouts are cached by function, and a patched function may lay out its defaults differently
    _micronaut_self_invocation_layouts.clear()
    return [patch.module.__name__ for patch in patches]
