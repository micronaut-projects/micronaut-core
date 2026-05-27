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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.annotation.processing.visitor.JavaMethodElement;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.utils.EnclosedElementsQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.jspecify.annotations.NonNull;

public abstract sealed class AbstractPythonClassElement extends AbstractPythonElement
    implements ArrayableClassElement, ElementProvider permits PythonClassElement, PythonEnumElement, PythonGenericPlaceholderElement {
    public static final String PYTHON_DEFAULT_PACKAGE = "python";

    protected final int arrayDimensions;
    protected final PythonProcessingEnvironment environment;
    /** Query implementation for enclosed elements. */
    private final PythonEnclosedElementsQuery enclosedElementsQuery = new PythonEnclosedElementsQuery();

    protected ElementDef typeAnnotationsKey;
    private ElementAnnotationMetadata typeAnnotationMetadata;

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        this(classDef, environment, 0);
    }

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(
            qualifiedClassName(classDef),
            classDef,
            environment.metadataFactory()
        );
        this.environment = environment;
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        if (typeAnnotationsKey == null) {
            return super.getAnnotationMetadataToWrite();
        }
        return getTypeAnnotationMetadata();
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (presetAnnotationMetadata != null) {
            return presetAnnotationMetadata;
        }
        if (typeAnnotationsKey == null) {
            return super.getAnnotationMetadata();
        } else {
            return new AnnotationMetadataHierarchy(true, super.getAnnotationMetadata(), getTypeAnnotationMetadata());
        }
    }

    @Override
    public @NonNull MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        if (typeAnnotationMetadata == null) {
            typeAnnotationMetadata = elementAnnotationMetadataFactory.buildTypeAnnotations(this);
        }
        return typeAnnotationMetadata;
    }

    public final ElementDef getTypeAnnotationsKey() {
        return typeAnnotationsKey;
    }

    final ClassElement withTypeAnnotationsKey(ElementDef typeAnnotationsKey) {
        AbstractPythonClassElement copy = (AbstractPythonClassElement) makeCopy();
        copy.typeAnnotationsKey = typeAnnotationsKey;
        return copy;
    }

    protected final @Nullable ClassElement findPythonClass(TypeRef typeRef) {
        Map<String, ClassElement> classes = environment.classes();
        ClassElement classElement = classes.get(typeRef.name());
        if (classElement != null) {
            return classElement;
        }
        if (typeRef.name().indexOf('.') > -1) {
            return null;
        }
        classElement = classes.get(getPackageName() + "." + typeRef.name());
        if (classElement != null) {
            return classElement;
        }
        return classes.get(PYTHON_DEFAULT_PACKAGE + "." + typeRef.name());
    }

    protected final void excludeIntrospectedProperties(String... propertyNames) {
        if (!hasDeclaredIntrospectedDecorator()) {
            return;
        }
        AnnotationValue<Introspected> introspected = getDeclaredAnnotation(Introspected.class);
        if (introspected == null) {
            return;
        }
        Set<String> excludes = new LinkedHashSet<>(Arrays.asList(introspected.stringValues("excludes")));
        boolean changed = false;
        for (String propertyName : propertyNames) {
            changed |= excludes.add(propertyName);
        }
        if (!changed) {
            return;
        }
        removeAnnotation(Introspected.class);
        annotate(AnnotationValue.builder(introspected)
            .member("excludes", excludes.toArray(String[]::new))
            .build()
        );
    }

    private boolean hasDeclaredIntrospectedDecorator() {
        String introspectedName = Introspected.class.getName();
        String introspectedSimpleName = Introspected.class.getSimpleName();
        for (DecoratorDef decorator : getNativeType().decorators()) {
            String annotationName = decorator.annotationName();
            if (annotationName.equals(introspectedName)
                || annotationName.equals(introspectedSimpleName)
                || annotationName.endsWith("." + introspectedSimpleName)
                || decorator.name().equals(introspectedSimpleName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAbstract() {
        ClassDef nativeType = getNativeType();
        return nativeType
            .bases().stream().anyMatch(b -> b.name().equals("abc.ABC") || isProtocolType(b.name()))
            || nativeType.functions().stream().anyMatch(FunctionDef::isAbstract);
    }

    private static boolean isProtocolType(String typeName) {
        return typeName.equals("typing.Protocol")
            || typeName.equals("typing_extensions.Protocol")
            || typeName.equals("Protocol");
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        ClassDef nativeType = getNativeType();
        List<TypeRef> bases = nativeType.bases();
        if (environment.javaVisitorContext() != null) {
            return bases.stream()
                .flatMap(base -> environment.javaVisitorContext().getClassElement(base.name()).stream())
                .filter(ClassElement::isInterface)
                .toList();
        } else {
            return List.of();
        }
    }

    private static @NotNull String qualifiedClassName(ClassDef classDef) {
        return classDef.packageName().isEmpty() ? PYTHON_DEFAULT_PACKAGE + "." + classDef.name() : classDef.packageName() + "." + classDef.name();
    }

    @Override
    public @Nullable javax.lang.model.element.Element element() {
        return environment.originatingElement();
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return createWithArrayDimensions(arrayDimensions);
    }

    protected abstract ClassElement createWithArrayDimensions(int arrayDimensions);

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(ElementQuery<T> query) {
        List<T> elements = enclosedElementsQuery.getEnclosedElements(this, query);
        elements = decorateDeclaredAbstractMethods(elements, query);
        return appendJavaInterfaceMethods(elements, query);
    }

    @SuppressWarnings("unchecked")
    private <T extends Element> List<T> decorateDeclaredAbstractMethods(List<T> elements, ElementQuery<T> query) {
        ElementQuery.Result<T> result = query.result();
        Class<T> elementType = result.getElementType();
        if (elements.isEmpty() || (elementType != MethodElement.class && elementType != MemberElement.class) || !hasDeclaredTypeAroundBinding()) {
            return elements;
        }
        List<T> decorated = null;
        for (int i = 0; i < elements.size(); i++) {
            T element = elements.get(i);
            if (element instanceof MethodElement methodElement && methodElement.isAbstract() && methodElement.getDeclaringType().equals(this)) {
                if (decorated == null) {
                    decorated = new ArrayList<>(elements);
                }
                decorated.set(i, (T) decorateMethodWithTypeInterceptorMetadata(methodElement));
            }
        }
        return decorated == null ? elements : decorated;
    }

    private boolean hasDeclaredTypeAroundBinding() {
        return !getAnnotationMetadata().getDeclaredAnnotationNamesByStereotype(Around.class.getName()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private <T extends Element> List<T> appendJavaInterfaceMethods(List<T> elements, ElementQuery<T> query) {
        ElementQuery.Result<T> result = query.result();
        Class<T> elementType = result.getElementType();
        if (result.isOnlyDeclared() || (elementType != MethodElement.class && elementType != MemberElement.class)) {
            return elements;
        }

        List<MethodElement> inheritedMethods = new ArrayList<>();
        for (ClassElement anInterface : getInterfaces()) {
            if (elementType == MethodElement.class) {
                for (MethodElement methodElement : anInterface.getEnclosedElements((ElementQuery<MethodElement>) query)) {
                    inheritedMethods.add(ownInheritedInterfaceMethod(resolveInheritedInterfaceMethod(anInterface, methodElement)));
                }
            } else {
                for (MemberElement memberElement : anInterface.getEnclosedElements((ElementQuery<MemberElement>) query)) {
                    if (memberElement instanceof MethodElement methodElement) {
                        inheritedMethods.add(ownInheritedInterfaceMethod(resolveInheritedInterfaceMethod(anInterface, methodElement)));
                    }
                }
            }
        }
        if (inheritedMethods.isEmpty()) {
            return elements;
        }

        List<T> allElements = new ArrayList<>(elements);
        for (MethodElement inheritedMethod : inheritedMethods) {
            int representedMethodIndex = representedInterfaceMethodIndex(allElements, inheritedMethod);
            if (representedMethodIndex == -1) {
                allElements.add((T) decorateInheritedInterfaceMethod(inheritedMethod));
            } else if (allElements.get(representedMethodIndex) instanceof MethodElement representedMethod
                && !representedMethod.getDeclaringType().equals(this)
                && representedMethod.isAbstract()) {
                allElements.set(representedMethodIndex, (T) decorateInheritedInterfaceMethod(representedMethod));
            }
        }
        return allElements;
    }

    private MethodElement resolveInheritedInterfaceMethod(ClassElement anInterface, MethodElement inheritedMethod) {
        if (!(inheritedMethod instanceof JavaMethodElement javaMethodElement) || anInterface.getTypeArguments().isEmpty()) {
            return inheritedMethod;
        }
        List<? extends VariableElement> nativeParameters = javaMethodElement.getNativeType().element().getParameters();
        ParameterElement[] parameters = inheritedMethod.getParameters();
        if (nativeParameters.size() != parameters.length) {
            return inheritedMethod;
        }
        Map<String, ClassElement> typeArguments = new LinkedHashMap<>(anInterface.getTypeArguments());
        for (TypeParameterElement typeParameter : javaMethodElement.getNativeType().element().getTypeParameters()) {
            resolveTypeParameter(typeParameter, typeArguments)
                .ifPresent(type -> typeArguments.put(typeParameter.getSimpleName().toString(), type));
        }
        ParameterElement[] resolvedParameters = null;
        for (int i = 0; i < parameters.length; i++) {
            Optional<ClassElement> resolvedType = resolveTypeMirror(nativeParameters.get(i).asType(), typeArguments);
            if (resolvedType.isPresent() && !sameGenericType(resolvedType.get(), parameters[i].getGenericType())) {
                if (resolvedParameters == null) {
                    resolvedParameters = Arrays.copyOf(parameters, parameters.length);
                }
                resolvedParameters[i] = ParameterElement.of(resolvedType.get(), parameters[i].getName())
                    .withAnnotationMetadata(parameters[i].getAnnotationMetadata());
            }
        }
        return resolvedParameters == null ? inheritedMethod : inheritedMethod.withParameters(resolvedParameters);
    }

    private MethodElement ownInheritedInterfaceMethod(MethodElement inheritedMethod) {
        if (inheritedMethod instanceof PythonMethodElement) {
            // Match Java, Groovy, and Kotlin: inherited methods are viewed through the concrete owning type
            // so type-level metadata participates in normal method metadata resolution.
            return inheritedMethod.withNewOwningType(this);
        }
        return inheritedMethod;
    }

    private Optional<ClassElement> resolveTypeParameter(TypeParameterElement typeParameter, Map<String, ClassElement> typeArguments) {
        for (TypeMirror bound : typeParameter.getBounds()) {
            Optional<ClassElement> resolvedBound = resolveTypeMirror(bound, typeArguments);
            if (resolvedBound.isPresent() && !Object.class.getName().equals(resolvedBound.get().getName())) {
                return resolvedBound;
            }
        }
        return Optional.empty();
    }

    private Optional<ClassElement> resolveTypeMirror(TypeMirror typeMirror, Map<String, ClassElement> typeArguments) {
        return switch (typeMirror.getKind()) {
            case TYPEVAR -> {
                TypeVariable typeVariable = (TypeVariable) typeMirror;
                yield Optional.ofNullable(typeArguments.get(typeVariable.asElement().getSimpleName().toString()));
            }
            case WILDCARD -> {
                WildcardType wildcardType = (WildcardType) typeMirror;
                TypeMirror extendsBound = wildcardType.getExtendsBound();
                TypeMirror superBound = wildcardType.getSuperBound();
                if (extendsBound != null) {
                    yield resolveTypeMirror(extendsBound, typeArguments);
                }
                if (superBound != null) {
                    yield resolveTypeMirror(superBound, typeArguments);
                }
                yield Optional.empty();
            }
            case DECLARED -> resolveDeclaredType((DeclaredType) typeMirror, typeArguments);
            case INT -> Optional.of(PrimitiveElement.INT);
            case LONG -> Optional.of(PrimitiveElement.LONG);
            case BOOLEAN -> Optional.of(PrimitiveElement.BOOLEAN);
            case DOUBLE -> Optional.of(PrimitiveElement.DOUBLE);
            case FLOAT -> Optional.of(PrimitiveElement.FLOAT);
            case SHORT -> Optional.of(PrimitiveElement.SHORT);
            case BYTE -> Optional.of(PrimitiveElement.BYTE);
            case CHAR -> Optional.of(PrimitiveElement.CHAR);
            case VOID -> Optional.of(PrimitiveElement.VOID);
            default -> Optional.empty();
        };
    }

    private Optional<ClassElement> resolveDeclaredType(DeclaredType declaredType, Map<String, ClassElement> typeArguments) {
        if (!(declaredType.asElement() instanceof TypeElement typeElement)) {
            return Optional.empty();
        }
        String typeName = typeElement.getQualifiedName().toString();
        ClassElement rawType = environment.visitorContext()
            .getClassElement(typeName)
            .orElse(ClassElement.of(typeName));
        List<? extends TypeMirror> declaredTypeArguments = declaredType.getTypeArguments();
        if (declaredTypeArguments.isEmpty()) {
            return Optional.of(rawType);
        }
        List<? extends GenericPlaceholderElement> placeholders = rawType.getDeclaredGenericPlaceholders();
        if (placeholders.isEmpty() || placeholders.size() != declaredTypeArguments.size()) {
            return Optional.of(rawType);
        }
        Map<String, ClassElement> resolvedTypeArguments = new LinkedHashMap<>(placeholders.size());
        for (int i = 0; i < declaredTypeArguments.size(); i++) {
            ClassElement resolvedType = resolveTypeMirror(declaredTypeArguments.get(i), typeArguments)
                .orElse(ClassElement.of(Object.class));
            resolvedTypeArguments.put(placeholders.get(i).getVariableName(), resolvedType);
        }
        return Optional.of(rawType.withTypeArguments(resolvedTypeArguments));
    }

    private static boolean sameGenericType(ClassElement left, ClassElement right) {
        if (!left.getName().equals(right.getName())) {
            return false;
        }
        Map<String, ClassElement> leftTypeArguments = left.getTypeArguments();
        Map<String, ClassElement> rightTypeArguments = right.getTypeArguments();
        if (leftTypeArguments.size() != rightTypeArguments.size()) {
            return false;
        }
        for (Map.Entry<String, ClassElement> entry : leftTypeArguments.entrySet()) {
            ClassElement rightTypeArgument = rightTypeArguments.get(entry.getKey());
            if (rightTypeArgument == null || !sameGenericType(entry.getValue(), rightTypeArgument)) {
                return false;
            }
        }
        return true;
    }

    private MethodElement decorateInheritedInterfaceMethod(MethodElement inheritedMethod) {
        if (hasDeclaredTypeAroundBinding()) {
            return decorateMethodWithTypeInterceptorMetadata(inheritedMethod);
        }
        if (!this.equals(inheritedMethod.getOwningType())) {
            // JavaMethodElement cannot be re-owned by a Python class because its implementation requires a
            // JavaClassElement owner. Apply the Python owning type metadata here instead, which is the
            // metadata hierarchy that MethodElementAnnotationMetadata would build after withNewOwningType.
            return decorateMethodWithOwningTypeMetadata(inheritedMethod);
        }
        return inheritedMethod;
    }

    private MethodElement decorateMethodWithOwningTypeMetadata(MethodElement method) {
        return method.withAnnotationMetadata(
            new AnnotationMetadataHierarchy(getAnnotationMetadata(), method.getMethodAnnotationMetadata())
        );
    }

    private MethodElement decorateMethodWithTypeInterceptorMetadata(MethodElement method) {
        return method.withAnnotationMetadata(
            new AnnotationMetadataHierarchy(true, typeInterceptorMetadata(), MutableAnnotationMetadata.of(method.getMethodAnnotationMetadata()))
        );
    }

    private AnnotationMetadata typeInterceptorMetadata() {
        AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        MutableAnnotationMetadata explicitBindings = null;
        Set<String> existingAroundBindings = annotationMetadata.getAnnotationValuesByType(InterceptorBinding.class)
            .stream()
            .filter(binding -> binding.enumValue("kind", InterceptorKind.class).orElse(InterceptorKind.AROUND) == InterceptorKind.AROUND)
            .flatMap(binding -> binding.stringValue().stream())
            .collect(Collectors.toSet());
        for (String annotationName : annotationMetadata.getAnnotationNamesByStereotype(Around.class.getName())) {
            if (existingAroundBindings.contains(annotationName)) {
                continue;
            }
            if (explicitBindings == null) {
                explicitBindings = new MutableAnnotationMetadata();
            }
            explicitBindings.addDeclaredRepeatable(
                AnnotationUtil.ANN_INTERCEPTOR_BINDINGS,
                AnnotationValue.builder(InterceptorBinding.class)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(annotationName))
                    .member("kind", InterceptorKind.AROUND)
                    .build()
            );
        }
        if (explicitBindings == null) {
            return annotationMetadata;
        }
        return new AnnotationMetadataHierarchy(true, annotationMetadata, explicitBindings);
    }

    private static int representedInterfaceMethodIndex(List<? extends Element> elements, MethodElement inheritedMethod) {
        for (int i = 0; i < elements.size(); i++) {
            Element element = elements.get(i);
            if (element instanceof MethodElement methodElement &&
                methodElement.getName().equals(inheritedMethod.getName()) &&
                (methodElement.overrides(inheritedMethod) ||
                    methodElement.isSubSignature(inheritedMethod) ||
                    hasSameRawParameterTypes(methodElement, inheritedMethod))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasSameRawParameterTypes(MethodElement methodElement, MethodElement inheritedMethod) {
        ParameterElement[] parameters = methodElement.getParameters();
        ParameterElement[] inheritedParameters = inheritedMethod.getParameters();
        if (parameters.length != inheritedParameters.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].getType().getName().equals(inheritedParameters[i].getType().getName())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<FieldElement> getFields() {
        return getNativeType().attributes()
            .stream().map(a -> (FieldElement) new PythonFieldElement(a, environment, this, this, environment.metadataFactory()))
            .toList();
    }

    @Override
    public ClassDef getNativeType() {
        return (ClassDef) super.getNativeType();
    }

    @Override
    public String getPackageName() {
        String packageName = getNativeType().packageName();
        return packageName.isEmpty() ? PYTHON_DEFAULT_PACKAGE : packageName;
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        String doc = getNativeType().documentation();
        if (doc == null) {
            return Optional.empty();
        }
        if (parseContent) {
            // Parse Python docstring to extract main description
            return Optional.of(GraalPyUtil.parsePythonDocstring(doc));
        }
        return Optional.of(doc);
    }

    @Override
    public List<PropertyElement> getSyntheticBeanProperties() {
        return getBeanProperties();
    }

    @Override
    public List<PropertyElement> getBeanProperties() {
        PropertyElementQuery defaultPropertyElementQuery = PropertyElementQuery.of(this);
        return getBeanProperties(defaultPropertyElementQuery);
    }

    @Override
    public List<PropertyElement> getBeanProperties(PropertyElementQuery propertyElementQuery) {
        // For Python, we create properties from both @property decorators and regular attributes

        // First, add properties from @property decorators (these are already PropertyDef instances)
        List<PropertyElement> decoratorProperties = getEnclosedElements(ElementQuery.of(PropertyElement.class));
        List<PropertyElement> allProperties = new java.util.ArrayList<>(decoratorProperties);

        // Then, create properties from regular attributes that aren't already represented as properties
        addAttributeBackedProperties(this, this, allProperties);

        // Apply propertyElementQuery filtering
        return filterProperties(allProperties, propertyElementQuery);
    }

    private void addAttributeBackedProperties(AbstractPythonClassElement declaringType,
                                              ClassElement owningType,
                                              List<PropertyElement> allProperties) {
        List<AttributeDef> fields = declaringType.getNativeType().attributes();
        for (AttributeDef field : fields) {
            // Check if this field is already represented as a property
            boolean alreadyExists = allProperties.stream()
                .anyMatch(prop -> prop.getName().equals(field.name()));

            if (!alreadyExists) {
                // Create a field-based property from the attribute
                PropertyDef propertyDef = new PropertyDef(field.name());
                propertyDef = propertyDef.withField(field);

                PythonPropertyElement propertyElement = new PythonPropertyElement(
                    propertyDef,
                    environment,
                    declaringType,
                    owningType,
                    environment.metadataFactory()
                );
                allProperties.add(propertyElement);
            }
        }

        declaringType.getSuperType().ifPresent(superType -> {
            if (superType instanceof AbstractPythonClassElement pythonSuperType) {
                addAttributeBackedProperties(pythonSuperType, owningType, allProperties);
            }
        });
    }

    static List<PropertyElement> filterProperties(List<PropertyElement> properties, PropertyElementQuery query) {
        if (properties.isEmpty()) {
            return properties;
        }

        Set<String> includes = query.getIncludes();
        Set<String> excludes = query.getExcludes();
        Set<String> excludedAnnotations = query.getExcludedAnnotations();
        Set<io.micronaut.context.annotation.BeanProperties.AccessKind> accessKinds = query.getAccessKinds();
        io.micronaut.context.annotation.BeanProperties.Visibility visibility = query.getVisibility();
        boolean allowStaticProperties = query.isAllowStaticProperties();

        List<PropertyElement> filteredProperties = new java.util.ArrayList<>();

        for (PropertyElement property : properties) {
            String propertyName = property.getName();

            // Apply includes/excludes filtering
            if (!includes.isEmpty() && !includes.contains(propertyName)) {
                continue;
            }
            if (!excludes.isEmpty() && excludes.contains(propertyName)) {
                continue;
            }

            // Apply annotation-based exclusion
            if (isExcludedByAnnotations(property, excludedAnnotations)) {
                continue;
            }

            // Apply access kind filtering
            if (!isAccessibleViaAccessKinds(property, accessKinds)) {
                continue;
            }

            // Apply visibility filtering
            if (!isAccessibleViaVisibility(property, visibility)) {
                continue;
            }

            // Apply static properties filtering
            if (!allowStaticProperties && isStaticProperty(property)) {
                continue;
            }

            filteredProperties.add(property);
        }

        return filteredProperties;
    }

    private static boolean isExcludedByAnnotations(PropertyElement property, Set<String> excludedAnnotations) {
        if (excludedAnnotations.isEmpty()) {
            return false;
        }

        // Check if any of the property's elements (field, getter, setter) have excluded annotations
        if (property.getField().isPresent() && hasExcludedAnnotation(property.getField().get(), excludedAnnotations)) {
            return true;
        }
        if (property.getReadMethod().isPresent() && hasExcludedAnnotation(property.getReadMethod().get(), excludedAnnotations)) {
            return true;
        }
        if (property.getWriteMethod().isPresent() && hasExcludedAnnotation(property.getWriteMethod().get(), excludedAnnotations)) {
            return true;
        }

        return false;
    }

    private static boolean hasExcludedAnnotation(io.micronaut.inject.ast.Element element, Set<String> excludedAnnotations) {
        return excludedAnnotations.stream().anyMatch(element::hasAnnotation);
    }

    private static boolean isAccessibleViaAccessKinds(PropertyElement property, Set<io.micronaut.context.annotation.BeanProperties.AccessKind> accessKinds) {
        // Check if any of the requested access kinds match the property's actual access kinds
        if (accessKinds.contains(io.micronaut.context.annotation.BeanProperties.AccessKind.METHOD)) {
            // Property has METHOD access if it has a getter or setter
            if (property.getReadMethod().isPresent() || property.getWriteMethod().isPresent()) {
                return true;
            }
        }
        if (accessKinds.contains(io.micronaut.context.annotation.BeanProperties.AccessKind.FIELD)) {
            // Property has FIELD access if it has a field (field-based property)
            if (property.getField().isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAccessibleViaVisibility(PropertyElement property, io.micronaut.context.annotation.BeanProperties.Visibility visibility) {
        // For Python, we consider all properties accessible for now
        // In the future, we could check for private/protected modifiers if Python supports them
        return visibility == io.micronaut.context.annotation.BeanProperties.Visibility.ANY ||
               visibility == io.micronaut.context.annotation.BeanProperties.Visibility.DEFAULT ||
               visibility == io.micronaut.context.annotation.BeanProperties.Visibility.PUBLIC;
    }

    private static boolean isStaticProperty(PropertyElement property) {
        // Check if the property is backed by a static field
        return property.getField()
            .map(field -> {
                if (field instanceof PythonFieldElement pythonField) {
                    AttributeDef attrDef = pythonField.getNativeType();
                    return attrDef.isStatic();
                }
                return false;
            })
            .orElse(false);
    }

    private final class PythonEnclosedElementsQuery extends EnclosedElementsQuery<ClassDef, ElementDef> {
        private ClassDef currentDeclaringClass;

        @Override
        protected boolean hasAnnotation(ElementDef element, Class<? extends java.lang.annotation.Annotation> annotation) {
            for (DecoratorDef decorator : element.decorators()) {
                if (decorator.annotationName().equals(annotation.getName())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected ElementDef getNativeType(Element element) {
            return (ElementDef) element.getNativeType();
        }

        @Override
        protected String getElementName(ElementDef element) {
            return element.name();
        }

        @Override
        protected ClassDef getSuperClass(ClassDef classNode) {
            List<TypeRef> bases = classNode.bases();
            if (!bases.isEmpty()) {
                // Find the first base class that exists in our environment
                for (TypeRef base : bases) {
                    ClassElement baseElement = findPythonClass(base);
                    if (baseElement instanceof AbstractPythonClassElement pythonBaseElement && !pythonBaseElement.isInterface()) {
                        return pythonBaseElement.getNativeType();
                    }
                }
            }
            return null;
        }

        @Override
        protected List<ClassDef> getInterfaces(ClassDef classNode) {
            List<TypeRef> bases = classNode.bases();
            return bases.stream()
                .map(base -> {
                    ClassElement baseElement = findPythonClass(base);
                    return baseElement instanceof AbstractPythonClassElement pythonBaseElement && pythonBaseElement.isInterface()
                        ? pythonBaseElement.getNativeType()
                        : null;
                })
                .filter(Objects::nonNull)
                .toList();
        }

        @Override
        protected List<ElementDef> getEnclosedElements(ClassDef classNode, ElementQuery.Result<?> result, boolean includeAbstract) {
            this.currentDeclaringClass = classNode;
            List<ElementDef> elements = new java.util.ArrayList<>();
            Class<?> elementType = result.getElementType();

            // Add functions (methods) if the query is for methods/constructors or members
            if (elementType == ConstructorElement.class) {
                FunctionDef constructor = classNode.constructor();
                if (constructor != null) {
                    elements.add(constructor);
                }
            }

            // Add functions (methods) if the query is for methods/constructors or members
            if (elementType == MethodElement.class ||
                elementType == MemberElement.class) {
                for (FunctionDef function : classNode.functions()) {
                    if (includeAbstract || !function.isAbstract()) {
                        elements.add(function);
                    }
                }
            }

            // For Python, attributes are not real fields since Python uses dynamic attributes
            // So we don't return them as FieldElement instances to avoid injection issues
            // Properties are handled separately via PropertyElement
            // if (elementType == FieldElement.class ||
            //     elementType == MemberElement.class) {
            //     elements.addAll(classNode.attributes());
            // }

            // Add properties if the query is for properties or members
            if (elementType == PropertyElement.class ||
                elementType == MemberElement.class) {
                elements.addAll(classNode.properties());
            }

            if (elementType == ClassElement.class) {
                // The Python processor models direct nested classes on the declaring ClassDef.
                // BeanDefinitionWriter uses this enclosed ClassElement query to record nested
                // configuration readers for runtime binding, so avoid rediscovering them from
                // binary names here.
                elements.addAll(classNode.nestedClasses());
            }

            return elements;
        }

        @Override
        protected boolean excludeClass(ClassDef classNode) {
            String name = classNode.name();
            // Exclude built-in Python classes
            return "object".equals(name) || "type".equals(name);
        }

        @Override
        protected boolean isAbstractClass(ClassDef classNode) {
            return classNode.functions().stream().anyMatch(FunctionDef::isAbstract);
        }

        @Override
        protected boolean isInterface(ClassDef classNode) {
            // Python doesn't have interfaces, so always return false
            return false;
        }

        @Override
        protected Element toAstElement(ElementDef nativeType, Class<?> elementType) {
            // Determine the declaring class element
            AbstractPythonClassElement declaringClassElement = AbstractPythonClassElement.this; // Default to the queried class
            if (nativeType instanceof MemberDef memberDef && memberDef.declaringClass() != null) {
                ClassDef classDef = memberDef.declaringClass();
                String qualifiedName = classDef.qualifiedName();
                if (!qualifiedName.equals(declaringClassElement.getName())) {
                    ClassElement ce = environment.classes().get(qualifiedName);
                    if (ce instanceof AbstractPythonClassElement ape) {
                        declaringClassElement = ape;
                    }
                }
            } else {
                if (currentDeclaringClass != null && !currentDeclaringClass.equals(getNativeClassType(AbstractPythonClassElement.this))) {
                    // This is an inherited element. The Python environment is keyed by qualified
                    // class name, and preserving the real declaring type is required for inherited
                    // class-level metadata such as @Executable to be evaluated against the superclass
                    // instead of the subclass currently being queried.
                    String declaringClassName = qualifiedClassName(currentDeclaringClass);
                    ClassElement declaringElement = environment.classes().get(declaringClassName);
                    if (declaringElement instanceof PythonClassElement pythonDeclaringClass) {
                        declaringClassElement = pythonDeclaringClass;
                    }
                }
            }

            if (nativeType instanceof FunctionDef functionDef) {
                if (functionDef.name().equals(FunctionDef.CONSTRUCTOR_NAME)) {
                    return new PythonConstructorElement(functionDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
                } else {
                    return new PythonMethodElement(functionDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
                }
            } else if (nativeType instanceof AttributeDef attributeDef) {
                return new PythonFieldElement(attributeDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
            } else if (nativeType instanceof PropertyDef propertyDef) {
                return new PythonPropertyElement(propertyDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
            } else if (nativeType instanceof ClassDef classDef) {
                ClassElement classElement = environment.classes().get(qualifiedClassName(classDef));
                if (classElement != null) {
                    return classElement;
                }
                return new PythonClassElement(classDef, environment);
            }
            throw new IllegalStateException("Unknown native type: " + nativeType.getClass());
        }
    }

    @Override
    protected abstract AbstractPythonElement copyThis();

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ClassElement) super.withAnnotationMetadata(annotationMetadata);
    }
}
