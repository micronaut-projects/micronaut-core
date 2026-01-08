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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.exception.InstantiationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jetbrains.annotations.NotNull;

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
    private static volatile Context context;

    private ContextHolder() {
        // Utility class
    }

    /**
     * Get the shared GraalPy context for Python execution.
     * The context is initialized lazily by the GraalPyContextFactory bean.
     *
     * @return The GraalPy context
     * @throws IllegalStateException if the context has not been initialized
     */
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
                    // populate a stub that will be filled out later by PythonAopSetup
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

    private static Value instantiate(String packageName, String simpleName, Object[] args, Value pythonClass) {
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

    public static Value findClass(String packageName, String simpleName) {
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

    public static @NotNull Value findClass(String simpleName) {
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
     * Find a Python script.
     * @param packageName The package name
     * @param scriptName The script name
     * @return The value
     * @throws InstantiationException if the script cannot be found
     */
    public static @NotNull Value findScript(String packageName, String scriptName) {
        Context ctx = getContext();
        Value v = ctx.getBindings(PYTHON);
        if (v != null) {
            if (PYTHON.equals(packageName)) {
                if ("Unnamed".equals(scriptName)) {
                    return v;
                } else {
                    Value member = ctx.eval(PYTHON, "import " + scriptName )
                        .getMember(scriptName);
                    if (member == null) {
                        throw new InstantiationException("Cannot find Python module: " + packageName);
                    }
                    return member;
                }
            } else {
                Value member = ctx.eval(PYTHON, "from " + packageName + " import " + scriptName )
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
    public static Value invokeStaticMethod(String packageName, String simpleName, String methodName, Object... args) {
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
     * @param simpleName The simple name
     * @param methodName The method name
     * @param args       The args
     * @return The new instance
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
            // Not sufficient for now, probably because of use of Class as resourceLoader
            context.eval(Source.create("python", """
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
     * Returns true if the context should be reused
     * @return the reuse flag
     */
    public static boolean isReuseContext() {
        return REUSE_CONTEXT.get();
    }
}
