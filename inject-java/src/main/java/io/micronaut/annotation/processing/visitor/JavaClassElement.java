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

import io.micronaut.annotation.processing.ModelUtils;
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
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils;
import io.micronaut.inject.ast.BeanPropertiesConfiguration;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static javax.lang.model.element.Modifier.STATIC;

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

    public TypeElement getNativeTypeElement() {
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
                this::mapToPropertyElement);
        }
        return AstBeanPropertiesUtils.resolveBeanProperties(configuration,
            this,
            () -> AstBeanPropertiesUtils.getSubtypeFirstMethods(this),
            () -> AstBeanPropertiesUtils.getSubtypeFirstFields(this),
            false,
            Collections.emptySet(),
            methodElement -> {
                String methodName = methodElement.getSimpleName();
                if (isKotlinClass(((Element) methodElement.getNativeType()).getEnclosingElement()) && methodName.startsWith(PREFIX_IS)) {
                    return Optional.of(methodName);
                }
                return Optional.empty();
            }, this::mapToPropertyElement);
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
        ElementKind kind = getElementKind(result.getElementType());
        List<T> resultingElements = new ArrayList<>();

        List<Element> collectedElements = new ArrayList<>(getDeclaredEnclosedElements());

        if (!result.isOnlyDeclared()) {
            Elements elements = visitorContext.getElements();

            collectFromSubtypes(result, kind, elements, collectedElements);
            collectFromInterfaces(result, kind, elements, collectedElements);

            if (result.isOnlyAbstract()) {
                if (isInterface()) {
                    collectedElements.removeIf((e) -> e.getModifiers().contains(Modifier.DEFAULT));
                } else {
                    collectedElements.removeIf((e) -> !e.getModifiers().contains(Modifier.ABSTRACT));
                }
            } else if (result.isOnlyConcrete()) {
                if (isInterface()) {
                    collectedElements.removeIf((e) -> !e.getModifiers().contains(Modifier.DEFAULT));
                } else {
                    collectedElements.removeIf((e) -> e.getModifiers().contains(Modifier.ABSTRACT));
                }
            }
        }
        if (result.isOnlyInstance()) {
            collectedElements.removeIf((e) -> e.getModifiers().contains(Modifier.STATIC));
        } else if (result.isOnlyStatic()) {
            collectedElements.removeIf((e) -> !e.getModifiers().contains(Modifier.STATIC));
        }
        List<Predicate<Set<ElementModifier>>> modifierPredicates = result.getModifierPredicates();
        List<Predicate<String>> namePredicates = result.getNamePredicates();
        List<Predicate<AnnotationMetadata>> annotationPredicates = result.getAnnotationPredicates();
        final List<Predicate<ClassElement>> typePredicates = result.getTypePredicates();
        boolean hasNamePredicates = !namePredicates.isEmpty();
        boolean hasModifierPredicates = !modifierPredicates.isEmpty();
        boolean hasAnnotationPredicates = !annotationPredicates.isEmpty();
        boolean hasTypePredicates = !typePredicates.isEmpty();
        boolean onlyAccessible = result.isOnlyAccessible();
        boolean includeEnumConstants = result.isIncludeEnumConstants();
        final JavaElementFactory elementFactory = visitorContext.getElementFactory();
        boolean excludePropertyElements = result.isExcludePropertyElements();
        Set<Element> excludeElements;
        if (excludePropertyElements) {
            excludeElements = new HashSet<>();
            for (PropertyElement excludePropertyElement : getBeanProperties()) {
                excludePropertyElement.getReadMethod().ifPresent(methodElement -> excludeElements.add((Element) methodElement.getNativeType()));
                excludePropertyElement.getWriteMethod().ifPresent(methodElement -> excludeElements.add((Element) methodElement.getNativeType()));
                excludePropertyElement.getField().ifPresent(fieldElement -> excludeElements.add((Element) fieldElement.getNativeType()));
            }
        } else {
            excludeElements = Collections.emptySet();
        }

        elementLoop:
        for (Element enclosedElement : collectedElements) {
            if (excludeElements.contains(enclosedElement)) {
                continue;
            }
            ElementKind enclosedElementKind = enclosedElement.getKind();
            if (enclosedElementKind == kind
                || includeEnumConstants && kind == ElementKind.FIELD && enclosedElementKind == ElementKind.ENUM_CONSTANT
                || (enclosedElementKind == ElementKind.ENUM && kind == ElementKind.CLASS)) {
                String elementName = enclosedElement.getSimpleName().toString();
                if (onlyAccessible) {
                    // exclude private members
                    if (enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                        continue;
                    } else if (elementName.startsWith("$")) {
                        // exclude synthetic members or bridge methods that start with $
                        continue;
                    } else {
                        Element enclosingElement = enclosedElement.getEnclosingElement();
                        final ClassElement onlyAccessibleFrom = result.getOnlyAccessibleFromType().orElse(this);
                        Object accessibleFrom = onlyAccessibleFrom.getNativeType();
                        // if the outer element of the enclosed element is not the current class
                        // we need to check if it package private and within a different package so it can be excluded
                        if (enclosingElement != accessibleFrom && visitorContext.getModelUtils().isPackagePrivate(enclosedElement)) {
                            if (enclosingElement instanceof TypeElement) {
                                Name qualifiedName = ((TypeElement) enclosingElement).getQualifiedName();
                                String packageName = NameUtils.getPackageName(qualifiedName.toString());
                                if (!packageName.equals(onlyAccessibleFrom.getPackageName())) {
                                    continue;
                                }
                            }
                        }
                    }
                }

                if (hasModifierPredicates) {
                    Set<ElementModifier> modifiers = enclosedElement
                        .getModifiers().stream().map(m -> ElementModifier.valueOf(m.name())).collect(Collectors.toSet());
                    for (Predicate<Set<ElementModifier>> modifierPredicate : modifierPredicates) {
                        if (!modifierPredicate.test(modifiers)) {
                            continue elementLoop;
                        }
                    }
                }

                if (hasNamePredicates) {
                    for (Predicate<String> namePredicate : namePredicates) {
                        if (!namePredicate.test(elementName)) {
                            continue elementLoop;
                        }
                    }
                }

                T element;

                switch (enclosedElementKind) {
                    case METHOD:

                        final ExecutableElement executableElement = (ExecutableElement) enclosedElement;
                        //noinspection unchecked
                        element = (T) elementFactory.newMethodElement(
                            this,
                            executableElement,
                            elementAnnotationMetadataFactory,
                            genericTypeInfo
                        );
                        break;
                    case FIELD:
                        //noinspection unchecked
                        element = (T) elementFactory.newFieldElement(
                            this,
                            (VariableElement) enclosedElement,
                            elementAnnotationMetadataFactory
                        );
                        break;
                    case ENUM_CONSTANT:
                        //noinspection unchecked
                        element = (T) elementFactory.newEnumConstantElement(
                            this,
                            (VariableElement) enclosedElement,
                            elementAnnotationMetadataFactory
                        );
                        break;
                    case CONSTRUCTOR:
                        //noinspection unchecked
                        element = (T) elementFactory.newConstructorElement(
                            this,
                            (ExecutableElement) enclosedElement,
                            elementAnnotationMetadataFactory
                        );
                        break;
                    case CLASS:
                    case ENUM:
                        //noinspection unchecked
                        element = (T) elementFactory.newClassElement(
                            (TypeElement) enclosedElement,
                            elementAnnotationMetadataFactory
                        );
                        break;
                    default:
                        element = null;
                }

                if (element != null) {
                    if (hasAnnotationPredicates) {
                        for (Predicate<AnnotationMetadata> annotationPredicate : annotationPredicates) {
                            if (!annotationPredicate.test(element)) {
                                continue elementLoop;
                            }
                        }
                    }
                    if (hasTypePredicates) {
                        for (Predicate<ClassElement> typePredicate : typePredicates) {
                            ClassElement classElement;
                            if (element instanceof ConstructorElement) {
                                classElement = this;
                            } else if (element instanceof MethodElement) {
                                classElement = ((MethodElement) element).getGenericReturnType();
                            } else if (element instanceof ClassElement) {
                                classElement = (ClassElement) element;
                            } else {
                                classElement = ((FieldElement) element).getGenericField();
                            }
                            if (!typePredicate.test(classElement)) {
                                continue elementLoop;
                            }
                        }
                    }
                    List<Predicate<T>> elementPredicates = result.getElementPredicates();
                    if (!elementPredicates.isEmpty()) {
                        for (Predicate<T> elementPredicate : elementPredicates) {
                            if (!elementPredicate.test(element)) {
                                continue elementLoop;
                            }
                        }
                    }
                    resultingElements.add(element);
                }
            }
        }
        return Collections.unmodifiableList(resultingElements);
    }

    private <T extends io.micronaut.inject.ast.Element> void collectFromInterfaces(ElementQuery.Result<T> result,
                                                                                   ElementKind kind,
                                                                                   Elements elements,
                                                                                   List<Element> enclosedElements) {
        boolean onlyAbstract = result.isOnlyAbstract();
        boolean onlyConcrete = result.isOnlyConcrete();
        boolean includeOverriddenMethods = result.isIncludeOverriddenMethods();
        boolean includeHiddenElements = result.isIncludeHiddenElements();

        if (kind == ElementKind.METHOD || kind == ElementKind.FIELD) {
            // if the element kind is interfaces then we need to go through interfaces as well
            Set<TypeElement> allInterfaces = visitorContext.getModelUtils().getAllInterfaces(this.classElement);
            Collection<TypeElement> interfacesToProcess = new ArrayList<>(allInterfaces.size());
            // Remove duplicates
            outer:
            for (TypeElement el : allInterfaces) {
                for (TypeElement existingEl : interfacesToProcess) {
                    Name qualifiedName = existingEl.getQualifiedName();
                    if (qualifiedName.equals(el.getQualifiedName())) {
                        continue outer;
                    }
                }
                interfacesToProcess.add(el);
            }
            for (TypeElement itfe : interfacesToProcess) {
                List<? extends Element> interfaceElements = itfe.getEnclosedElements();
                interfaceElements:
                for (Element interfaceElement : interfaceElements) {
                    if (interfaceElement.getKind() == ElementKind.METHOD) {
                        ExecutableElement ee = (ExecutableElement) interfaceElement;
                        if (onlyAbstract && ee.getModifiers().contains(Modifier.DEFAULT)) {
                            continue;
                        } else if (onlyConcrete && !ee.getModifiers().contains(Modifier.DEFAULT)) {
                            continue;
                        }

                        for (Element enclosedElement : enclosedElements) {
                            if (enclosedElement.getKind() == ElementKind.METHOD) {
                                if (!includeOverriddenMethods && elements.overrides((ExecutableElement) enclosedElement, ee, this.classElement)) {
                                    continue interfaceElements;
                                }
                            }
                        }
                    }
                    enclosedElements.add(interfaceElement);
                }
            }
        }
    }

    private <T extends io.micronaut.inject.ast.Element> void collectFromSubtypes(ElementQuery.Result<T> result,
                                                                                 ElementKind kind,
                                                                                 Elements elements,
                                                                                 List<Element> enclosedElements
    ) {

        boolean includeOverriddenMethods = result.isIncludeOverriddenMethods();
        boolean includeHiddenElements = result.isIncludeHiddenElements();

        TypeMirror superclass = classElement.getSuperclass();
        // traverse the super class true and add elements that are not overridden
        while (superclass instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) superclass;
            TypeElement element = (TypeElement) dt.asElement();
            // reached non-accessible class like Object, Enum, Record etc.
            if (element.getQualifiedName().toString().startsWith("java.lang.")) {
                break;
            }
            List<? extends Element> superElements = element.getEnclosedElements();

            List<Element> elementsToAdd = new ArrayList<>(superElements.size());
            superElements:
            for (Element superElement : superElements) {
                ElementKind superKind = superElement.getKind();
                if (superKind == kind) {
                    for (Element enclosedElement : enclosedElements) {
                        if (!includeHiddenElements && elements.hides(enclosedElement, superElement)) {
                            continue superElements;
                        } else if (enclosedElement.getKind() == ElementKind.METHOD && superElement.getKind() == ElementKind.METHOD) {
                            final ExecutableElement methodCandidate = (ExecutableElement) superElement;
                            if (!includeOverriddenMethods && elements.overrides((ExecutableElement) enclosedElement, methodCandidate, this.classElement)) {
                                continue superElements;
                            }
                        }
                    }
                    // dependency injection method resolution requires extended overrides checks
                    if (result.isOnlyInjected() && superElement.getKind() == ElementKind.METHOD) {
                        final ExecutableElement methodCandidate = (ExecutableElement) superElement;
                        // check for extended override
                        final String thisClassName = this.classElement.getQualifiedName().toString();
                        final String declaringClassName = element.getQualifiedName().toString();
                        boolean isParent = !declaringClassName.equals(thisClassName);
                        final ModelUtils javaModelUtils = visitorContext.getModelUtils();
                        final ExecutableElement overridingMethod = javaModelUtils
                            .overridingOrHidingMethod(methodCandidate, this.classElement, false)
                            .orElse(methodCandidate);
                        TypeElement overridingClass = javaModelUtils.classElementFor(overridingMethod);
                        boolean overridden = isParent && overridingClass != null &&
                            !overridingClass.getQualifiedName().toString().equals(declaringClassName);

                        boolean isPackagePrivate = javaModelUtils.isPackagePrivate(methodCandidate);
                        boolean isPrivate = methodCandidate.getModifiers().contains(Modifier.PRIVATE);
                        if (overridden && !(isPrivate || isPackagePrivate)) {
                            // bail out if the method has been overridden, since it will have already been handled
                            continue;
                        }
                        if (isParent && overridden) {
                            String packageOfOverridingClass = visitorContext.getElements().getPackageOf(overridingMethod).getQualifiedName().toString();
                            String packageOfDeclaringClass = visitorContext.getElements().getPackageOf(element).getQualifiedName().toString();
                            boolean isPackagePrivateAndPackagesDiffer = isPackagePrivate && !packageOfOverridingClass.equals(packageOfDeclaringClass);
                            if (!isPackagePrivateAndPackagesDiffer && !isPrivate) {
                                MethodElement methodElement = visitorContext.getElementFactory()
                                    .newMethodElement(this, overridingMethod, elementAnnotationMetadataFactory);
                                boolean overriddenInjected = methodElement.hasDeclaredAnnotation(AnnotationUtil.INJECT);
                                if (!overriddenInjected) {
                                    // bail out if the overridden method is package private and in the same package
                                    // and is not annotated with @Inject
                                    continue;
                                }
                            }
                        }
                    }
                    if (result.isOnlyAbstract() && !superElement.getModifiers().contains(Modifier.ABSTRACT)) {
                        continue;
                    } else if (result.isOnlyConcrete() && superElement.getModifiers().contains(Modifier.ABSTRACT)) {
                        continue;
                    } else if (result.isOnlyInstance() && superElement.getModifiers().contains(Modifier.STATIC)) {
                        continue;
                    } else if (result.isOnlyStatic() && !superElement.getModifiers().contains(Modifier.STATIC)) {
                        continue;
                    }
                    elementsToAdd.add(superElement);
                }
            }
            enclosedElements.addAll(elementsToAdd);
            superclass = element.getSuperclass();
        }
    }

    private List<? extends Element> getDeclaredEnclosedElements() {
        if (this.enclosedElements == null) {
            this.enclosedElements = classElement.getEnclosedElements();
        }
        return this.enclosedElements;
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
        if (staticCreators.isEmpty()) {
            return ElementFilter.typesIn(enclosedElements)
                .stream()
                .filter(type -> type.getSimpleName().toString().equals("Companion"))
                .filter(type -> type.getModifiers().contains(STATIC))
                .flatMap(typeElement -> visitorContext.getElementFactory().newClassElement(typeElement, elementAnnotationMetadataFactory)
                    .getEnclosedElements(ElementQuery.ALL_METHODS.annotated(annotationMetadata -> annotationMetadata.hasStereotype(Creator.class))).stream())
                .filter(method -> method.isPrivate() && method.getReturnType().equals(this))
                .collect(Collectors.toList());
        }
        return staticCreators;
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
                Optional<ClassElement> classElement1 = visitorContext.getClassElement(element.getName(), elementAnnotationMetadataFactory);
                return visitorContext.getTypes().getDeclaredType(
                    ((JavaClassElement) classElement1.get()).classElement,
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
    public @NonNull
    Map<String, ClassElement> getTypeArguments() {
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
