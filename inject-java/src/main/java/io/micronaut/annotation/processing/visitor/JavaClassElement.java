/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.annotation.processing.SuperclassAwareTypeVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils;
import io.micronaut.inject.ast.utils.EnclosedElementsQuery;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A class element returning data from a {@link TypeElement}.
 *
 * @author James Kleeh
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
public class JavaClassElement extends AbstractJavaElement implements ArrayableClassElement {
    private static final String KOTLIN_METADATA = "kotlin.Metadata";
    private static final String PREFIX_IS = "is";
    protected final TypeElement classElement;
    protected final int arrayDimensions;
    final List<? extends TypeMirror> typeArguments;
    private final boolean isTypeVariable;
    private List<PropertyElement> beanProperties;
    private Map<String, Map<String, Supplier<ClassElement>>> genericTypeInfo;
    private String simpleName;
    private String name;
    private String packageName;
    @Nullable
    private Map<String, ClassElement> resolvedTypeArguments;
    @Nullable
    private Map<String, Map<String, ClassElement>> resolvedAllTypeArguments;
    @Nullable
    private ClassElement resolvedSuperType;
    private final JavaEnclosedElementsQuery enclosedElementsQuery = new JavaEnclosedElementsQuery();
    /**
     * @param classElement              The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    @Internal
    public JavaClassElement(TypeElement classElement, ElementAnnotationMetadataFactory annotationMetadataFactory, JavaVisitorContext visitorContext) {
        this(classElement, annotationMetadataFactory, visitorContext, Collections.emptyList(), null, 0, false);
    }

    /**
     * @param classElement              The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     * @param typeArguments             The declared type arguments
     * @param genericsInfo              The generic type info
     */
    JavaClassElement(
        TypeElement classElement,
        ElementAnnotationMetadataFactory annotationMetadataFactory,
        JavaVisitorContext visitorContext,
        List<? extends TypeMirror> typeArguments,
        Map<String, Map<String, Supplier<ClassElement>>> genericsInfo) {
        this(classElement, annotationMetadataFactory, visitorContext, typeArguments, genericsInfo, 0, false);
    }

    /**
     * @param classElement              The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     * @param typeArguments             The declared type arguments
     * @param genericsInfo              The generic type info
     * @param arrayDimensions           The number of array dimensions
     */
    JavaClassElement(
        TypeElement classElement,
        ElementAnnotationMetadataFactory annotationMetadataFactory,
        JavaVisitorContext visitorContext,
        List<? extends TypeMirror> typeArguments,
        Map<String, Map<String, Supplier<ClassElement>>> genericsInfo,
        int arrayDimensions) {
        this(classElement, annotationMetadataFactory, visitorContext, typeArguments, genericsInfo,  arrayDimensions, false);
    }

    /**
     * @param classElement              The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     * @param typeArguments             The declared type arguments
     * @param genericsInfo              The generic type info
     * @param isTypeVariable            Is the class element a type variable
     */
    JavaClassElement(
        TypeElement classElement,
        ElementAnnotationMetadataFactory annotationMetadataFactory,
        JavaVisitorContext visitorContext,
        List<? extends TypeMirror> typeArguments,
        Map<String, Map<String, Supplier<ClassElement>>> genericsInfo,
        boolean isTypeVariable) {
        this(classElement, annotationMetadataFactory, visitorContext, typeArguments, genericsInfo, 0, isTypeVariable);
    }

    /**
     * @param classElement              The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     * @param typeArguments             The declared type arguments
     * @param genericsInfo              The generic type info
     * @param arrayDimensions           The number of array dimensions
     * @param isTypeVariable            Is the type a type variable
     */
    JavaClassElement(
        TypeElement classElement,
        ElementAnnotationMetadataFactory annotationMetadataFactory,
        JavaVisitorContext visitorContext,
        List<? extends TypeMirror> typeArguments,
        Map<String, Map<String, Supplier<ClassElement>>> genericsInfo,
        int arrayDimensions,
        boolean isTypeVariable) {
        super(classElement, annotationMetadataFactory, visitorContext);
        this.classElement = classElement;
        this.typeArguments = typeArguments;
        this.genericTypeInfo = genericsInfo;
        this.arrayDimensions = arrayDimensions;
        this.isTypeVariable = isTypeVariable;
    }

