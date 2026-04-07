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
import io.micronaut.core.reflect.exception.InstantiationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

/**
 * Static holder for the GraalPy context used by Python bridge classes.
 * Provides thread-safe access to the shared Python execution context.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class ContextHolder {

    private static final AtomicBoolean REUSE_CONTEXT = new AtomicBoolean();
    private static volatile @Nullable Context context;
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
     * Injects an attribute value into the most recently created pooled context for a given script.
     * This avoids broadcasting to all contexts and aligns with synchronous object creation flows.
     *
     * @param packageName The package
     * @param scriptName The script name
     * @param attribute The attribute name
     * @param value The value to inject
     */
    @UsedByGeneratedCode
    public static void injectPooledScript(String packageName, String scriptName, String attribute, Object value) {
        if (isReuseContext() || pythonPool == null) {
            Value script = findScript(packageName, scriptName);
            script.putMember(attribute, (value instanceof Value v) ? v : Value.asValue(value));
            return;
        }
        getPythonPool().injectMostRecent(packageName, scriptName, attribute, value);
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
    public static Value invokePooled(String packageName, String simpleName, String methodName, Object... args) {
        return withPooled(packageName, simpleName, v -> v.getMember(methodName).execute(args));
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
        return withPooledScript(packageName, scriptName, v -> v.getMember(methodName).execute(args));
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
        }
        return instantiate(packageName, simpleName, args, pythonClass);
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
        Value instance = instantiate(packageName, simpleName, new Object[0], pythonClass);
        if (props != null && !props.isEmpty()) {
            for (java.util.Map.Entry<String, Object> e : props.entrySet()) {
                instance.putMember(e.getKey(), e.getValue());
            }
        }
        return instance;
    }

    private static Value instantiate(@Nullable String packageName, String simpleName, Object[] args, Value pythonClass) {
        if (pythonClass.canInstantiate()) {
            return pythonClass.newInstance(args);
        } else {
            String qualifiedName = packageName == null || PYTHON.equals(packageName) ? simpleName :  packageName + "." + simpleName;
            throw new InstantiationException("Cannot instantiate class: " + qualifiedName + ". Ensure the class is a valid Python class and is non-abstract.");
        }
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

    public static Value findClass(@org.jspecify.annotations.Nullable String packageName, String simpleName) {
        if (packageName == null || PYTHON.equals(packageName)) {
            return findClass(simpleName);
        }

        Context ctx = getContext();
        String source = "from " + packageName + " import " + simpleName + "; " + simpleName;
        try {
            return ctx.eval(PYTHON, source);
        } catch (Exception e) {
            throw new InstantiationException("Failed to import Python class [" + source + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Find a Python class by fully qualified name.
     * @param simpleName The class name
     * @return The class Value
     */
    @UsedByGeneratedCode
    public static Value findClass(String simpleName) {
        Context ctx = getContext();
        Value v = ctx.getBindings(PYTHON).getMember(simpleName);
        if (v == null) {
            Value member = ctx.eval(PYTHON, "import " + simpleName + "; " + simpleName)
                .getMember(simpleName);
            if (member == null) {
                throw new InstantiationException("Cannot find Python class: " + simpleName);
            }
            v = member;
        }
        return v;
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
                    Value member = ctx.eval(PYTHON, "import " + scriptName)
                        .getMember(scriptName);
                    if (member == null) {
                        throw new InstantiationException("Cannot find Python module: " + packageName);
                    }
                    return member;
                }
            } else {
                Value member = ctx.eval(PYTHON, "from " + packageName + " import " + scriptName)
                    .getMember(scriptName);
                if (member == null) {
                    throw new InstantiationException("Cannot find Python module: " + packageName);
                }
                return member;
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
            Value pythonClass = ctx.eval(PYTHON, "from " + packageName + " import " + simpleName + "; " + simpleName);
            return pythonClass.invokeMember(methodName, args);
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
        Value v = ctx.getBindings(PYTHON).getMember(simpleName);
        if (v != null) {
            return v.invokeMember(methodName);
        } else {
            Value member = ctx.eval(PYTHON, "import " + simpleName + "; " + simpleName)
                .getMember(simpleName);
            if (member == null) {
                throw new InstantiationException("Cannot find Python class: " + simpleName);
            }
            return member
                .invokeMember(methodName, args);
        }
    }

    /**
     * Set the GraalPy context. This method is called by GraalPyContextFactory
     * during application startup.
     *
     * @param context The GraalPy context to set
     */
    public static void setContext(Context context) {
        ContextHolder.context = context;
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
