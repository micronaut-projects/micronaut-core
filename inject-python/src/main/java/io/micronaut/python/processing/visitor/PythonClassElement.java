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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.jetbrains.annotations.Nullable;

public final class PythonClassElement extends AbstractPythonClassElement {
    private static final String MEMBER_KEYS_PROPERTY = "memberKeys";

    private Map<String, ClassElement> resolvedTypeArguments;

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment);
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
    }

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(classDef, environment, arrayDimensions);
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
    }

    PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions, Map<String, ClassElement> resolvedTypeArguments) {
        super(classDef, environment, arrayDimensions);
        this.resolvedTypeArguments = resolvedTypeArguments;
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
    }

    @Override
    public @org.jspecify.annotations.NonNull ClassElement getType() {
        if (typeAnnotationsKey == null) {
            return this;
        }
        PythonClassElement pythonClassElement = copyThis();
        pythonClassElement.typeAnnotationsKey = null;
        return pythonClassElement;
    }

    @Override
    protected PythonClassElement copyThis() {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions);
    }

    public boolean isPythonSource() {
        return environment.classes().containsKey(getName());
    }

    @Override
    public boolean isInner() {
        return getNativeType().name().indexOf('$') > -1;
    }

    @Override
    public boolean isStatic() {
        return isInner();
    }

    @Override
    public Optional<ClassElement> getEnclosingType() {
        String name = getNativeType().name();
        int innerSeparator = name.lastIndexOf('$');
        if (innerSeparator < 0) {
            return Optional.empty();
        }
        String enclosingName = name.substring(0, innerSeparator);
        String qualifiedEnclosingName = getNativeType().packageName().isEmpty()
            ? enclosingName
            : getPackageName() + "." + enclosingName;
        return Optional.ofNullable(environment.classes().get(qualifiedEnclosingName));
    }

    @Override
    protected ClassElement createWithArrayDimensions(int arrayDimensions) {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    public String toString() {
        return "Python Class: " + getNativeType().name();
    }

    @Override
    public Optional<MethodElement> getDefaultConstructor() {
        Optional<MethodElement> primaryConstructor = getPrimaryConstructor();
        if (primaryConstructor.isEmpty()) {
            if (!hasDeclaredAnnotation("dataclass")) {
                // python class with no explicit constructor return default
                return Optional.of(new PythonConstructorElement(new FunctionDef(FunctionDef.CONSTRUCTOR_NAME), environment, this, this, environment.metadataFactory()));
            }
        }
        return super.getDefaultConstructor();
    }

    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        // First check for @Creator methods (static factory methods)
        List<FunctionDef> functions = getNativeType().functions();
        for (FunctionDef function : functions) {
            if (function.isStatic()) {
                // Check if this static method has @Creator annotation
                for (DecoratorDef decorator : function.decorators()) {
                    if ("Creator".equals(decorator.name()) ||
                        "io.micronaut.core.annotation.Creator".equals(decorator.annotationName())) {
                        return Optional.of(new PythonMethodElement(function, environment, this, this, environment.metadataFactory()));
                    }
                }
            }
        }

        // Fall back to regular constructor
        FunctionDef constructor = getNativeType().constructor();
        if (constructor != null) {
            return Optional.of(new PythonConstructorElement(constructor, environment, this, this, environment.metadataFactory()));
        }
        return Optional.empty();
    }

    @Override
    public boolean isAssignable(String type) {
        if (Object.class.getName().equals(type) || getName().equals(type)) {
            return true;
        }
        for (TypeRef base : getNativeType().bases()) {
            if (base.name().equals(type)) {
                return true;
            }
            ClassElement baseElement = findPythonClass(base);
            if (baseElement != null && baseElement.isAssignable(type)) {
                return true;
            }
        }

        for (ClassElement anInterface : getInterfaces()) {
            if (anInterface.isAssignable(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        List<TypeRef> bases = getNativeType().bases();
        List<ClassElement> interfaces = new ArrayList<>();
        for (TypeRef basis : bases) {
            ClassElement baseElement = findPythonClass(basis);
            // python types can't be interfaces so skip if null and search java
            if (baseElement == null) {
                ClassElement javaInterface = toJavaType(basis).orElse(null);
                if (javaInterface != null && javaInterface.isInterface()) {
                    interfaces.add(javaInterface);
                }
            }
        }
        return interfaces;
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        List<TypeRef> bases = getNativeType().bases();

        if (!bases.isEmpty()) {
            for (TypeRef base : bases) {
                ClassElement baseElement = findPythonClass(base);
                if (baseElement != null) {
                    List<? extends GenericPlaceholderElement> declaredGenericPlaceholders = baseElement.getDeclaredGenericPlaceholders();
                    List<TypeRef> typeArguments = base.typeArguments();
                    if (!typeArguments.isEmpty() && declaredGenericPlaceholders != null && !declaredGenericPlaceholders.isEmpty() && typeArguments.size() == declaredGenericPlaceholders.size()) {
                        Map<String, ClassElement> resolvedTypeArguments = new HashMap<>(declaredGenericPlaceholders.size());
                        for (int i = 0; i < declaredGenericPlaceholders.size(); i++) {
                            GenericPlaceholderElement placeHolder = declaredGenericPlaceholders.get(i);
                            TypeRef typeRef = typeArguments.get(i);
                            ClassElement resolvedType = GraalPyUtil.resolvePythonTypeToJava(typeRef, environment.visitorContext(), Map.of());
                            String variableName = placeHolder.getVariableName();
                            resolvedTypeArguments.put(variableName, resolvedType);
                        }
                        return Optional.of(baseElement.withTypeArguments(resolvedTypeArguments));
                    } else {
                        return Optional.of(baseElement);
                    }
                } else  {
                    // maybe a java type
                    ClassElement javaSuper = toJavaType(base).orElse(null);
                    if (javaSuper != null && !javaSuper.isInterface()) {
                        return Optional.of(javaSuper);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ClassElement> toJavaType(TypeRef typeRef) {
        ClassElement baseType = GraalPyUtil.resolvePythonTypeToJava(
            typeRef,
            environment.visitorContext(),
            Map.of()
        );
        if (baseType != null && !baseType.getName().equals(Object.class.getName())) {
            return Optional.of(baseType);
        }
        return Optional.empty();
    }

    @Override
    public Map<String, ClassElement> getTypeArguments() {
        if (resolvedTypeArguments == null) {
            return super.getTypeArguments();
        } else {
            return resolvedTypeArguments;
        }
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions, typeArguments);
    }

    @NonNull
    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        return getNativeType().typeParams().stream()
            .map(typeVar -> {
                // For now, we'll create empty bounds. Bounds handling can be added later
                List<PythonClassElement> bounds = List.of();
                return new PythonGenericPlaceholderElement(typeVar, environment, bounds, this);
            })
            .toList();
    }

}
