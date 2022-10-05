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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.BeanPropertiesConfiguration;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A class element returning data from a {@link TypeElement}.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class JavaClassElement extends AbstractJavaElement implements ArrayableClassElement {
    private static final String KOTLIN_METADATA = "kotlin.Metadata";
    private static final String PREFIX_IS = "is";
    protected final TypeElement classElement;
    protected final JavaVisitorContext visitorContext;
    final List<? extends TypeMirror> typeArguments;
    private final int arrayDimensions;
    private final boolean isTypeVariable;
    private List<PropertyElement> beanProperties;
    private Map<String, Map<String, TypeMirror>> genericTypeInfo;
    private List<? extends Element> enclosedElements;
    private String simpleName;
    private String name;
    private String packageName;
    private final Map<Element, io.micronaut.inject.ast.Element> elementsCache = new HashMap<>();
    private Map<String, ClassElement> resolvedTypeArguments;

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
        Map<String, Map<String, TypeMirror>> genericsInfo) {
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
        Map<String, Map<String, TypeMirror>> genericsInfo,
        int arrayDimensions) {
        this(classElement, annotationMetadataFactory, visitorContext, typeArguments, genericsInfo, arrayDimensions, false);
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
        Map<String, Map<String, TypeMirror>> genericsInfo,
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
        Map<String, Map<String, TypeMirror>> genericsInfo,
        int arrayDimensions,
        boolean isTypeVariable) {
        super(classElement, annotationMetadataFactory, visitorContext);
        this.classElement = classElement;
        this.visitorContext = visitorContext;
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
                Map<String, ClassElement> typeArgs = new LinkedHashMap<>(forType.size());
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
        final List<? extends TypeMirror> interfaces = classElement.getInterfaces();
        if (!interfaces.isEmpty()) {
            return Collections.unmodifiableList(interfaces.stream().map((mirror) ->
                mirrorToClassElement(mirror, visitorContext, genericTypeInfo)).collect(Collectors.toList())
            );
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        final TypeMirror superclass = classElement.getSuperclass();
        if (superclass != null) {
            final Element element = visitorContext.getTypes().asElement(superclass);

            if (element instanceof TypeElement) {
                TypeElement superElement = (TypeElement) element;
                if (!Object.class.getName().equals(superElement.getQualifiedName().toString())) {
                    // if super type has type arguments, then build a parameterized ClassElement
                    if (superclass instanceof DeclaredType && !((DeclaredType) superclass).getTypeArguments().isEmpty()) {
                        return Optional.of(
                            parameterizedClassElement(
                                superclass,
                                visitorContext,
                                visitorContext.getGenericUtils().buildGenericTypeArgumentElementInfo(classElement, null, getBoundTypeMirrors())));
                    }
                    return Optional.of(
                        new JavaClassElement(
                            superElement,
                            elementAnnotationMetadataFactory,
                            visitorContext
                        )
                    );
                }
            }
        }
        return Optional.empty();
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
            beanProperties = getBeanProperties(BeanPropertiesConfiguration.of(this));
        }
        return Collections.unmodifiableList(beanProperties);
    }

    @Override
    public List<PropertyElement> getBeanProperties(BeanPropertiesConfiguration configuration) {
        if (isRecord()) {
            return AstBeanPropertiesUtils.resolveBeanProperties(configuration,
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
        return AstBeanPropertiesUtils.resolveBeanProperties(configuration,
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
        if (value.type == null) {
            // withSomething() builder setter
            value.type = PrimitiveElement.VOID;
        }
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
                    List<? extends Element> enclosedElements = element.getEnclosedElements();
                    for (Element enclosedElement : enclosedElements) {
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
        Objects.requireNonNull(query, "Query cannot be null");
        ElementQuery.Result<T> result = query.result();
        Set<Element> excludeElements;
        if (result.isExcludePropertyElements()) {
            excludeElements = new HashSet<>();
            for (PropertyElement excludePropertyElement : getBeanProperties()) {
                excludePropertyElement.getReadMethod().ifPresent(methodElement -> excludeElements.add((Element) methodElement.getNativeType()));
                excludePropertyElement.getWriteMethod().ifPresent(methodElement -> excludeElements.add((Element) methodElement.getNativeType()));
                excludePropertyElement.getField().ifPresent(fieldElement -> excludeElements.add((Element) fieldElement.getNativeType()));
            }
        } else {
            excludeElements = Collections.emptySet();
        }
        Elements elements = visitorContext.getElements();
        ElementKind kind = getElementKind(result.getElementType());
        Predicate<Element> predicate = new Predicate<Element>() {

            @Override
            public boolean test(Element element) {
                Element enclosingElement = element.getEnclosingElement();
                if (enclosingElement instanceof TypeElement
                    && ((TypeElement) enclosingElement).getQualifiedName().toString().equals(Enum.class.getName())
                    && element.getKind() == ElementKind.FIELD) {
                    // Skip any fields on Enum but allow to query methods
                    return false;
                }
                ElementKind enclosedElementKind = element.getKind();
                return enclosedElementKind == kind
                    || result.isIncludeEnumConstants() && kind == ElementKind.FIELD && enclosedElementKind == ElementKind.ENUM_CONSTANT
                    || (enclosedElementKind == ElementKind.ENUM && kind == ElementKind.CLASS);
            }
        };

        Predicate<io.micronaut.inject.ast.Element> filter = new Predicate<io.micronaut.inject.ast.Element>() {
            @Override
            public boolean test(io.micronaut.inject.ast.Element element) {
                if (excludeElements.contains(element.getNativeType())) {
                    return false;
                }
                List<Predicate<T>> elementPredicates = result.getElementPredicates();
                if (!elementPredicates.isEmpty()) {
                    for (Predicate<T> elementPredicate : elementPredicates) {
                        if (!elementPredicate.test((T) element)) {
                            return false;
                        }
                    }
                }
                if (element instanceof MethodElement) {
                    MethodElement methodElement = (MethodElement) element;
                    if (result.isOnlyAbstract()) {
                        if (methodElement.getDeclaringType().isInterface() && methodElement.isDefault()) {
                            return false;
                        } else if (!element.isAbstract()) {
                            return false;
                        }
                    } else if (result.isOnlyConcrete()) {
                        if (methodElement.getDeclaringType().isInterface() && !methodElement.isDefault()) {
                            return false;
                        } else if (element.isAbstract()) {
                            return false;
                        }
                    }
                }
                if (result.isOnlyInstance() && element.isStatic()) {
                    return false;
                } else if (result.isOnlyStatic() && !element.isStatic()) {
                    return false;
                }
                if (result.isOnlyAccessible()) {
                    // exclude private members
                    // exclude synthetic members or bridge methods that start with $
                    if (element.isPrivate() || element.getName().startsWith("$")) {
                        return false;
                    }
                    if (element instanceof MemberElement && !((MemberElement) element).isAccessible()) {
                        return false;
                    }
                }
                if (!result.getModifierPredicates().isEmpty()) {
                    Set<ElementModifier> modifiers = element.getModifiers();
                    for (Predicate<Set<ElementModifier>> modifierPredicate : result.getModifierPredicates()) {
                        if (!modifierPredicate.test(modifiers)) {
                            return false;
                        }
                    }
                }
                if (!result.getNamePredicates().isEmpty()) {
                    for (Predicate<String> namePredicate : result.getNamePredicates()) {
                        if (!namePredicate.test(element.getName())) {
                            return false;
                        }
                    }
                }
                if (!result.getAnnotationPredicates().isEmpty()) {
                    for (Predicate<AnnotationMetadata> annotationPredicate : result.getAnnotationPredicates()) {
                        if (!annotationPredicate.test(element)) {
                            return false;
                        }
                    }
                }
                if (!result.getTypePredicates().isEmpty()) {
                    for (Predicate<ClassElement> typePredicate : result.getTypePredicates()) {
                        ClassElement classElement;
                        if (element instanceof ConstructorElement) {
                            classElement = JavaClassElement.this;
                        } else if (element instanceof MethodElement) {
                            classElement = ((MethodElement) element).getGenericReturnType();
                        } else if (element instanceof ClassElement) {
                            classElement = (ClassElement) element;
                        } else {
                            classElement = ((FieldElement) element).getGenericField();
                        }
                        if (!typePredicate.test(classElement)) {
                            return false;
                        }
                    }
                }
//                if (result.isOnlyInjected() && !element.hasDeclaredAnnotation(AnnotationUtil.INJECT)) {
//                    return false;
//                }
                return true;
            }
        };

        BiFunction<io.micronaut.inject.ast.Element, io.micronaut.inject.ast.Element, Boolean> reduce;
        if (result.isIncludeHiddenElements() && result.isIncludeOverriddenMethods()) {
            reduce = (t1, t2) -> false;
        } else {
            reduce = (newElement, existingElement) -> {
                if (!result.isIncludeHiddenElements() && hidden(elements, newElement, existingElement)) {
                    return true;
                }
                if (!result.isIncludeOverriddenMethods()) {
                    if (kind == ElementKind.METHOD) {
                        if (newElement instanceof MethodElement && existingElement instanceof MethodElement) {
                            return ((MethodElement) newElement).overrides((MethodElement) existingElement);
                        }
                    }
                }
                return false;
            };
        }
        return (List<T>) Collections.unmodifiableList(
            getAllElements(classElement, result.isOnlyDeclared(), predicate, reduce)
                .stream()
                .filter(filter)
                .collect(Collectors.toList())
        );
    }

    private static boolean hidden(Elements elements, io.micronaut.inject.ast.Element newElement, io.micronaut.inject.ast.Element existingElement) {
        if (newElement instanceof MemberElement) {
            if (newElement.isStatic() && ((MemberElement) newElement).getDeclaringType().isInterface()) {
                return false;
            }
        }
        return elements.hides((Element) newElement.getNativeType(), (Element) existingElement.getNativeType());
    }

    private Collection<io.micronaut.inject.ast.Element> getAllElements(TypeElement classNode,
                                                                       boolean onlyDeclared,
                                                                       Predicate<Element> predicate,
                                                                       BiFunction<io.micronaut.inject.ast.Element, io.micronaut.inject.ast.Element, Boolean> reduce) {
        Set<io.micronaut.inject.ast.Element> elements = new LinkedHashSet<>();
        List<List<Element>> hierarchy = new ArrayList<>();
        collectHierarchy(classNode, onlyDeclared, predicate, hierarchy);
        for (List<Element> classElements : hierarchy) {
            Set<io.micronaut.inject.ast.Element> addedFromClassElements = new LinkedHashSet<>();
            classElements:
            for (Element element : classElements) {
                io.micronaut.inject.ast.Element newElement = elementsCache.computeIfAbsent(element, this::toAstElement);
                for (Iterator<io.micronaut.inject.ast.Element> iterator = elements.iterator(); iterator.hasNext(); ) {
                    io.micronaut.inject.ast.Element existingElement = iterator.next();
                    if (newElement.equals(existingElement)) {
                        continue;
                    }
                    if (reduce.apply(newElement, existingElement)) {
                        iterator.remove();
                        addedFromClassElements.add(newElement);
                    } else if (reduce.apply(existingElement, newElement)) {
                        continue classElements;
                    }
                }
                addedFromClassElements.add(newElement);
            }
            elements.addAll(addedFromClassElements);
        }
        return elements;
    }

    private void collectHierarchy(TypeElement classNode,
                                  boolean onlyDeclared,
                                  Predicate<Element> predicate,
                                  List<List<Element>> hierarchy) {
        if (classNode.getQualifiedName().toString().equals(Object.class.getName())
            || classNode.getQualifiedName().toString().equals(Enum.class.getName())) {
            return;
        }
        if (!onlyDeclared) {
            TypeMirror superclass = classNode.getSuperclass();
            if (superclass instanceof DeclaredType) {
                DeclaredType dt = (DeclaredType) superclass;
                TypeElement element = (TypeElement) dt.asElement();
                collectHierarchy(element, false, predicate, hierarchy);
            }
            for (TypeMirror ifaceMirror : classNode.getInterfaces()) {
                final Element ifaceEl = visitorContext.getTypes().asElement(ifaceMirror);
                if (ifaceEl instanceof TypeElement) {
                    TypeElement iface = (TypeElement) ifaceEl;
                    List<List<Element>> interfaceElements = new ArrayList<>();
                    collectHierarchy(iface, false, predicate, interfaceElements);
                    hierarchy.addAll(interfaceElements);
                }
            }
        }
        List<? extends Element> enclosedElements;
        if (classNode == classElement) {
            if (this.enclosedElements == null) {
                this.enclosedElements = classElement.getEnclosedElements();
            }
            enclosedElements = this.enclosedElements;
        } else {
            enclosedElements = classNode.getEnclosedElements();
        }
        hierarchy.add(enclosedElements.stream().filter(predicate).collect(Collectors.toList()));
    }

    private io.micronaut.inject.ast.Element toAstElement(Element enclosedElement) {
        final JavaElementFactory elementFactory = visitorContext.getElementFactory();
        switch (enclosedElement.getKind()) {
            case METHOD:
                return elementFactory.newMethodElement(
                    JavaClassElement.this,
                    (ExecutableElement) enclosedElement,
                    elementAnnotationMetadataFactory,
                    genericTypeInfo
                );
            case FIELD:
                return elementFactory.newFieldElement(
                    JavaClassElement.this,
                    (VariableElement) enclosedElement,
                    elementAnnotationMetadataFactory
                );
            case ENUM_CONSTANT:
                return elementFactory.newEnumConstantElement(
                    JavaClassElement.this,
                    (VariableElement) enclosedElement,
                    elementAnnotationMetadataFactory
                );
            case CONSTRUCTOR:
                return elementFactory.newConstructorElement(
                    JavaClassElement.this,
                    (ExecutableElement) enclosedElement,
                    elementAnnotationMetadataFactory
                );
            case CLASS:
            case ENUM:
                return elementFactory.newClassElement(
                    (TypeElement) enclosedElement,
                    elementAnnotationMetadataFactory
                );
            default:
                return null;
        }
    }

    private <T extends io.micronaut.inject.ast.Element> ElementKind getElementKind(Class<T> elementType) {
        if (elementType == MethodElement.class) {
            return ElementKind.METHOD;
        } else if (elementType == FieldElement.class) {
            return ElementKind.FIELD;
        } else if (elementType == ConstructorElement.class) {
            return ElementKind.CONSTRUCTOR;
        } else if (elementType == ClassElement.class) {
            return ElementKind.CLASS;
        }
        throw new IllegalArgumentException("Unsupported element type for query: " + elementType);
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
        if (enclosingElement instanceof javax.lang.model.element.PackageElement) {
            javax.lang.model.element.PackageElement packageElement = (javax.lang.model.element.PackageElement) enclosingElement;
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

    private boolean isAssignable(TypeElement otherElement) {
        Types types = visitorContext.getTypes();
        TypeMirror thisType = types.erasure(classElement.asType());
        TypeMirror thatType = types.erasure(otherElement.asType());
        return types.isAssignable(thisType, thatType);
    }

    @NonNull
    @Override
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
            List<ConstructorElement> constructors = findConstructors();
            Optional<ConstructorElement> annotatedConstructor = constructors.stream()
                .filter(c -> c.hasStereotype(AnnotationUtil.INJECT) || c.hasStereotype(Creator.class))
                .findFirst();
            if (annotatedConstructor.isPresent()) {
                return annotatedConstructor.map(c -> c);
            }
            // with records the record constructor is always the last constructor
            return Optional.of(constructors.get(constructors.size() - 1));
        }
        return ArrayableClassElement.super.getPrimaryConstructor();
    }

    @Override
    public List<MethodElement> findStaticCreators() {
        List<MethodElement> staticCreators = new ArrayList<>(ArrayableClassElement.super.findStaticCreators());
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
            if (enclosingElement instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) enclosingElement;
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
        return new JavaClassElement(classElement, elementAnnotationMetadataFactory, visitorContext, typeMirrors, genericsInfo, arrayDimensions);
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

        Map<String, ClassElement> map = new LinkedHashMap<>();
        while (tpi.hasNext()) {
            TypeParameterElement tpe = tpi.next();
            ClassElement classElement = mirrorToClassElement(tpe.asType(), visitorContext, this.genericTypeInfo, visitorContext.getConfiguration().includeTypeLevelAnnotationsInGenericArguments());
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
        Map<String, TypeMirror> typeArguments = getBoundTypeMirrors();
        Map<String, Map<String, TypeMirror>> info = visitorContext.getGenericUtils()
            .buildGenericTypeArgumentElementInfo(
                classElement,
                null,
                typeArguments
            );
        Map<String, Map<String, ClassElement>> result = new LinkedHashMap<>(info.size());
        info.forEach((name, generics) -> {
            Map<String, ClassElement> resolved = new LinkedHashMap<>(generics.size());
            generics.forEach((variable, mirror) -> {
                final Map<String, TypeMirror> typeInfo = this.genericTypeInfo != null ? this.genericTypeInfo.get(getName()) : null;
                TypeMirror resolvedType = mirror;
                if (mirror instanceof TypeVariable && typeInfo != null) {
                    final TypeMirror tm = typeInfo.get(mirror.toString());
                    if (tm != null) {
                        resolvedType = tm;
                    }
                }
                ClassElement classElement = mirrorToClassElement(
                    resolvedType,
                    visitorContext,
                    info,
                    visitorContext.getConfiguration().includeTypeLevelAnnotationsInGenericArguments(),
                    mirror instanceof TypeVariable
                );
                resolved.put(variable, classElement);
            });
            result.put(name, resolved);
        });

        if (!typeArguments.isEmpty()) {
            result.put(JavaModelUtils.getClassName(this.classElement), getTypeArguments());
        }
        return result;
    }

    /**
     * @return The generic type info for this class.
     */
    Map<String, Map<String, TypeMirror>> getGenericTypeInfo() {
        if (genericTypeInfo == null) {
            genericTypeInfo = visitorContext.getGenericUtils().buildGenericTypeArgumentElementInfo(classElement, null, getBoundTypeMirrors());
        }
        return genericTypeInfo;
    }

}
