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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.typecheck.JavaInstanceAliases;
import io.micronaut.python.processing.util.PythonAnnotationTypes;
import io.micronaut.python.processing.util.PythonJavaTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Collects the Python names of the instance methods of the public Java APIs a compilation references
 * (see {@link JavaInstanceAliases}), for the Java imports manifest. Like the functional-interface
 * provider, covers imports, Java bases and hints, and one level of method return types, plus the
 * receivers whose aliases the type checker resolved in deeper call chains. The runtime registers each
 * type's names for the type, so they also apply to its implementations, whose members it never inspects.
 *
 * @since 5.3.0
 */
@Internal
final class PythonJavaInstanceMembersCollector {
    // the types whose values GraalPy hands to Python as Python values, never as Java objects
    private static final Set<String> PYTHON_VALUE_TYPES = Set.of(String.class.getName(), Boolean.class.getName(),
        Character.class.getName(), Byte.class.getName(), Short.class.getName(), Integer.class.getName(),
        Long.class.getName(), Float.class.getName(), Double.class.getName());
    private final VisitorContext context;
    private final Map<String, Map<String, JavaInstanceAliases.Alias>> members = new TreeMap<>();
    private final Map<String, Integer> walked = new HashMap<>();

    PythonJavaInstanceMembersCollector(VisitorContext context) {
        this.context = context;
    }

    /**
     * The names collected so far.
     *
     * @return The aliases by binary name of the Java type, then by Python name
     */
    Map<String, Map<String, JavaInstanceAliases.Alias>> members() {
        return members;
    }

    void referenceImport(String name) {
        context.getClassElement(name)
            .or(() -> context.getClassElement(name.replace('$', '.')))
            .ifPresent(type -> reference(type, 0));
    }

    void referenceAliasReceiver(String name) {
        context.getClassElement(name).ifPresent(type -> reference(type, 1));
    }

    void referencePythonType(ClassElement type) {
        type.getSuperType().filter(parent -> !parent.getName().equals(Object.class.getName()))
            .ifPresent(parent -> reference(parent, 0));
        type.getInterfaces().forEach(parent -> reference(parent, 0));
        for (MethodElement method : type.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            referenceSignature(method);
        }
        for (MethodElement constructor : type.getEnclosedElements(ElementQuery.CONSTRUCTORS)) {
            referenceSignature(constructor);
        }
        type.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared()).forEach(field -> reference(field.getType(), 0));
        type.getBeanProperties().forEach(property -> reference(property.getType(), 0));
    }

    private void referenceSignature(MethodElement method) {
        for (var parameter : method.getParameters()) {
            reference(parameter.getType(), 0);
        }
        reference(method.getReturnType(), 0);
    }

    private void reference(ClassElement referenced, int depth) {
        if (referenced.isPrimitive() || referenced.isVoid() || referenced.isArray() || PythonJavaTypes.isPythonClass(referenced)) {
            return;
        }
        ClassElement type = referenced.getRawClassElement();
        if (!accessible(type) || PythonAnnotationTypes.isAnnotationType(type) || PYTHON_VALUE_TYPES.contains(type.getName())) {
            return;
        }
        Integer previous = walked.get(type.getName());
        if (previous != null && previous <= depth) {
            return;
        }
        walked.put(type.getName(), depth);
        List<MethodElement> methods = type.getEnclosedElements(ElementQuery.ALL_METHODS.filter(MethodElement::isPublic));
        Map<String, JavaInstanceAliases.Alias> aliases = JavaInstanceAliases.of(methods);
        if (!aliases.isEmpty()) {
            members.put(type.getName(), aliases);
        }
        if (depth == 0) {
            for (MethodElement method : methods) {
                reference(method.getReturnType(), 1);
            }
        }
    }

    private static boolean accessible(ClassElement type) {
        ClassElement current = type;
        while (current != null) {
            if (!current.isPublic()) {
                return false;
            }
            current = current.getEnclosingType().orElse(null);
        }
        return true;
    }
}
