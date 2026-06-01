/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.python;

import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

/**
 * Static holder for the GraalPy context used by Python bridge classes.
 * Provides thread-safe access to the shared Python execution context.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class ContextHolder {

    private static final String NEW_UNINITIALIZED_INSTANCE = "__micronaut_new_uninitialized_instance";
    private static final String SET_INSTANCE_PROPERTY = "__micronaut_set_instance_property";

    private static final AtomicBoolean REUSE_CONTEXT = new AtomicBoolean();
    private static volatile @Nullable Context context;
    private static volatile @Nullable ClassLoader contextClassLoader;
    private static volatile @Nullable PythonPool pythonPool;

    private ContextHolder() {
    }

    /**
     * Internal hook to register the PythonPool once initialized.
     * @param pool The Python pool
     */
    public static void setPythonPool(@Nullable PythonPool pool) {
        ContextHolder.pythonPool = pool;
    }

    /**
     * @return The configured PythonPool. Throws if not initialized.
     */
    public static PythonPool getPythonPool() {
        PythonPool pool = pythonPool;
        if (pool == null) {
            throw new IllegalStateException("PythonPool has not been initialized.");
        }
        return pool;
    }

    /**
     * Obtain a pooled Python class instance (per-context cached).
     * @param packageName The Python package (or null/python for top-level)
     * @param simpleName The class simple name
     * @return The pooled class instance (Value) from some context
     */
    @UsedByGeneratedCode
    public static Value findPooledClass(@Nullable String packageName, String simpleName) {
        if (isReuseContext() || pythonPool == null) {
            return findClass(packageName, simpleName);
        }
        return getPythonPool().getAnyClass(packageName, simpleName);
    }

    /**
     * Obtain a pooled Python class instance from a specific context.
     * @param packageName The Python package (or null/python for top-level)
     * @param simpleName The class simple name
     * @param context The context
     * @return The pooled class instance (Value)
     */
    @UsedByGeneratedCode
    public static Value findPooledClass(@Nullable String packageName, String simpleName, Context context) {
        if (isReuseContext() || pythonPool == null) {
            return findClass(packageName, simpleName, context);
        }
        return getPythonPool().getClass(context, packageName, simpleName);
    }

    /**
     * Execute a function with a borrowed pooled class instance.
     * @param packageName The Python package
     * @param simpleName The class name
     * @param fn Function receiving the pooled Value
     * @param <T> Result type returned by the function
     * @return Result returned from the function
     */
    @UsedByGeneratedCode
    public static <T> T withPooled(@Nullable String packageName, String simpleName, java.util.function.Function<Value, T> fn) {
        if (isReuseContext() || pythonPool == null) {
            return fn.apply(findClass(packageName, simpleName));
        }
        return getPythonPool().withClass(packageName, simpleName, fn);
    }

    /**
     * Obtain a pooled Python script/module object.
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @return A pooled script Value
     */
    @UsedByGeneratedCode
    public static Value findPooledScript(String packageName, String scriptName) {
        if (isReuseContext() || pythonPool == null) {
            return findScript(packageName, scriptName);
        }
        return getPythonPool().getAnyScript(packageName, scriptName);
    }

    /**
     * Obtain a pooled Python script/module object from a specific context.
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @param context The context
     * @return A pooled script Value
     */
    @UsedByGeneratedCode
    public static Value findPooledScript(String packageName, String scriptName, Context context) {
        if (isReuseContext() || pythonPool == null) {
            return findScript(packageName, scriptName, context);
        }
        return getPythonPool().getScript(context, packageName, scriptName);
    }

    /**
     * Execute a function with a borrowed pooled script/module object.
     * @param packageName The package
     * @param scriptName The script name
     * @param fn Function receiving the script Value
     * @param <T> Result type returned by the function
     * @return Result returned from the function
     */
    @UsedByGeneratedCode
    public static <T> T withPooledScript(String packageName, String scriptName, java.util.function.Function<Value, T> fn) {
        if (isReuseContext() || pythonPool == null) {
            return fn.apply(findScript(packageName, scriptName));
        }
        return getPythonPool().withScript(packageName, scriptName, fn);
    }

    /**
     * @param packageName The package
     * @param scriptName The script name
     * @param attribute The attribute name
     * @param value The value to inject
     */
    @UsedByGeneratedCode
    public static void injectPooledScript(String packageName, String scriptName, String attribute, Object value) {
        if (isReuseContext() || pythonPool == null) {
            Value script = findScript(packageName, scriptName);
            script.putMember(attribute, GraalPyRuntimeUtil.coerceToContext(value, script.getContext()));
            return;
        }
        getPythonPool().injectScript(packageName, scriptName, attribute, value);
    }

    /**
     * Invoke a method on a pooled class instance.
     * @param packageName The package
     * @param simpleName The class name
     * @param methodName The method name
     * @param args Arguments
     * @return The polyglot result
     */
    @UsedByGeneratedCode
    public static Value invokePooled(@Nullable String packageName, String simpleName, String methodName, Object... args) {
        return withPooled(packageName, simpleName, v -> GraalPyRuntimeUtil.invokePythonMethod(
            v,
            methodName,
            GraalPyRuntimeUtil.coerceArgumentsToContext(v.getContext(), args)
        ));
    }

    /**
     * Invoke a method on a pooled script instance.
     * @param packageName The package
     * @param scriptName The script name
     * @param methodName The method name
     * @param args Arguments
     * @return The polyglot result
     */
    @UsedByGeneratedCode
    public static Value invokePooledScript(String packageName, String scriptName, String methodName, Object... args) {
        return withPooledScript(packageName, scriptName, v -> v.getMember(methodName).execute(
            GraalPyRuntimeUtil.coerceArgumentsToContext(v.getContext(), args)
        ));
    }

    public static Context getContext() {
        Context ctx = context;
        if (ctx == null) {
            throw new IllegalStateException("GraalPy context has not been initialized. " +
                "Make sure micronaut-context-python is on the classpath.");
        }
        return ctx;
    }

    /**
     * Create an instance that is abstract and fill out the abstract methods with stubs to be later populated.
     *
     * @param packageName The package name
     * @param simpleName  The simple name
     * @param args        The args
     * @return The new instance
     */
    @UsedByGeneratedCode
    public static Value newIntroduction(@Nullable String packageName, String simpleName, Object... args) {
        Value pythonClass = findClass(packageName, simpleName);
        if (!pythonClass.hasMember("__micronaut_introduction__")) {

            Value abstractMethods = pythonClass.getMember("__abstractmethods__");
            if (abstractMethods != null && abstractMethods.hasIterator()) {
                Value iterator = abstractMethods.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    String methodName = iterator.getIteratorNextElement().asString();
                    ProxyExecutable stub = (execArgs) -> null;
                    pythonClass.putMember(methodName, stub);
                }
            }
            Context context = pythonClass.getContext();
            if (!context.getBindings(PYTHON).hasMember("update_abstractmethods")) {
                context.eval(PYTHON, "from abc import update_abstractmethods");
            }

            Value updateAbstractMethods = context.getBindings(PYTHON).getMember("update_abstractmethods");
            updateAbstractMethods.execute(pythonClass);
            Value isProtocol = pythonClass.getMember("_is_protocol");
            if (isProtocol != null && isProtocol.isBoolean() && isProtocol.asBoolean()) {
                pythonClass.putMember("_is_protocol", false);
            }
        }
        return instantiate(packageName, simpleName, args, pythonClass);
    }

    /**
     * Create a new abstract introduction instance, omitting trailing null arguments that correspond
     * to Python constructor defaults.
     *
     * @param packageName The package name
     * @param simpleName The simple name
     * @param requiredArgCount The number of non-defaulted positional constructor arguments
     * @param args The arguments
     * @return The new instance
     */
    @UsedByGeneratedCode
    public static Value newIntroductionWithDefaultedTrailingNulls(@Nullable String packageName,
                                                                  String simpleName,
                                                                  int requiredArgCount,
                                                                  Object... args) {
        return newIntroduction(packageName, simpleName, trimDefaultedTrailingNulls(requiredArgCount, args));
    }

    /**
     * Create a new instance for the given package name, simple name and args.
     *
     * @param packageName The package name
     * @param simpleName  The simple name
     * @param args        The args
     * @return The new instance
     */
    @UsedByGeneratedCode
    public static Value newInstance(@Nullable String packageName, String simpleName, Object... args) {
        Value pythonClass = findClass(packageName, simpleName);
        return instantiate(packageName, simpleName, args, pythonClass);
    }

    /**
     * Resolve a Python enum constant by its Java enum name.
     *
     * @param packageName The package name
     * @param simpleName  The simple name
     * @param name        The enum constant name
     * @return The Python enum constant
     */
    @UsedByGeneratedCode
    public static Value enumValue(@Nullable String packageName, String simpleName, String name) {
        Value pythonClass = findClass(packageName, simpleName);
        return withContextClassLoader(() -> {
            Value enumValue = pythonClass.getMember(name);
            if (enumValue != null && !GraalPyRuntimeUtil.isNone(enumValue)) {
                return enumValue;
            }
            Value members = pythonClass.getMember("__members__");
            if (members != null && members.hasHashEntries()) {
                enumValue = members.getHashValue(name);
                if (enumValue != null && !GraalPyRuntimeUtil.isNone(enumValue)) {
                    return enumValue;
                }
            }
            String qualifiedName = packageName == null || PYTHON.equals(packageName) ? simpleName : packageName + "." + simpleName;
            throw new IllegalArgumentException("Cannot resolve Python enum constant: " + qualifiedName + "." + name);
        });
    }

    /**
     * Create a new instance, omitting trailing null arguments that correspond to Python constructor
     * defaults.
     *
     * @param packageName The package name
     * @param simpleName The simple name
     * @param requiredArgCount The number of non-defaulted positional constructor arguments
     * @param args The arguments
     * @return The new instance
     */
    @UsedByGeneratedCode
    public static Value newInstanceWithDefaultedTrailingNulls(@Nullable String packageName,
                                                             String simpleName,
                                                             int requiredArgCount,
                                                             Object... args) {
        return newInstance(packageName, simpleName, trimDefaultedTrailingNulls(requiredArgCount, args));
    }

    /**
     * Create a Python instance without invoking {@code __init__}.
     *
     * @param packageName The package name
     * @param simpleName The simple name
     * @return The new uninitialized instance
     */
    @UsedByGeneratedCode
    public static Value newUninitializedInstance(@Nullable String packageName, String simpleName) {
        Context context = getContext();
        Value pythonClass = findClass(packageName, simpleName, context);
        return context.eval(PYTHON, "lambda cls: object.__new__(cls)").execute(pythonClass);
    }

    /**
     * Create a new instance and set properties via member assignment when no constructor exists.
     * This overload accepts a map of property values and will instantiate the Python class with
     * no positional arguments, then populate attributes using Graal Polyglot putMember.
     *
     * @param packageName The package name (nullable)
     * @param simpleName  The simple class name
     * @param props       Map of property names to values
     * @return The new instance with members populated.
     */
    @UsedByGeneratedCode
    public static Value newInstance(@Nullable String packageName, String simpleName, java.util.Map<String, Object> props) {
        Value pythonClass = findClass(packageName, simpleName);
        return withContextClassLoader(() -> {
            Value instance = instantiate(packageName, simpleName, new Object[0], pythonClass);
            if (props != null && !props.isEmpty()) {
                Value propertySetter = propertySetter(instance.getContext());
                for (java.util.Map.Entry<String, Object> e : props.entrySet()) {
                    propertySetter.execute(
                        instance,
                        e.getKey(),
                        GraalPyRuntimeUtil.coerceToContext(e.getValue(), instance.getContext())
                    );
                }
            }
            return instance;
        });
    }

    /**
     * Create a new frozen dataclass instance and set properties without invoking __init__.
     *
     * @param packageName The package name (nullable)
     * @param simpleName  The simple class name
     * @param props       Map of property names to values
     * @return The new frozen dataclass instance with members populated.
     */
    @UsedByGeneratedCode
    public static Value newFrozenDataclassInstance(@Nullable String packageName, String simpleName, java.util.Map<String, Object> props) {
        Value pythonClass = findClass(packageName, simpleName);
        return withContextClassLoader(() -> {
            Value instance = uninitializedInstanceFactory(pythonClass.getContext()).execute(pythonClass);
            if (props != null && !props.isEmpty()) {
                Value propertySetter = propertySetter(instance.getContext());
                for (java.util.Map.Entry<String, Object> e : props.entrySet()) {
                    propertySetter.execute(
                        instance,
                        e.getKey(),
                        GraalPyRuntimeUtil.coerceToContext(e.getValue(), instance.getContext())
                    );
                }
            }
            return instance;
        });
    }

    private static Value uninitializedInstanceFactory(Context context) {
        Value bindings = context.getBindings(PYTHON);
        Value factory = bindings.getMember(NEW_UNINITIALIZED_INSTANCE);
        if (factory == null || GraalPyRuntimeUtil.isNone(factory)) {
            context.eval(
                PYTHON,
                """
                def __micronaut_new_uninitialized_instance(cls):
                    return object.__new__(cls)
                """
            );
            factory = bindings.getMember(NEW_UNINITIALIZED_INSTANCE);
        }
        return factory;
    }

    private static Value propertySetter(Context context) {
        Value bindings = context.getBindings(PYTHON);
        Value setter = bindings.getMember(SET_INSTANCE_PROPERTY);
        if (setter == null || GraalPyRuntimeUtil.isNone(setter)) {
            context.eval(
                PYTHON,
                """
                def __micronaut_set_instance_property(instance, name, value):
                    object.__setattr__(instance, name, value)
                    return instance
                """
            );
            setter = bindings.getMember(SET_INSTANCE_PROPERTY);
        }
        return setter;
    }

    private static Value instantiate(@Nullable String packageName, String simpleName, Object[] args, Value pythonClass) {
        return withContextClassLoader(() -> {
            if (pythonClass.canInstantiate()) {
                return pythonClass.newInstance(GraalPyRuntimeUtil.coerceArgumentsToContext(pythonClass.getContext(), args));
            } else {
                String qualifiedName = packageName == null || PYTHON.equals(packageName) ? simpleName :  packageName + "." + simpleName;
                throw new InstantiationException("Cannot instantiate class: " + qualifiedName + ". Ensure the class is a valid Python class and is non-abstract.");
            }
        });
    }

    /**
     * Create a new instance for the given simple name and args.
     *
     * @param simpleName The simple name
     * @param args       The args
     * @return The new instance
     */
    public static Value newInstance(String simpleName, Object... args) {
        Value pythonClass = findClass(simpleName);
        return instantiate(null, simpleName, args, pythonClass);
    }

    private static Object[] trimDefaultedTrailingNulls(int requiredArgCount, Object[] args) {
        int length = args.length;
        while (length > requiredArgCount && args[length - 1] == null) {
            length--;
        }
        return length == args.length ? args : Arrays.copyOf(args, length);
    }

    public static Value findClass(@org.jspecify.annotations.Nullable String packageName, String simpleName) {
        return findClass(packageName, simpleName, getContext());
    }

    static Value findClass(@org.jspecify.annotations.Nullable String packageName, String simpleName, Context ctx) {
        if (packageName == null || PYTHON.equals(packageName)) {
            return findClass(simpleName, ctx);
        }

        String importName = rootName(simpleName);
        try {
            return nestedMember(importPackageMember(ctx, packageName, importName), simpleName);
        } catch (Exception e) {
            throw new InstantiationException("Failed to import Python class [" + packageName + "." + simpleName + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Find a Python class by fully qualified name.
     * @param simpleName The class name
     * @return The class Value
     */
    @UsedByGeneratedCode
    public static Value findClass(String simpleName) {
        return findClass(simpleName, getContext());
    }

    static Value findClass(String simpleName, Context ctx) {
        String importName = rootName(simpleName);
        Value v = ctx.getBindings(PYTHON).getMember(importName);
        if (v == null) {
            Value member = importModule(ctx, importName).getMember(importName);
            if (member == null) {
                throw new InstantiationException("Cannot find Python class: " + simpleName);
            }
            v = member;
        }
        return nestedMember(v, simpleName);
    }

    private static String rootName(String simpleName) {
        int nestedSeparator = simpleName.indexOf('.');
        return nestedSeparator < 0 ? simpleName : simpleName.substring(0, nestedSeparator);
    }

    private static Value nestedMember(Value root, String simpleName) {
        Value value = root;
        int nestedSeparator = simpleName.indexOf('.');
        if (nestedSeparator < 0) {
            return value;
        }
        int start = nestedSeparator + 1;
        while (start < simpleName.length()) {
            int end = simpleName.indexOf('.', start);
            String nestedName = end < 0 ? simpleName.substring(start) : simpleName.substring(start, end);
            value = value.getMember(nestedName);
            if (value == null) {
                throw new InstantiationException("Cannot find Python class: " + simpleName);
            }
            if (end < 0) {
                break;
            }
            start = end + 1;
        }
        return value;
    }

    /**
     * Find a Python script/module Value.
     * @param packageName The package name (or python for top-level)
     * @param scriptName The script/module name
     * @return The module Value
     */
    @UsedByGeneratedCode
    public static Value findScript(String packageName, String scriptName) {
        Context ctx = getContext();
        return findScript(packageName, scriptName, ctx);
    }

    static Value findScript(String packageName, String scriptName, Context ctx) {
        Value v = ctx.getBindings(PYTHON);
        if (v != null) {
            if (PYTHON.equals(packageName)) {
                if ("Unnamed".equals(scriptName)) {
                    return v;
                } else {
                    return importModule(ctx, scriptName);
                }
            } else {
                return importModule(ctx, packageName + "." + scriptName);
            }
        } else {
            throw new InstantiationException("Cannot find Python module: " + packageName);
        }
    }

    /**
     * Invoke a static method on the given Python class.
     *
     * @param packageName The package name
     * @param simpleName  The simple class name
     * @param methodName  The method name
     * @param args        The method arguments
     * @return The method result
     */
    public static Value invokeStaticMethod(@Nullable String packageName, String simpleName, String methodName, Object... args) {
        if (packageName == null || PYTHON.equals(packageName)) {
            return invokeStaticMethod(simpleName, methodName, args);
        } else {
            Context ctx = getContext();
            return findClass(packageName, simpleName, ctx).invokeMember(methodName, args);
        }
    }

    /**
     * Invoke a static method on the given Python class.
     *
     * @param simpleName  The simple class name
     * @param methodName  The method name
     * @param args        The method arguments
     * @return The method result
     */
    public static Value invokeStaticMethod(String simpleName, String methodName, Object... args) {
        Context ctx = getContext();
        String importName = rootName(simpleName);
        Value v = ctx.getBindings(PYTHON).getMember(importName);
        if (v != null) {
            return nestedMember(v, simpleName).invokeMember(methodName, args);
        } else {
            Value member = importModule(ctx, importName).getMember(importName);
            if (member == null) {
                throw new InstantiationException("Cannot find Python class: " + simpleName);
            }
            return nestedMember(member, simpleName)
                .invokeMember(methodName, args);
        }
    }

    private static Value importPackageMember(Context ctx, String packageName, String importName) {
        Value module = importModule(ctx, packageName);
        Value member = module.getMember(importName);
        if (member != null && isPythonClass(ctx, member)) {
            return member;
        }
        member = importPackageSubmoduleMember(ctx, packageName, importName);
        if (member != null && isPythonClass(ctx, member)) {
            return member;
        }
        member = findClassInPackageModules(ctx, packageName, importName);
        if (member != null && isPythonClass(ctx, member)) {
            return member;
        }
        throw new InstantiationException("Cannot find Python member: " + packageName + "." + importName);
    }

    private static @Nullable Value importPackageSubmoduleMember(Context ctx, String packageName, String importName) {
        try {
            Value submodule = importModule(ctx, packageName + "." + importName);
            Value member = submodule.getMember(importName);
            if (member != null) {
                return member;
            }
        } catch (Exception ignored) {
            // Fall back to the Python source module name below.
        }
        String pythonModuleName = NameUtils.underscoreSeparate(importName, true);
        if (!pythonModuleName.equals(importName)) {
            try {
                Value submodule = importModule(ctx, packageName + "." + pythonModuleName);
                return submodule.getMember(importName);
            } catch (Exception ignored) {
                // Fall back to package module scanning below.
            }
        }
        return null;
    }

    private static @Nullable Value findClassInPackageModules(Context ctx, String packageName, String importName) {
        Value bindings = ctx.getBindings(PYTHON);
        Value findClass = bindings.getMember("__micronaut_find_class_in_package_modules");
        if (findClass == null) {
            ctx.eval(PYTHON, """
                import importlib
                import inspect
                import pkgutil

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
                """);
            findClass = bindings.getMember("__micronaut_find_class_in_package_modules");
        }
        return findClass.execute(packageName, importName);
    }

    private static boolean isPythonClass(Context ctx, @Nullable Value value) {
        if (value == null || GraalPyRuntimeUtil.isNone(value)) {
            return false;
        }
        Value bindings = ctx.getBindings(PYTHON);
        Value isClass = bindings.getMember("__micronaut_inspect_isclass");
        if (isClass == null) {
            ctx.eval(PYTHON, "import inspect\n__micronaut_inspect_isclass = inspect.isclass");
            isClass = bindings.getMember("__micronaut_inspect_isclass");
        }
        return isClass.execute(value).asBoolean();
    }

    private static Value importModule(Context ctx, String moduleName) {
        return withContextClassLoader(() -> {
            Value bindings = ctx.getBindings(PYTHON);
            Value importModule = bindings.getMember("__micronaut_import_module");
            if (importModule == null) {
                ctx.eval(PYTHON, "import importlib\n__micronaut_import_module = importlib.import_module");
                importModule = bindings.getMember("__micronaut_import_module");
            }
            return importModule.execute(moduleName);
        });
    }

    /**
     * Set the GraalPy context. This method is called by GraalPyContextFactory
     * during application startup.
     *
     * @param context The GraalPy context to set
     */
    public static void setContext(Context context) {
        ContextHolder.context = context;
        ContextHolder.contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    /**
     * Set the GraalPy context and the application class loader that should be active when
     * generated bridge classes enter Python from arbitrary runtime threads.
     *
     * @param context The GraalPy context to set
     * @param classLoader The application class loader used to build the context
     */
    public static void setContext(Context context, @Nullable ClassLoader classLoader) {
        ContextHolder.context = context;
        ContextHolder.contextClassLoader = classLoader;
    }

    /**
     * Returns the application class loader associated with the current GraalPy context.
     *
     * @return The application class loader
     */
    public static @Nullable ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }

    /**
     * Check if the context has been initialized.
     *
     * @return true if the context is available, false otherwise
     */
    public static boolean isInitialized() {
        return context != null;
    }

    /**
     * Reset the context to null. This method is called during application shutdown
     * to ensure proper cleanup and prevent memory leaks.
     */
    public static void resetContext() {
        if (REUSE_CONTEXT.get()) {
            Context ctx = context;
            if (ctx == null) {
                return;
            }
            ctx.eval(Source.create("python", """
                import importlib
                import sys
                for module in sys.modules.values():
                    try:
                        importlib.reload(module)
                    except:
                        pass
                """));
            return;
        }
        context = null;
        contextClassLoader = null;
    }

    private static <T> T withContextClassLoader(Supplier<T> action) {
        ClassLoader classLoader = contextClassLoader;
        if (classLoader == null) {
            return action.get();
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (previous == classLoader) {
            return action.get();
        }
        thread.setContextClassLoader(classLoader);
        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /**
     * If context reuse is set to true, then the context will never be cleared.
     * @param reuse tells if the context should be reused
     */
    public static void setReuseContext(boolean reuse) {
        REUSE_CONTEXT.set(reuse);
    }

    /**
     * Returns true if the context should be reused.
     * @return the reuse flag
     */
    public static boolean isReuseContext() {
        return REUSE_CONTEXT.get();
    }
}
