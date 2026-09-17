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
        return list(vars(obj).keys())
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


def __micronaut_create_raw_instance(cls):
    return cls.__new__(cls)


class _MicronautSelfInvocation:
    """Instance attribute that routes ``self.method(...)`` of a proxied bean through the Java proxy.

    The override runs the interceptor chain and then binds the class function to the target, so the
    attribute is never re-entered. Keyword and omitted defaulted arguments are laid out positionally
    the way the generated Java method declares them.
    """

    __slots__ = ("_function", "_override", "_parameters")

    def __init__(self, function, override):
        self._function = function
        self._override = override
        parameters = None
        try:
            signature = inspect.signature(function)
            parameters = tuple(signature.parameters.values())[1:]
            for parameter in parameters:
                if parameter.kind in (parameter.VAR_POSITIONAL, parameter.VAR_KEYWORD):
                    parameters = None
                    break
        except (TypeError, ValueError):
            pass
        self._parameters = parameters

    def __call__(self, *args, **kwargs):
        parameters = self._parameters
        if parameters is not None and (kwargs or len(args) < len(parameters)):
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


def __micronaut_is_coroutine_function(function):
    return inspect.iscoroutinefunction(function)


def __micronaut_await_stage(to_awaitable):
    """Coroutine over the Java stage an intercepted ``async def`` produced.

    The interceptor chain of an async method sees a CompletionStage, as it does for a Java bean. The
    stage is turned into an asyncio future only when the coroutine is awaited, on the loop that awaits it.
    """
    async def await_stage():
        return await to_awaitable()
    return await_stage()


def __micronaut_bind_self_invocations(target, proxy, names):
    """Make the intercepted methods of a proxied bean dispatch through the proxy when called on ``self``.

    A Java bean is its own proxy, so ``this.method()`` from inside the bean runs the interceptor chain.
    A Python bean is a plain object behind the scoped proxy; an instance attribute per intercepted method
    gives ``self.method()`` the same semantics while ``self`` stays the bean object.
    """
    try:
        attributes = vars(target)
    except TypeError:
        # an object without __dict__ cannot carry the attributes; its self-invocations stay direct
        return
    if attributes.get("_micronaut_proxy") is proxy:
        return
    overrides = object.__getattribute__(proxy, "_micronaut_overrides")
    cls = type(target)
    for name in names:
        override = overrides.get(name)
        function = __micronaut_get_raw_class_member(cls, name)
        if override is None or function is None or not callable(function):
            continue
        object.__setattr__(target, name, _MicronautSelfInvocation(function, override))
    object.__setattr__(target, "_micronaut_proxy", proxy)


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
