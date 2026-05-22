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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.micronaut.aop.Introduction;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.graalvm.polyglot.Value;

public final class PythonClassElement extends AbstractPythonClassElement {
    private static final String MEMBER_KEYS_PROPERTY = "memberKeys";
    private static final String INTRODUCTION_INTERFACE_MARKER = "java.io.Serializable";

    private Map<String, ClassElement> resolvedTypeArguments;
    private final List<ClassElement> introductionInterfaces = new ArrayList<>();

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment);
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
        markPropertyInjectionBeanCandidate();
        moveIntroductionInterfacesToImplementedInterfaces();
    }

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(classDef, environment, arrayDimensions);
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
        markPropertyInjectionBeanCandidate();
        moveIntroductionInterfacesToImplementedInterfaces();
    }

    PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions, Map<String, ClassElement> resolvedTypeArguments) {
        super(classDef, environment, arrayDimensions);
        this.resolvedTypeArguments = resolvedTypeArguments;
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
        markPropertyInjectionBeanCandidate();
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

    private void markPropertyInjectionBeanCandidate() {
        if (BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(getAnnotationMetadata())) {
            return;
        }
        if (isAbstract()) {
            return;
        }
        if (hasPropertyInjectionPoint()) {
            annotate(Bean.class);
        }
    }

    private boolean hasPropertyInjectionPoint() {
        for (PropertyDef propertyDef : getNativeType().properties()) {
            if (hasPropertyInjectionPoint(propertyDef)) {
                return true;
            }
        }
        for (AttributeDef attributeDef : getNativeType().attributes()) {
            if (hasPropertyInjectionPoint(attributeDef)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPropertyInjectionPoint(PropertyDef propertyDef) {
        return hasPropertyInjectionPoint((ElementDef) propertyDef);
    }

    private boolean hasPropertyInjectionPoint(AttributeDef attributeDef) {
        return hasPropertyInjectionPoint((ElementDef) attributeDef);
    }

    private boolean hasPropertyInjectionPoint(ElementDef element) {
        AnnotationMetadata annotationMetadata = environment.annotationMetadataBuilder().buildDeclared(element);
        return annotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || annotationMetadata.hasStereotype(Property.class)
            || annotationMetadata.hasStereotype(io.micronaut.context.annotation.Value.class);
    }

    @Override
    public BeanElementBuilder addAssociatedBean(ClassElement type) {
        JavaVisitorContext javaVisitorContext = environment.javaVisitorContext();
        if (javaVisitorContext != null) {
            return javaVisitorContext.addAssociatedBean(this, type);
        }
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding associated beans without a Java visitor context");
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
        boolean hasInterfaceBase = hasInterfaceBase();
        if ((!hasInterfaceBase && BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(getAnnotationMetadata()))
            || hasStereotype(InterceptorBinding.class)
            || hasStereotype(Introspected.class)
            || getPrimaryConstructor().isPresent()
            || !getNativeType().attributes().isEmpty()
            || !getNativeType().properties().isEmpty()) {
            return false;
        }
        List<MethodElement> declaredMethods = getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared());
        if (declaredMethods.isEmpty()) {
            return hasInterfaceBase;
        }
        return !declaredMethods.isEmpty()
            && declaredMethods.stream().allMatch(MethodElement::isAbstract)
            && declaredMethods.stream().noneMatch(PythonClassElement::isIntroductionFactoryMethod);
    }

    private boolean hasInterfaceBase() {
        for (TypeRef basis : getNativeType().bases()) {
            if (isProtocolType(basis.name())) {
                return true;
            }
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

    private static boolean isProtocolType(String typeName) {
        return typeName.equals("typing.Protocol")
            || typeName.equals("typing_extensions.Protocol")
            || typeName.equals("Protocol");
    }

    private static boolean isIntroductionFactoryMethod(MethodElement method) {
        return method.hasAnnotation("io.micronaut.context.annotation.Mapper")
            || method.hasAnnotation("io.micronaut.context.annotation.Mapper$Mapping")
            // Visitors can add @Bean directly; treat that like @Bean stereotypes
            // when deciding whether abstract methods require introduction wiring.
            || method.hasDeclaredAnnotation(Bean.class)
            || method.hasDeclaredStereotype(Bean.class)
            || method.hasStereotype(Introduction.class);
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
            javaVisitorContext.getClassElement(decorator.annotationName()).filter(annotationElement -> decorator.members().isEmpty()).ifPresent(annotationElement ->
                environment.annotationMetadataBuilder()
                    .mapAnnotation(decorator)
                    .forEach(annotationValue -> collectIntroductionInterfaceNames(javaVisitorContext, annotationValue, interfaceNames))
            );
            collectIntroductionInterfaceNames(javaVisitorContext, decorator.stereotypes(), interfaceNames);
        }
    }

    private void collectIntroductionInterfaceNames(JavaVisitorContext javaVisitorContext, AnnotationValue<?> annotationValue, Set<String> interfaceNames) {
        if (Introduction.class.getName().equals(annotationValue.getAnnotationName())) {
            collectIntroductionInterfaceNames(annotationValue, interfaceNames);
        }
        javaVisitorContext.getClassElement(annotationValue.getAnnotationName())
            .map(annotationElement -> annotationElement.getAnnotation(Introduction.class))
            .ifPresent(introduction -> collectIntroductionInterfaceNames(introduction, interfaceNames));
        List<AnnotationValue<?>> stereotypes = annotationValue.getStereotypes();
        if (stereotypes != null) {
            stereotypes.forEach(stereotype -> collectIntroductionInterfaceNames(javaVisitorContext, stereotype, interfaceNames));
        }
    }

    private void collectIntroductionInterfaceNames(AnnotationValue<?> introduction, Set<String> interfaceNames) {
        for (AnnotationClassValue<?> interfaceValue : introduction.annotationClassValues("interfaces")) {
            interfaceNames.add(rawTypeName(interfaceValue.getName()));
        }
    }

    private void collectIntroductionInterfaceNames(Object value, Set<String> interfaceNames) {
        if (value == null) {
            return;
        }
        if (value instanceof AnnotationClassValue<?> annotationClassValue) {
            interfaceNames.add(rawTypeName(annotationClassValue.getName()));
        } else if (value instanceof AnnotationClassValue<?>[] annotationClassValues) {
            for (AnnotationClassValue<?> annotationClassValue : annotationClassValues) {
                interfaceNames.add(rawTypeName(annotationClassValue.getName()));
            }
        } else if (value instanceof String interfaceName) {
            interfaceNames.add(rawTypeName(interfaceName));
        } else if (value instanceof String[] names) {
            for (String name : names) {
                interfaceNames.add(rawTypeName(name));
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
            interfaceNames.add(rawTypeName(value.asString()));
        } else {
            Object converted = GraalPyUtil.convertValueToJava(value, environment.visitorContext());
            if (converted != value) {
                collectIntroductionInterfaceNames(converted, interfaceNames);
            } else {
                interfaceNames.add(rawTypeName(value.toString()));
            }
        }
    }

    private static String rawTypeName(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart > -1 ? typeName.substring(0, genericStart) : typeName;
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
            Map<String, ClassElement> boundGenerics = new HashMap<>(getTypeArguments());
            for (int i = 0; i < declaredGenericPlaceholders.size(); i++) {
                GenericPlaceholderElement placeHolder = declaredGenericPlaceholders.get(i);
                TypeRef typeRef = typeArguments.get(i);
                ClassElement resolvedType = GraalPyUtil.resolvePythonTypeToJava(typeRef, environment.visitorContext(), boundGenerics);
                String variableName = placeHolder.getVariableName();
                resolvedTypeArguments.put(variableName, resolvedType);
            }
            return baseElement.withTypeArguments(resolvedTypeArguments);
        }
        if (typeArguments.isEmpty() && declaredGenericPlaceholders != null && !declaredGenericPlaceholders.isEmpty()) {
            Map<String, ClassElement> resolvedTypeArguments = new HashMap<>(declaredGenericPlaceholders.size());
            for (GenericPlaceholderElement placeholder : declaredGenericPlaceholders) {
                resolvedTypeArguments.put(placeholder.getVariableName(), firstBound(placeholder));
            }
            return baseElement.withTypeArguments(resolvedTypeArguments);
        }
        return baseElement;
    }

    private static ClassElement firstBound(GenericPlaceholderElement placeholder) {
        List<? extends ClassElement> bounds = placeholder.getBounds();
        if (bounds.isEmpty()) {
            return ClassElement.of(Object.class);
        }
        return bounds.getFirst();
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
            List<? extends GenericPlaceholderElement> placeholders = getDeclaredGenericPlaceholders();
            if (placeholders.isEmpty()) {
                return super.getTypeArguments();
            }
            Map<String, ClassElement> typeArguments = new LinkedHashMap<>(placeholders.size());
            for (GenericPlaceholderElement placeholder : placeholders) {
                typeArguments.put(placeholder.getVariableName(), firstBound(placeholder));
            }
            return typeArguments;
        }
        return resolvedTypeArguments;
    }

    @Override
    public Map<String, Map<String, ClassElement>> getAllTypeArguments() {
        Map<String, Map<String, ClassElement>> result = new LinkedHashMap<>();
        for (TypeRef base : getNativeType().bases()) {
            ClassElement baseElement = findPythonClass(base);
            if (baseElement != null) {
                result.putAll(resolveTypeArguments(baseElement, base).getAllTypeArguments());
            } else {
                toJavaType(base).ifPresent(javaType -> result.putAll(javaType.getAllTypeArguments()));
            }
        }
        result.put(getName(), getTypeArguments());
        return result;
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions, typeArguments);
    }

    boolean hasExplicitTypeArguments() {
        return resolvedTypeArguments != null;
    }

    @NonNull
    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        return getNativeType().typeParams().stream()
            .map(typeVar -> new PythonGenericPlaceholderElement(typeVar, environment, resolveTypeVarBounds(typeVar), this))
            .toList();
    }

    private List<ClassElement> resolveTypeVarBounds(TypeVar typeVar) {
        List<ClassElement> bounds = new ArrayList<>();
        addTypeVarBound(bounds, typeVar.bound());
        for (Object constraint : typeVar.constraints()) {
            addTypeVarBound(bounds, constraint);
        }
        return bounds;
    }

    private void addTypeVarBound(List<ClassElement> bounds, Object bound) {
        if (bound == null) {
            return;
        }
        TypeRef typeRef = bound instanceof TypeRef tr ? tr : new TypeRef(bound.toString());
        ClassElement pythonClass = findPythonClass(typeRef);
        if (pythonClass != null && pythonClass.getName().equals(getName())) {
            return;
        }
        ClassElement boundElement = GraalPyUtil.resolvePythonTypeToJava(typeRef, environment.visitorContext(), Map.of());
        if (!Object.class.getName().equals(boundElement.getName())) {
            bounds.add(boundElement);
        }
    }

}
