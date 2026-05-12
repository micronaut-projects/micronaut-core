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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.micronaut.aop.Introduction;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

public final class PythonClassElement extends AbstractPythonClassElement {
    private static final String MEMBER_KEYS_PROPERTY = "memberKeys";
    private static final String INTRODUCTION_INTERFACE_MARKER = "java.io.Serializable";

    private Map<String, ClassElement> resolvedTypeArguments;
    private final List<ClassElement> introductionInterfaces = new ArrayList<>();

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment);
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
        moveIntroductionInterfacesToImplementedInterfaces();
    }

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(classDef, environment, arrayDimensions);
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
        moveIntroductionInterfacesToImplementedInterfaces();
    }

    PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions, Map<String, ClassElement> resolvedTypeArguments) {
        super(classDef, environment, arrayDimensions);
        this.resolvedTypeArguments = resolvedTypeArguments;
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
        moveIntroductionInterfacesToImplementedInterfaces();
    }

    @Override
    public @org.jspecify.annotations.NonNull ClassElement getType() {
        if (typeAnnotationsKey == null) {
            return this;
        }
        PythonClassElement pythonClassElement = (PythonClassElement) makeCopy();
        pythonClassElement.typeAnnotationsKey = null;
        return pythonClassElement;
    }

    @Override
    protected PythonClassElement copyThis() {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    protected void copyValues(AbstractPythonElement element) {
        super.copyValues(element);
        if (element instanceof PythonClassElement pythonClassElement) {
            pythonClassElement.resolvedTypeArguments = resolvedTypeArguments;
            pythonClassElement.introductionInterfaces.clear();
            pythonClassElement.introductionInterfaces.addAll(introductionInterfaces);
        }
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
        Set<ClassElement> interfaces = new LinkedHashSet<>(introductionInterfaces);
        for (TypeRef basis : bases) {
            ClassElement baseElement = findPythonClass(basis);
            if (baseElement != null) {
                if (baseElement.isInterface()) {
                    interfaces.add(resolveTypeArguments(baseElement, basis));
                }
            } else {
                ClassElement javaInterface = toJavaType(basis).orElse(null);
                if (javaInterface != null && javaInterface.isInterface()) {
                    interfaces.add(javaInterface);
                }
            }
        }
        return interfaces;
    }

    @Override
    public boolean isInterface() {
        if (BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(getAnnotationMetadata())
            || hasStereotype(InterceptorBinding.class)
            || hasStereotype(Introspected.class)
            || getPrimaryConstructor().isPresent()
            || !getNativeType().attributes().isEmpty()
            || !getNativeType().properties().isEmpty()) {
            return false;
        }
        List<MethodElement> declaredMethods = getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared());
        if (declaredMethods.isEmpty()) {
            return hasInterfaceBase();
        }
        return !declaredMethods.isEmpty()
            && declaredMethods.stream().allMatch(MethodElement::isAbstract)
            && declaredMethods.stream().noneMatch(PythonClassElement::isIntroductionFactoryMethod);
    }

    private boolean hasInterfaceBase() {
        for (TypeRef basis : getNativeType().bases()) {
            ClassElement baseElement = findPythonClass(basis);
            if (baseElement != null) {
                if (baseElement.isInterface()) {
                    return true;
                }
            } else {
                ClassElement javaInterface = toJavaType(basis).orElse(null);
                if (javaInterface != null && javaInterface.isInterface()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIntroductionFactoryMethod(MethodElement method) {
        return method.hasAnnotation("io.micronaut.context.annotation.Mapper")
            || method.hasAnnotation("io.micronaut.context.annotation.Mapper$Mapping")
            || method.hasDeclaredStereotype(Bean.class)
            || method.hasStereotype(InterceptorBinding.class);
    }

    private void moveIntroductionInterfacesToImplementedInterfaces() {
        JavaVisitorContext javaVisitorContext = environment.javaVisitorContext();
        if (javaVisitorContext == null) {
            return;
        }
        Set<String> interfaceNames = new LinkedHashSet<>();
        collectIntroductionInterfaceNames(javaVisitorContext, getNativeType().decorators(), interfaceNames);
        if (interfaceNames.isEmpty()) {
            return;
        }
        for (String interfaceName : interfaceNames) {
            javaVisitorContext.getClassElement(interfaceName)
                .filter(ClassElement::isInterface)
                .ifPresent(introductionInterfaces::add);
        }
        if (!introductionInterfaces.isEmpty()) {
            annotate(Introduction.class, builder ->
                builder.member("interfaces", new AnnotationClassValue<?>[]{new AnnotationClassValue<>(INTRODUCTION_INTERFACE_MARKER)})
            );
        }
    }

    private void collectIntroductionInterfaceNames(JavaVisitorContext javaVisitorContext, List<DecoratorDef> decorators, Set<String> interfaceNames) {
        for (DecoratorDef decorator : decorators) {
            if (Introduction.class.getName().equals(decorator.annotationName())) {
                collectIntroductionInterfaceNames(decorator.members().get("interfaces"), interfaceNames);
            }
            javaVisitorContext.getClassElement(decorator.annotationName())
                .map(annotationElement -> annotationElement.getAnnotation(Introduction.class))
                .ifPresent(introduction -> collectIntroductionInterfaceNames(introduction, interfaceNames));
            collectIntroductionInterfaceNames(javaVisitorContext, decorator.stereotypes(), interfaceNames);
        }
    }

    private void collectIntroductionInterfaceNames(AnnotationValue<Introduction> introduction, Set<String> interfaceNames) {
        for (AnnotationClassValue<?> interfaceValue : introduction.annotationClassValues("interfaces")) {
            interfaceNames.add(interfaceValue.getName());
        }
    }

    private void collectIntroductionInterfaceNames(Object value, Set<String> interfaceNames) {
        if (value == null) {
            return;
        }
        if (value instanceof AnnotationClassValue<?> annotationClassValue) {
            interfaceNames.add(annotationClassValue.getName());
        } else if (value instanceof AnnotationClassValue<?>[] annotationClassValues) {
            for (AnnotationClassValue<?> annotationClassValue : annotationClassValues) {
                interfaceNames.add(annotationClassValue.getName());
            }
        } else if (value instanceof String interfaceName) {
            interfaceNames.add(interfaceName);
        } else if (value instanceof String[] names) {
            for (String name : names) {
                interfaceNames.add(name);
            }
        } else if (value instanceof Value polyglotValue) {
            collectIntroductionInterfaceNames(polyglotValue, interfaceNames);
        }
    }

    private void collectIntroductionInterfaceNames(Value value, Set<String> interfaceNames) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.hasArrayElements()) {
            for (long i = 0; i < value.getArraySize(); i++) {
                collectIntroductionInterfaceNames(value.getArrayElement(i), interfaceNames);
            }
        } else if (value.isHostObject()) {
            Object hostObject = value.asHostObject();
            if (hostObject instanceof Class<?> clazz) {
                interfaceNames.add(clazz.getName());
            } else {
                collectIntroductionInterfaceNames(hostObject, interfaceNames);
            }
        } else if (value.isString()) {
            interfaceNames.add(value.asString());
        }
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        List<TypeRef> bases = getNativeType().bases();

        if (!bases.isEmpty()) {
            for (TypeRef base : bases) {
                ClassElement baseElement = findPythonClass(base);
                if (baseElement != null) {
                    if (!baseElement.isInterface()) {
                        return Optional.of(resolveTypeArguments(baseElement, base));
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

    private ClassElement resolveTypeArguments(ClassElement baseElement, TypeRef base) {
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
            return baseElement.withTypeArguments(resolvedTypeArguments);
        }
        return baseElement;
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
