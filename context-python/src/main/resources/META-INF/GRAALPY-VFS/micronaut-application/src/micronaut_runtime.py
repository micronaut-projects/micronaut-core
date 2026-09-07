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
import pkgutil
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


def __micronaut_put_member(target, name, value):
    setattr(target, name, value)


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


def __micronaut_async_member_value(target, adapter, context):
    def adapt(value):
        try:
            if inspect.isawaitable(value) or asyncio.isfuture(value):
                return value
        except Exception:
            pass
        adapted = adapter.adaptAwaitable(context, value)
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


def __micronaut_create_raw_instance(cls):
    return cls.__new__(cls)


def __micronaut_create_scoped_proxy(cls, target_supplier):
    """A subclass of cls that forwards every attribute to the bean the supplier returns.

    Method and setter overrides registered by the Java proxy creator run the interceptor chain
    before the target is reached.
    """
    class _MicronautScopedProxy(cls):
        def __init__(self, supplier):
            object.__setattr__(self, "_micronaut_target_supplier", supplier)
            object.__setattr__(self, "_micronaut_overrides", {})
            object.__setattr__(self, "_micronaut_setter_overrides", {})

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
            for name in names:
                object.__getattribute__(self, "_micronaut_register_member")(name)

        def __getattribute__(self, name):
            if name in ("_micronaut_target_supplier", "_micronaut_overrides", "_micronaut_setter_overrides", "_micronaut_target", "_micronaut_put_override", "_micronaut_put_setter_override", "_micronaut_register_member", "_micronaut_sync_target_attributes"):
                return object.__getattribute__(self, name)
            overrides = object.__getattribute__(self, "_micronaut_overrides")
            if name in overrides:
                return overrides[name]
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

    return _MicronautScopedProxy(target_supplier)