    @Override
    protected JavaClassElement copyThis() {
        return new JavaClassElement(classElement, elementAnnotationMetadataFactory, visitorContext, typeArguments, genericTypeInfo);
    }

    @Override
    protected void copyValues(AbstractJavaElement element) {
        super.copyValues(element);
        ((JavaClassElement) element).resolvedTypeArguments = resolvedTypeArguments;
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> newTypeArguments) {
        JavaClassElement javaClassElement = (JavaClassElement) makeCopy();
        javaClassElement.resolvedTypeArguments = newTypeArguments;
        return javaClassElement;
    }

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ClassElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public boolean isTypeVariable() {
        return isTypeVariable;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean isInner() {
        return classElement.getNestingKind().isNested();
    }

    @Override
    public boolean isRecord() {
        return JavaModelUtils.isRecord(classElement);
    }

    public final TypeElement getNativeTypeElement() {
        return classElement;
    }

    @NonNull
    @Override
    public Map<String, ClassElement> getTypeArguments(@NonNull String type) {
        if (StringUtils.isNotEmpty(type)) {
            Map<String, Map<String, TypeMirror>> data = visitorContext.getGenericUtils().buildGenericTypeArgumentElementInfo(classElement, null, getBoundTypeMirrors());
            Map<String, TypeMirror> forType = data.get(type);
            if (forType != null) {
                Map<String, ClassElement> typeArgs = CollectionUtils.newLinkedHashMap(forType.size());
                for (Map.Entry<String, TypeMirror> entry : forType.entrySet()) {
                    TypeMirror v = entry.getValue();
                    ClassElement ce = v != null ? mirrorToClassElement(v, visitorContext, Collections.emptyMap(), visitorContext.getConfiguration().includeTypeLevelAnnotationsInGenericArguments()) : null;
                    if (ce == null) {
                        return Collections.emptyMap();
                    } else {
                        typeArgs.put(entry.getKey(), ce);
                    }
                }
                return Collections.unmodifiableMap(typeArgs);
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public boolean isPrimitive() {
        return ClassUtils.getPrimitiveType(getName()).isPresent();
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        return classElement.getInterfaces().stream().map(mirror -> mirrorToClassElement(mirror, visitorContext, genericTypeInfo)).toList();
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        if (resolvedSuperType == null) {
            final TypeMirror superclass = classElement.getSuperclass();
            if (superclass == null) {
                return Optional.empty();
            }
            final Element element = visitorContext.getTypes().asElement(superclass);
            if (element instanceof TypeElement superElement) {
                if (Object.class.getName().equals(superElement.getQualifiedName().toString())) {
                    return Optional.empty();
                }
                // if super type has type arguments, then build a parameterized ClassElement
                if (superclass instanceof DeclaredType && !((DeclaredType) superclass).getTypeArguments().isEmpty()) {
                    resolvedSuperType = parameterizedClassElement(superclass, visitorContext, createTypeArguments());
                } else {
                    resolvedSuperType = new JavaClassElement(superElement, elementAnnotationMetadataFactory, visitorContext);
                }
            }
        }
        return Optional.ofNullable(resolvedSuperType);
    }

    @Override
    public boolean isAbstract() {
        return classElement.getModifiers().contains(Modifier.ABSTRACT);
    }

    @Override
    public boolean isInterface() {
        return JavaModelUtils.isInterface(classElement);
    }

    @Override
    public List<PropertyElement> getBeanProperties() {
        if (beanProperties == null) {
            beanProperties = getBeanProperties(PropertyElementQuery.of(this));
        }
        return Collections.unmodifiableList(beanProperties);
    }

    @Override
    public List<PropertyElement> getBeanProperties(PropertyElementQuery propertyElementQuery) {
        if (isRecord()) {
            return AstBeanPropertiesUtils.resolveBeanProperties(propertyElementQuery,
                this,
                this::getRecordMethods,
                this::getRecordFields,
                true,
                Collections.emptySet(),
                methodElement -> Optional.empty(),
                methodElement -> Optional.empty(),
                this::mapToPropertyElement);
        }
        Function<MethodElement, Optional<String>> customReaderPropertyNameResolver = methodElement -> Optional.empty();
        Function<MethodElement, Optional<String>> customWriterPropertyNameResolver = methodElement -> Optional.empty();
        if (isKotlinClass(getNativeTypeElement())) {
            Set<String> isProperties = getEnclosedElements(ElementQuery.ALL_METHODS)
                .stream()
                .map(io.micronaut.inject.ast.Element::getName)
                .filter(method -> method.startsWith(PREFIX_IS))
                .collect(Collectors.toSet());
            if (!isProperties.isEmpty()) {
                customReaderPropertyNameResolver = methodElement -> {
                    String methodName = methodElement.getSimpleName();
                    if (methodName.startsWith(PREFIX_IS)) {
                        return Optional.of(methodName);
                    }
                    return Optional.empty();
                };
                customWriterPropertyNameResolver = methodElement -> {
                    String methodName = methodElement.getSimpleName();
                    String propertyName = NameUtils.getPropertyNameForSetter(methodName);
                    String isPropertyName = PREFIX_IS + NameUtils.capitalize(propertyName);
                    if (isProperties.contains(isPropertyName)) {
                        return Optional.of(isPropertyName);
                    }
                    return Optional.empty();
                };
            }
        }
        return AstBeanPropertiesUtils.resolveBeanProperties(propertyElementQuery,
            this,
            () -> getEnclosedElements(ElementQuery.ALL_METHODS),
            () -> getEnclosedElements(ElementQuery.ALL_FIELDS),
            false,
            Collections.emptySet(),
            customReaderPropertyNameResolver,
            customWriterPropertyNameResolver,
            this::mapToPropertyElement);
    }

    private JavaPropertyElement mapToPropertyElement(AstBeanPropertiesUtils.BeanPropertyData value) {
        return new JavaPropertyElement(
            JavaClassElement.this,
            value.type,
            value.readAccessKind == null ? null : value.getter,
            value.writeAccessKind == null ? null : value.setter,
            value.field,
            elementAnnotationMetadataFactory,
            value.propertyName,
            value.readAccessKind == null ? PropertyElement.AccessKind.METHOD : PropertyElement.AccessKind.valueOf(value.readAccessKind.name()),
            value.writeAccessKind == null ? PropertyElement.AccessKind.METHOD : PropertyElement.AccessKind.valueOf(value.writeAccessKind.name()),
            value.isExcluded,
            visitorContext);
    }

    private List<MethodElement> getRecordMethods() {
        List<MethodElement> methodElements = new ArrayList<>();
        classElement.asType().accept(new SuperclassAwareTypeVisitor<Object, Object>(visitorContext) {

            private final Set<String> recordComponents = new HashSet<>();

            @Override
            protected boolean isAcceptable(Element element) {
                return JavaModelUtils.isRecord(element);
            }

            @Override
            public Object visitDeclared(DeclaredType type, Object o) {
                Element element = type.asElement();
                if (isAcceptable(element)) {
                    for (Element enclosedElement : element.getEnclosedElements()) {
                        if (JavaModelUtils.isRecordComponent(enclosedElement) || enclosedElement instanceof ExecutableElement) {
                            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                                accept(type, enclosedElement, o);
                            }
                        }
                    }
                }
                return o;
            }

            @Override
            protected void accept(DeclaredType type, Element element, Object o) {
                String name = element.getSimpleName().toString();
                if (element instanceof ExecutableElement) {
                    if (recordComponents.contains(name)) {
                        methodElements.add(
                            new JavaMethodElement(JavaClassElement.this, (ExecutableElement) element, elementAnnotationMetadataFactory, visitorContext)
                        );
                    }
                } else if (element instanceof VariableElement) {
                    recordComponents.add(name);
                }
            }

        }, null);
        return methodElements;
    }

    private List<FieldElement> getRecordFields() {
        List<FieldElement> fieldElements = new ArrayList<>();
        classElement.asType().accept(new SuperclassAwareTypeVisitor<Object, Object>(visitorContext) {

            @Override
            protected boolean isAcceptable(Element element) {
                return JavaModelUtils.isRecord(element);
            }

            @Override
            public Object visitDeclared(DeclaredType type, Object o) {
                Element element = type.asElement();
                if (isAcceptable(element)) {
                    List<? extends Element> enclosedElements = element.getEnclosedElements();
                    for (Element enclosedElement : enclosedElements) {
                        if ((JavaModelUtils.isRecordComponent(enclosedElement)
                            || enclosedElement instanceof ExecutableElement)
                            && enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                            accept(type, enclosedElement, o);
                        }
                    }
                }
                return o;
            }

            @Override
            protected void accept(DeclaredType type, Element element, Object o) {
                if (element instanceof VariableElement) {
                    fieldElements.add(
                        new JavaFieldElement(JavaClassElement.this, (VariableElement) element, elementAnnotationMetadataFactory, visitorContext)
                    );
                }
            }

        }, null);
        return fieldElements;
    }

    private boolean isKotlinClass(Element element) {
        return element.getAnnotationMirrors().stream().anyMatch(am -> am.getAnnotationType().asElement().toString().equals(KOTLIN_METADATA));
    }

    @Override
    public <T extends io.micronaut.inject.ast.Element> List<T> getEnclosedElements(@NonNull ElementQuery<T> query) {
        ClassElement classElementToInspect;
        if (this instanceof GenericPlaceholderElement genericPlaceholderElement) {
            List<? extends ClassElement> bounds = genericPlaceholderElement.getBounds();
            if (bounds.isEmpty()) {
                return Collections.emptyList();
            }
            classElementToInspect = bounds.get(0);
        } else {
            classElementToInspect = this;
        }
        return enclosedElementsQuery.getEnclosedElements(classElementToInspect, query);
    }

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        if (arrayDimensions == this.arrayDimensions) {
            return this;
        }
        return new JavaClassElement(classElement, elementAnnotationMetadataFactory, visitorContext, typeArguments, getGenericTypeInfo(), arrayDimensions, false);
    }

    @Override
    public String getSimpleName() {
        if (simpleName == null) {
            simpleName = JavaModelUtils.getClassNameWithoutPackage(classElement);
        }
        return simpleName;
    }

    @Override
    public String getName() {
        if (name == null) {
            name = JavaModelUtils.getClassName(classElement);
        }
        return name;
    }

    @Override
    public String getPackageName() {
        if (packageName == null) {
            packageName = JavaModelUtils.getPackageName(classElement);
        }
        return packageName;
    }

    @Override
    public PackageElement getPackage() {
        Element enclosingElement = classElement.getEnclosingElement();
        while (enclosingElement != null && enclosingElement.getKind() != ElementKind.PACKAGE) {
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        if (enclosingElement instanceof javax.lang.model.element.PackageElement packageElement) {
            return new JavaPackageElement(
                packageElement,
                elementAnnotationMetadataFactory,
                visitorContext
            );
        } else {
            return PackageElement.DEFAULT_PACKAGE;
        }

    }

    @Override
    public boolean isAssignable(String type) {
        TypeElement otherElement = visitorContext.getElements().getTypeElement(type);
        if (otherElement != null) {
            return isAssignable(otherElement);
        }
        return false;
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        if (type.isPrimitive()) {
            return isAssignable(type.getName());
        }
        Object nativeType = type.getNativeType();
        if (nativeType instanceof TypeElement) {
            return isAssignable((TypeElement) nativeType);
        }
        return isAssignable(type.getName());
    }

    @Override
    public Optional<ClassElement> getOptionalValueType() {
        if (isAssignable(Optional.class)) {
            return getFirstTypeArgument().or(() -> visitorContext.getClassElement(Object.class));
        }
        if (isAssignable(OptionalLong.class)) {
            return visitorContext.getClassElement(Long.class);
        }
        if (isAssignable(OptionalDouble.class)) {
            return visitorContext.getClassElement(Double.class);
        }
        if (isAssignable(OptionalInt.class)) {
            return visitorContext.getClassElement(Integer.class);
        }
        return Optional.empty();
    }

    private boolean isAssignable(TypeElement otherElement) {
        Types types = visitorContext.getTypes();
        TypeMirror thisType = types.erasure(classElement.asType());
        TypeMirror thatType = types.erasure(otherElement.asType());
        return types.isAssignable(thisType, thatType);
    }

    @NonNull
    @Override
    @SuppressWarnings("java:S1119")
    public Optional<MethodElement> getPrimaryConstructor() {
        if (JavaModelUtils.isRecord(classElement)) {
            Optional<MethodElement> staticCreator = findStaticCreator();
            if (staticCreator.isPresent()) {
                return staticCreator;
            }
            if (isInner() && !isStatic()) {
                // only static inner classes can be constructed
                return Optional.empty();
            }
            List<ConstructorElement> constructors = getAccessibleConstructors();
            Optional<ConstructorElement> annotatedConstructor = constructors.stream()
                .filter(c -> c.hasStereotype(AnnotationUtil.INJECT) || c.hasStereotype(Creator.class))
                .findFirst();
            if (annotatedConstructor.isPresent()) {
                return annotatedConstructor.map(c -> c);
            }
            // with records the record constructor is always the last constructor
            List<? extends RecordComponentElement> recordComponents = classElement.getRecordComponents();
            constructorSearch: for (ConstructorElement constructor : constructors) {
                ParameterElement[] parameters = constructor.getParameters();
                if (parameters.length == recordComponents.size()) {
                    for (int i = 0; i < parameters.length; i++) {
                        ParameterElement parameter = parameters[i];
                        RecordComponentElement rce = recordComponents.get(i);
                        VariableElement ve = (VariableElement) parameter.getNativeType();
                        TypeMirror leftType = visitorContext.getTypes().erasure(ve.asType());
                        TypeMirror rightType = visitorContext.getTypes().erasure(rce.asType());
                        if (!leftType.equals(rightType)) {
                            // types don't match, continue searching constructors
                            continue constructorSearch;
                        }
                    }
                    return Optional.of(constructor);
                }
            }
            return Optional.of(constructors.get(constructors.size() - 1));
        }
        return ArrayableClassElement.super.getPrimaryConstructor();
    }

    @Override
    public List<MethodElement> getAccessibleStaticCreators() {
        List<MethodElement> staticCreators = new ArrayList<>(ArrayableClassElement.super.getAccessibleStaticCreators());
        if (!staticCreators.isEmpty()) {
            return staticCreators;
        }
        return visitorContext.getClassElement(getName() + "$Companion", elementAnnotationMetadataFactory)
            .filter(io.micronaut.inject.ast.Element::isStatic)
            .flatMap(typeElement -> typeElement.getEnclosedElements(ElementQuery.ALL_METHODS
                .annotated(annotationMetadata -> annotationMetadata.hasStereotype(Creator.class))).stream().findFirst()
            )
            .filter(method -> !method.isPrivate() && method.getReturnType().equals(this))
            .map(Collections::singletonList).orElse(Collections.emptyList());
    }

    @Override
    public Optional<ClassElement> getEnclosingType() {
        if (isInner()) {
            Element enclosingElement = this.classElement.getEnclosingElement();
            if (enclosingElement instanceof TypeElement typeElement) {
                return Optional.of(visitorContext.getElementFactory().newClassElement(
                    typeElement,
                    elementAnnotationMetadataFactory
                ));
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public List<ClassElement> getBoundGenericTypes() {
        return typeArguments.stream()
            //return getGenericTypeInfo().getOrDefault(classElement.getQualifiedName().toString(), Collections.emptyMap()).values().stream()
            .map(tm -> mirrorToClassElement(tm, visitorContext, getGenericTypeInfo()))
            .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        return classElement.getTypeParameters().stream()
            // we want the *declared* variables, so we don't pass in our genericsInfo.
            .map(tpe -> (GenericPlaceholderElement) mirrorToClassElement(tpe.asType(), visitorContext))
            .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public ClassElement getRawClassElement() {
        return visitorContext.getElementFactory().newClassElement(classElement, elementAnnotationMetadataFactory)
            .withArrayDimensions(getArrayDimensions());
    }

    private TypeMirror toTypeMirror(JavaVisitorContext visitorContext, ClassElement element) {
        if (element.isArray()) {
            return visitorContext.getTypes().getArrayType(toTypeMirror(visitorContext, element.fromArray()));
        } else if (element.isWildcard()) {
            WildcardElement wildcardElement = (WildcardElement) element;
            List<? extends ClassElement> upperBounds = wildcardElement.getUpperBounds();
            if (upperBounds.size() != 1) {
                throw new UnsupportedOperationException("Multiple upper bounds not supported");
            }
            TypeMirror upperBound = toTypeMirror(visitorContext, upperBounds.get(0));
            if (upperBound.toString().equals("java.lang.Object")) {
                upperBound = null;
            }
            List<? extends ClassElement> lowerBounds = wildcardElement.getLowerBounds();
            if (lowerBounds.size() > 1) {
                throw new UnsupportedOperationException("Multiple upper bounds not supported");
            }
            TypeMirror lowerBound = lowerBounds.isEmpty() ? null : toTypeMirror(visitorContext, lowerBounds.get(0));
            return visitorContext.getTypes().getWildcardType(upperBound, lowerBound);
        } else if (element.isGenericPlaceholder()) {
            if (!(element instanceof JavaGenericPlaceholderElement)) {
                throw new UnsupportedOperationException("Free type variable on non-java class");
            }
            return ((JavaGenericPlaceholderElement) element).realTypeVariable;
        } else {
            if (element instanceof JavaClassElement) {
                return visitorContext.getTypes().getDeclaredType(
                    ((JavaClassElement) element).classElement,
                    ((JavaClassElement) element).typeArguments.toArray(new TypeMirror[0]));
            } else {
                ClassElement classElement1 = visitorContext.getRequiredClassElement(element.getName(), elementAnnotationMetadataFactory);
                return visitorContext.getTypes().getDeclaredType(
                    ((JavaClassElement) classElement1).classElement,
                    element.getBoundGenericTypes().stream().map(ce -> toTypeMirror(visitorContext, ce)).toArray(TypeMirror[]::new));
            }
        }
    }

    @NonNull
    @Override
    public ClassElement withBoundGenericTypes(@NonNull List<? extends ClassElement> typeArguments) {
        if (typeArguments.isEmpty() && this.typeArguments.isEmpty()) {
            return this;
        }

        List<TypeMirror> typeMirrors = typeArguments.stream()
            .map(ce -> toTypeMirror(visitorContext, ce))
            .collect(Collectors.toList());
        return withBoundGenericTypeMirrors(typeMirrors);
    }

    private ClassElement withBoundGenericTypeMirrors(@NonNull List<? extends TypeMirror> typeMirrors) {
        if (typeMirrors.equals(this.typeArguments)) {
            return this;
        }

        Map<String, TypeMirror> boundByName = new LinkedHashMap<>();
        Iterator<? extends TypeParameterElement> tpes = classElement.getTypeParameters().iterator();
        Iterator<? extends TypeMirror> args = typeMirrors.iterator();
        while (tpes.hasNext() && args.hasNext()) {
            boundByName.put(tpes.next().getSimpleName().toString(), args.next());
        }

        Map<String, Map<String, TypeMirror>> genericsInfo = visitorContext.getGenericUtils().buildGenericTypeArgumentElementInfo(classElement, null, boundByName);
        Map<String, Map<String, Supplier<ClassElement>>> g = convert(genericsInfo);
        return new JavaClassElement(classElement, elementAnnotationMetadataFactory, visitorContext, typeMirrors, g, arrayDimensions);
    }

    @Override
    @NonNull
    public Map<String, ClassElement> getTypeArguments() {
        if (resolvedTypeArguments == null) {
            resolvedTypeArguments = resolveTypeArguments();
        }
        return resolvedTypeArguments;
    }

    private Map<String, ClassElement> resolveTypeArguments() {
        List<? extends TypeParameterElement> typeParameters = classElement.getTypeParameters();
        Iterator<? extends TypeParameterElement> tpi = typeParameters.iterator();
        Iterator<? extends TypeMirror> tai = typeArguments.iterator();

        Map<String, ClassElement> map = new LinkedHashMap<>();
        while (tpi.hasNext()) {
            TypeParameterElement tpe = tpi.next();
            TypeMirror tme = tai.hasNext() ? tai.next() : null;
            ClassElement classElement = mirrorToClassElement(tpe.asType(), visitorContext, this.genericTypeInfo, visitorContext.getConfiguration().includeTypeLevelAnnotationsInGenericArguments(), tme instanceof TypeVariable);
            map.put(tpe.toString(), classElement);
        }

        return Collections.unmodifiableMap(map);
    }

    private Map<String, TypeMirror> getBoundTypeMirrors() {
        List<? extends TypeParameterElement> typeParameters = classElement.getTypeParameters();
        Iterator<? extends TypeParameterElement> tpi = typeParameters.iterator();

        Map<String, TypeMirror> map = new LinkedHashMap<>();
        while (tpi.hasNext()) {
            TypeParameterElement tpe = tpi.next();
            TypeMirror t = tpe.asType();
            map.put(tpe.toString(), t);
        }

        return Collections.unmodifiableMap(map);
    }

    @NonNull
    @Override
    public Map<String, Map<String, ClassElement>> getAllTypeArguments() {
        if (resolvedAllTypeArguments == null) {
            Map<String, Map<String, ClassElement>> result = new LinkedHashMap<>();
            Map<String, Map<String, Supplier<ClassElement>>> typeArguments1 = createTypeArguments();
            for (Map.Entry<String, Map<String, Supplier<ClassElement>>> e1 : typeArguments1.entrySet()) {
                for (Map.Entry<String, Supplier<ClassElement>> e2 : e1.getValue().entrySet()) {
                    Map<String, ClassElement> map = result.computeIfAbsent(e1.getKey(), s -> new LinkedHashMap<>());
                    map.put(e2.getKey(), findResolvedGenericArgument(e2.getValue().get()));
                }
            }
            if (!typeArguments.isEmpty()) {
                result.put(JavaModelUtils.getClassName(this.classElement), getTypeArguments());
            }
            resolvedAllTypeArguments = result;
        }
        return resolvedAllTypeArguments;
    }

    private ClassElement findResolvedGenericArgument(ClassElement cl) {
        final Map<String, Supplier<ClassElement>> typeInfo = genericTypeInfo == null ? null : genericTypeInfo.get(getName());
        Supplier<ClassElement> found = null;
        if (cl instanceof JavaGenericPlaceholderElement placeholderElement && typeInfo != null) {
            found = typeInfo.get(placeholderElement.getVariableName());
        }
        return found == null ? cl : found.get();
    }

    /**
     * @return The generic type info for this class.
     */
    Map<String, Map<String, Supplier<ClassElement>>> getGenericTypeInfo() {
        if (genericTypeInfo == null) {
            genericTypeInfo = createTypeArguments();
        }
        return genericTypeInfo;
    }

    private Map<String, Map<String, Supplier<ClassElement>>> createTypeArguments() {
        Map<String, Map<String, TypeMirror>> gt = visitorContext.getGenericUtils()
                .buildGenericTypeArgumentElementInfo(classElement, null, getBoundTypeMirrors());
        Map<String, Map<String, Supplier<ClassElement>>> result = convert(gt);
        return result;
    }

    private Map<String, Map<String, Supplier<ClassElement>>> convert(Map<String, Map<String, TypeMirror>> gt) {
        Map<String, Map<String, Supplier<ClassElement>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, TypeMirror>> e : gt.entrySet()) {
            Map<String, Supplier<ClassElement>> result2 = new LinkedHashMap<>();
            for (Map.Entry<String, TypeMirror> e2 : e.getValue().entrySet()) {
                TypeMirror typeMirror = e2.getValue();
                result2.put(e2.getKey(), SupplierUtil.memoizedNonEmpty(() -> {
                    return mirrorToClassElement(typeMirror, visitorContext);
                }));
            }
            result.put(e.getKey(), result2);
        }
        return result;
    }

    private final class JavaEnclosedElementsQuery extends EnclosedElementsQuery<TypeElement, Element> {

        private List<? extends Element> enclosedElements;

        @Override
        protected Set<Element> getExcludedNativeElements(ElementQuery.Result<?> result) {
            if (result.isExcludePropertyElements()) {
                Set<Element> excludeElements = new HashSet<>();
                for (PropertyElement excludePropertyElement : getBeanProperties()) {
                    excludePropertyElement.getReadMethod().ifPresent(methodElement -> excludeElements.add((Element) methodElement.getNativeType()));
                    excludePropertyElement.getWriteMethod().ifPresent(methodElement -> excludeElements.add((Element) methodElement.getNativeType()));
                    excludePropertyElement.getField().ifPresent(fieldElement -> excludeElements.add((Element) fieldElement.getNativeType()));
                }
                return excludeElements;
            }
            return Collections.emptySet();
        }

        @Override
        protected TypeElement getSuperClass(TypeElement classNode) {
            TypeMirror superclass = classNode.getSuperclass();
            if (superclass instanceof DeclaredType dt) {
                Element element = dt.asElement();
                if (element instanceof TypeElement) {
                    return (TypeElement) element;
                }
            }
            return null;
        }

        @Override
        protected Collection<TypeElement> getInterfaces(TypeElement classNode) {
            List<? extends TypeMirror> interfaces = classNode.getInterfaces();
            Collection<TypeElement> result = new ArrayList<>(interfaces.size());
            for (TypeMirror ifaceMirror : interfaces) {
                final Element ifaceEl = visitorContext.getTypes().asElement(ifaceMirror);
                if (ifaceEl instanceof TypeElement) {
                    result.add((TypeElement) ifaceEl);
                }
            }
            return result;
        }

        @Override
        protected List<Element> getEnclosedElements(TypeElement classNode, ElementQuery.Result<?> result) {
            List<? extends Element> ee;
            if (classNode == classElement) {
                ee = getEnclosedElements();
            } else {
                ee = classNode.getEnclosedElements();
            }
            EnumSet<ElementKind> elementKinds = getElementKind(result);
            return ee.stream().filter(element -> elementKinds.contains(element.getKind())).collect(Collectors.toList());
        }

        @Override
        protected boolean excludeClass(TypeElement classNode) {
            return classNode.getQualifiedName().toString().equals(Object.class.getName())
                || classNode.getQualifiedName().toString().equals(Enum.class.getName());
        }

        @Override
        protected io.micronaut.inject.ast.Element toAstElement(Element enclosedElement) {
            final JavaElementFactory elementFactory = visitorContext.getElementFactory();
            return switch (enclosedElement.getKind()) {
                case METHOD -> elementFactory.newMethodElement(
                    JavaClassElement.this,
                    (ExecutableElement) enclosedElement,
                    elementAnnotationMetadataFactory,
                    genericTypeInfo
                );
                case FIELD -> elementFactory.newFieldElement(
                    JavaClassElement.this,
                    (VariableElement) enclosedElement,
                    elementAnnotationMetadataFactory
                );
                case ENUM_CONSTANT -> elementFactory.newEnumConstantElement(
                    JavaClassElement.this,
                    (VariableElement) enclosedElement,
                    elementAnnotationMetadataFactory
                );
                case CONSTRUCTOR -> elementFactory.newConstructorElement(
                    JavaClassElement.this,
                    (ExecutableElement) enclosedElement,
                    elementAnnotationMetadataFactory
                );
                case CLASS, ENUM -> elementFactory.newClassElement(
                    (TypeElement) enclosedElement,
                    elementAnnotationMetadataFactory
                );
                default -> throw new IllegalStateException("Unknown element: " + enclosedElement);
            };
        }

        private List<? extends Element> getEnclosedElements() {
            if (enclosedElements == null) {
                enclosedElements = classElement.getEnclosedElements();
            }
            return enclosedElements;
        }

        private EnumSet<ElementKind> getElementKind(ElementQuery.Result<?> result) {
            Class<?> elementType = result.getElementType();
            if (elementType == MemberElement.class) {
                return EnumSet.of(ElementKind.FIELD, ElementKind.METHOD);
            } else if (elementType == MethodElement.class) {
                return EnumSet.of(ElementKind.METHOD);
            } else if (elementType == FieldElement.class) {
                if (result.isIncludeEnumConstants()) {
                    return EnumSet.of(ElementKind.FIELD, ElementKind.ENUM_CONSTANT);
                }
                return EnumSet.of(ElementKind.FIELD);
            } else if (elementType == ConstructorElement.class) {
                return EnumSet.of(ElementKind.CONSTRUCTOR);
            } else if (elementType == ClassElement.class) {
                return EnumSet.of(ElementKind.CLASS, ElementKind.ENUM);
            }
            throw new IllegalArgumentException("Unsupported element type for query: " + elementType);
        }

    }

}
