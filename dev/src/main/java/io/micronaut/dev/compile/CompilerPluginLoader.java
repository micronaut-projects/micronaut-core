/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Internal;

import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The loader of what runs inside an embedded compiler, annotation processors, AST transformations or symbol processors,
 * isolated from the launcher's, which holds the application's runtime. A class of theirs found there, micronaut-inject's
 * among them, would not see the package-private members of the same package on their own path, and a provider of the
 * runtime would be discovered by their service lookups.
 *
 * <p>They and what they depend on load from their own path alone, the Java SE API from the platform loader, and the
 * compiler's own API, {@code com.sun.source} for one, from the JDK module that has it. The service files come from their
 * path only. The packages of the compiler they run in come from the launcher's loader first, since the compiler hands
 * them instances of its own classes.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
class CompilerPluginLoader extends URLClassLoader {

    /**
     * The loaders of the packages of the JDK's modules that the platform loader does not define, by package: only
     * the modules of the run-time image, so that an application launched on the module path, whose modules are in
     * the boot layer too, does not resolve its own packages from the launcher's loader.
     */
    private static final Map<String, ClassLoader> JDK_PACKAGES = jdkPackages();

    static {
        registerAsParallelCapable();
    }

    private final List<String> compilerPackagePrefixes;

    /**
     * @param urls The path of what runs inside the compiler
     * @param compilerPackagePrefixes The prefixes of the class names of the compiler they run in
     */
    CompilerPluginLoader(URL[] urls, List<String> compilerPackagePrefixes) {
        super(urls, ClassLoader.getPlatformClassLoader());
        this.compilerPackagePrefixes = compilerPackagePrefixes;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (String prefix : compilerPackagePrefixes) {
            if (name.startsWith(prefix)) {
                try {
                    return CompilerPluginLoader.class.getClassLoader().loadClass(name);
                } catch (ClassNotFoundException e) {
                    // a module of the compiler's language that only the project has, in one of its packages
                    break;
                }
            }
        }
        return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // a package of the JDK's modules comes from the JDK first, as javac's own loader would delegate it, so that
        // a copy of the compiler's classes bundled on the path does not replace them
        int lastDot = name.lastIndexOf('.');
        ClassLoader jdk = lastDot > 0 ? JDK_PACKAGES.get(name.substring(0, lastDot)) : null;
        if (jdk != null) {
            try {
                return jdk.loadClass(name);
            } catch (ClassNotFoundException e) {
                // a class the JDK's module does not have, in one of its packages, comes from the path,
                // as javac's own loader would fall back to it
            }
        }
        return super.findClass(name);
    }

    private static Map<String, ClassLoader> jdkPackages() {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ModuleFinder system = ModuleFinder.ofSystem();
        Map<String, ClassLoader> packages = new HashMap<>();
        for (Module module : ModuleLayer.boot().modules()) {
            ClassLoader loader = module.getClassLoader();
            if (loader != null && loader != platform && system.find(module.getName()).isPresent()) {
                for (String packageName : module.getPackages()) {
                    packages.put(packageName, loader);
                }
            }
        }
        return packages;
    }
}
