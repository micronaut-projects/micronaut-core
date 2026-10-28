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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Arrays;

/**
 * The compiled Python classes of the Python context calling from Python, resolved like the classes
 * generated code refers to ({@link PythonContextRuntime#findClass(PythonContextRuntime.PythonClassReference, Context)}):
 * through the per-context class cache, importing a package before the module named after the class.
 * Used by {@code micronaut_java_imports} for the dataclass of a generated wrapper the Java imports
 * manifest records, whose fields and parameters must be those of the context reading them.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonCallerClasses {

    private PythonCallerClasses() {
    }

    /**
     * @param packageName The Python package, {@code python} for a top-level class
     * @param pythonName The class name, nested classes separated by dots
     * @return The class, belonging to the calling context
     */
    @UsedByGeneratedCode
    public static Value callerClass(String packageName, String pythonName) {
        Context caller;
        try {
            caller = Context.getCurrent();
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Python class [" + pythonName + "] requested outside of a Python context", e);
        }
        String[] names = pythonName.split("\\.", -1);
        var classReference = new PythonContextRuntime.PythonClassReference(packageName, names[0],
            Arrays.copyOfRange(names, 1, names.length), pythonName, "class-instance:" + packageName + "." + pythonName);
        return PythonContextRegistry.withExecutionFrame(caller, () -> PythonContextRuntime.findClass(classReference, caller));
    }
}
