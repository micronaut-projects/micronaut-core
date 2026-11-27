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
package io.micronaut.python.processing.model;

import io.micronaut.core.annotation.Experimental;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A ScriptDef node represents a Python script/module definition.
 * Scripts are module-level constructs that can have attributes (module variables)
 * and functions (module-level functions).
 * <p>
 * ScriptDef(identifier name, list[FunctionDef] functions, list[AttributeDef] attributes)
 * </p>
 *
 * @param name The name of the script/module.
 * @param packageName The package name of the script.
 * @param functions The functions defined at module level.
 * @param attributes The attributes defined at module level.
 * @param documentation The script documentation string.
 * @param decorators The annotations invoked at module level.
 * @param moduleClassNames The names of the top-level classes defined in the same source, which decide
 *                         whether the Java class of the module needs the {@value #MODULE_SUFFIX} suffix
 *                         (see {@link #toJavaClassName(String, Collection)}).
 */
@Experimental
public record ScriptDef(
    String name,
    String packageName,
    List<FunctionDef> functions,
    List<AttributeDef> attributes,
    String documentation,
    List<DecoratorDef> decorators,
    List<String> moduleClassNames
) implements ElementDef {

    /**
     * The suffix of the Java class of a module that defines a top-level class named like the module.
     */
    public static final String MODULE_SUFFIX = "Module";

    public ScriptDef {
        Objects.requireNonNull(name, "Script name cannot be null");
        if (functions == null) {
            functions = List.of();
        }
        if (attributes == null) {
            attributes = List.of();
        } else {
            attributes = attributes.stream().filter(ad -> ad.typeName() != null).toList();
        }
        if (decorators == null) {
            decorators = List.of();
        } else {
            decorators = List.copyOf(decorators);
        }
        moduleClassNames = moduleClassNames == null ? List.of() : List.copyOf(moduleClassNames);
    }

    /**
     * Constructor for a script whose source defines no top-level classes yet; see {@link #withModuleClassNames(Collection)}.
     *
     * @param name The script name
     * @param packageName The package name
     * @param functions The functions
     * @param attributes The attributes
     * @param documentation The documentation
     * @param decorators The annotations invoked at module level
     */
    public ScriptDef(String name,
                     String packageName,
                     List<FunctionDef> functions,
                     List<AttributeDef> attributes,
                     String documentation,
                     List<DecoratorDef> decorators) {
        this(name, packageName, functions, attributes, documentation, decorators, List.of());
    }

    /**
     * Backwards-compatible constructor for scripts without module annotations.
     *
     * @param name The script name
     * @param packageName The package name
     * @param functions The functions
     * @param attributes The attributes
     * @param documentation The documentation
     */
    public ScriptDef(String name,
                     String packageName,
                     List<FunctionDef> functions,
                     List<AttributeDef> attributes,
                     String documentation) {
        this(name, packageName, functions, attributes, documentation, List.of());
    }

    public ScriptDef(String name) {
        this(name, "", List.of(), List.of(), null, List.of(), List.of());
    }

    /**
     * The script with the names of the top-level classes defined in its source.
     *
     * @param classNames The class names
     * @return The script
     */
    public ScriptDef withModuleClassNames(Collection<String> classNames) {
        return new ScriptDef(name, packageName, functions, attributes, documentation, decorators, List.copyOf(classNames));
    }

    public ScriptDef withFunction(FunctionDef function) {
        Objects.requireNonNull(function, "Function cannot be null");
        List<FunctionDef> functions = new ArrayList<>(this.functions);
        functions.add(function);
        return new ScriptDef(
            name,
            packageName,
            functions,
            attributes,
            documentation,
            decorators,
            moduleClassNames
        );
    }

    public ScriptDef withAttribute(AttributeDef attribute) {
        Objects.requireNonNull(attribute, "Attribute cannot be null");
        if (attribute.typeName() == null) {
            return this;
        }
        List<AttributeDef> attributes = new ArrayList<>(this.attributes);
        attributes.add(attribute);
        return new ScriptDef(name, packageName, functions, attributes, documentation, decorators, moduleClassNames);
    }

    public String qualifiedName() {
        return packageName + "." + javaSimpleName();
    }

    /**
     * The simple name of the Java class generated for the script; see {@link #toJavaClassName(String, Collection)}.
     *
     * @return The simple name
     */
    public String javaSimpleName() {
        if (name.endsWith(".py")) {
            return toJavaClassName(name.substring(0, name.length() - 3), moduleClassNames);
        }
        return name.equals("Unnamed") ? "Script" : name;
    }

    /**
     * The top-level class of the same source whose Java name the module would otherwise have.
     *
     * @return The class name, or {@code null} when the module keeps its plain Java name
     */
    public String clashingClassName() {
        if (!name.endsWith(".py")) {
            return null;
        }
        String plainName = toJavaClassName(name.substring(0, name.length() - 3));
        return moduleClassNames.contains(plainName) ? plainName : null;
    }

    /**
     * The Java simple name of the class generated for a Python module. It is the module name in upper camel
     * case ({@link #toJavaClassName(String)}), unless the module itself defines a top-level class of that
     * name ({@code widget.py} defining {@code Widget}): the class keeps its name and the module's Java class
     * gets the {@value #MODULE_SUFFIX} suffix ({@code WidgetModule}). The name depends on the classes of the
     * module only, so processing, incremental compilation and the generated stubs all agree on it.
     *
     * @param moduleName The Python module name without the {@code .py} suffix
     * @param moduleClassNames The names of the top-level classes the module defines
     * @return A valid Java class name
     */
    public static String toJavaClassName(String moduleName, Collection<String> moduleClassNames) {
        String plainName = toJavaClassName(moduleName);
        return moduleClassNames.contains(plainName) ? plainName + MODULE_SUFFIX : plainName;
    }

    /**
     * Converts a Python module name into the Java simple name used for its generated stub.
     * Separators and underscores are treated as word boundaries, and names beginning with a
     * digit are prefixed with an underscore.
     *
     * @param moduleName The Python module name without the {@code .py} suffix
     * @return A valid Java class name
     */
    public static String toJavaClassName(String moduleName) {
        Objects.requireNonNull(moduleName, "moduleName");
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < moduleName.length(); i++) {
            char c = moduleName.charAt(i);
            if (c == '_') {
                capitalize = true;
                continue;
            }
            if (!Character.isJavaIdentifierPart(c)) {
                result.append('X').append(Integer.toHexString(c)).append('X');
                capitalize = true;
                continue;
            }
            if (result.isEmpty() && !Character.isJavaIdentifierStart(c)) {
                result.append('_');
            }
            result.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = false;
        }
        return result.isEmpty() ? "Script" : result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScriptDef scriptDef = (ScriptDef) o;
        return Objects.equals(name, scriptDef.name) && Objects.equals(packageName, scriptDef.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, packageName);
    }
}
