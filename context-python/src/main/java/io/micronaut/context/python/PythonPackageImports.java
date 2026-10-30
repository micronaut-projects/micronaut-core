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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the classes and modules of Python packages for {@link PythonContextRuntime}.
 *
 * @author Micronaut Team
 * @since 5.3.0
 */
final class PythonPackageImports {

    private PythonPackageImports() {
    }

    static Value importPackageMember(Context ctx, String packageName, String importName) {
        // The package is imported first, unless its import is running already: a package that is
        // imported serves the class without importing anything, so a submodule that happens to carry
        // the class name is not executed for a class the package defines, and the import holds the
        // lock of the package alone. Importing the module named after the class first would take the
        // lock of the module and then wait for the package, which a second thread resolving another
        // class of the package the same way waits for while holding the lock of its module; the
        // initialiser of a generated package imports every module of the package, so the threads
        // would wait for each other (_DeadlockError). While the import of the package runs, on this
        // thread or on another one, the module named after the class is imported instead, which the
        // import system executes without the lock of its package: a class instantiated on another
        // thread while its package is being imported (a service the parallel service loader creates
        // for a call made at import time) does not wait for the import lock the importing thread holds
        // while it waits for the instantiation. Only a missing submodule is tolerated on the way: an
        // error raised while executing one propagates.
        Value module = importPackageOfMember(ctx, packageName);
        if (PythonConversion.isNone(module)) {
            // The import of the package runs on this thread or on another one.
            return importMemberWhilePackageImports(ctx, packageName, importName);
        }
        Value member = classMember(ctx, module, importName);
        if (member == null) {
            member = importPackageSubmoduleMember(ctx, packageName, importName);
        }
        if (member == null) {
            member = findClassInPackageModules(ctx, packageName, importName);
        }
        return requireMember(ctx, member, packageName, importName);
    }

    private static Value importMemberWhilePackageImports(Context ctx, String packageName, String importName) {
        Value member = importPackageSubmoduleMember(ctx, packageName, importName);
        if (member == null) {
            member = classMember(ctx, importModule(ctx, packageName), importName);
        }
        if (member == null) {
            member = findClassInPackageModules(ctx, packageName, importName);
        }
        return requireMember(ctx, member, packageName, importName);
    }

    private static @Nullable Value classMember(Context ctx, Value module, String importName) {
        Value member = module.getMember(importName);
        return isPythonClass(ctx, member) ? member : null;
    }

    private static Value requireMember(Context ctx, @Nullable Value member, String packageName, String importName) {
        if (member != null && isPythonClass(ctx, member)) {
            return member;
        }
        throw new InstantiationException("Cannot find Python member: " + packageName + "." + importName);
    }

    private static @Nullable Value importPackageSubmoduleMember(Context ctx, String packageName, String importName) {
        Value member = importSubmoduleMember(ctx, packageName + "." + importName, importName);
        if (member != null) {
            return member;
        }
        String pythonModuleName = NameUtils.underscoreSeparate(importName, true);
        if (!pythonModuleName.equals(importName)) {
            return importSubmoduleMember(ctx, packageName + "." + pythonModuleName, importName);
        }
        return null;
    }

    /**
     * A member of a module that may not exist: {@code null} when the module (or its package) is not
     * found; any other error of the import propagates.
     */
    private static @Nullable Value importSubmoduleMember(Context ctx, String moduleName, String memberName) {
        try {
            return importModule(ctx, moduleName).getMember(memberName);
        } catch (PolyglotException e) {
            if (isModuleNotFound(e, moduleName)) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Whether an import failed because the module itself, or a package on its path, does not exist:
     * a {@code ModuleNotFoundError} naming that module, not one raised by the code of the module.
     */
    private static boolean isModuleNotFound(PolyglotException e, String moduleName) {
        Value error = e.isGuestException() ? e.getGuestObject() : null;
        if (error == null) {
            return false;
        }
        Value meta = error.getMetaObject();
        if (meta == null || !"ModuleNotFoundError".equals(meta.getMetaSimpleName())) {
            return false;
        }
        Value name = error.hasMember("name") ? error.getMember("name") : null;
        if (name == null || !name.isString()) {
            return false;
        }
        String missing = name.asString();
        return moduleName.equals(missing) || moduleName.startsWith(missing + ".");
    }

    /**
     * The package a class is resolved from, imported unless its import is running already:
     * {@code None} while this or another thread is still executing it.
     */
    private static Value importPackageOfMember(Context ctx, String packageName) {
        return PythonContextRuntime.withContextClassLoader(() -> PythonContextRuntime.helper(ctx, "__micronaut_import_package_of_member").execute(packageName));
    }

    private static @Nullable Value findClassInPackageModules(Context ctx, String packageName, String importName) {
        Value findClass = PythonContextRuntime.helper(ctx, "__micronaut_find_class_in_package_modules");
        return findClass.execute(packageName, importName);
    }

    private static boolean isPythonClass(Context ctx, @Nullable Value value) {
        if (value == null || PythonConversion.isNone(value)) {
            return false;
        }
        Value isClass = PythonContextRuntime.helper(ctx, "__micronaut_inspect_isclass");
        return isClass.execute(value).asBoolean();
    }

    static Value importModule(Context ctx, String moduleName) {
        return PythonContextRuntime.withContextClassLoader(() -> {
            Value importModule = PythonContextRuntime.helper(ctx, "__micronaut_import_module");
            return importModule.execute(moduleName);
        });
    }
}
