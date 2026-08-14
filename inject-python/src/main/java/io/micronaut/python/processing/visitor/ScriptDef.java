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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.naming.NameUtils;

import java.util.ArrayList;
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
 */
@Experimental
public record ScriptDef(
    String name,
    String packageName,
    List<FunctionDef> functions,
    List<AttributeDef> attributes,
    String documentation,
    List<DecoratorDef> decorators
) implements ElementDef {

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
        this(name, "", List.of(), List.of(), null, List.of());
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
            decorators
        );
    }

    public ScriptDef withAttribute(AttributeDef attribute) {
        Objects.requireNonNull(attribute, "Attribute cannot be null");
        if (attribute.typeName() == null) {
            return this;
        }
        List<AttributeDef> attributes = new ArrayList<>(this.attributes);
        attributes.add(attribute);
        return new ScriptDef(name, packageName, functions, attributes, documentation, decorators);
    }

    public String qualifiedName() {
        String n = name;
        if (n.endsWith(".py")) {
            n = NameUtils.capitalize(name.substring(0, name.length() - 3));
        } else if (name.equals("Unnamed")) {
            n = "Script";
        }
        return packageName + "." + n;
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
