/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.annotation.processing.ModelUtils;
import io.micronaut.annotation.processing.PublicMethodVisitor;
import io.micronaut.annotation.processing.SuperclassAwareTypeVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
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
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     */
    @Internal
    public JavaClassElement(TypeElement classElement, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        this(classElement, annotationMetadata, visitorContext, Collections.emptyList(), null, 0, false);
    }

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     * @param typeArguments      The declared type arguments
     * @param genericsInfo       The generic type info
     */
    JavaClassElement(
            TypeElement classElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext,
            List<? extends TypeMirror> typeArguments,
            Map<String, Map<String, TypeMirror>> genericsInfo) {
        this(classElement, annotationMetadata, visitorContext, typeArguments, genericsInfo, 0, false);
    }

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     * @param typeArguments      The declared type arguments
     * @param genericsInfo       The generic type info
     * @param arrayDimensions    The number of array dimensions
     */
    JavaClassElement(
            TypeElement classElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext,
            List<? extends TypeMirror> typeArguments,
            Map<String, Map<String, TypeMirror>> genericsInfo,
            int arrayDimensions) {
        this(classElement, annotationMetadata, visitorContext, typeArguments, genericsInfo, arrayDimensions, false);
    }

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     * @param typeArguments      The declared type arguments
     * @param genericsInfo       The generic type info
     * @param isTypeVariable     Is the class element a type variable
     */
    JavaClassElement(
            TypeElement classElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext,
            List<? extends TypeMirror> typeArguments,
            Map<String, Map<String, TypeMirror>> genericsInfo,
            boolean isTypeVariable) {
        this(classElement, annotationMetadata, visitorContext, typeArguments, genericsInfo, 0, isTypeVariable);
    }

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     * @param typeArguments      The declared type arguments
     * @param genericsInfo       The generic type info
     * @param arrayDimensions    The number of array dimensions
     * @param isTypeVariable     Is the type a type variable
     */
    JavaClassElement(
            TypeElement classElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext,
            List<? extends TypeMirror> typeArguments,
            Map<String, Map<String, TypeMirror>> genericsInfo,
            int arrayDimensions,
            boolean isTypeVariable) {
        super(classElement, annotationMetadata, visitorContext);
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
                                    visitorContext.getAnnotationUtils().getAnnotationMetadata(superElement),
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
        if (this.beanProperties == null) {

            Map<String, BeanPropertyData> props = new LinkedHashMap<>();
            Map<String, VariableElement> fields = new LinkedHashMap<>();
            if (isRecord()) {
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
                        String name = element.getSimpleName().toString();
                        if (element instanceof ExecutableElement) {
                            BeanPropertyData beanPropertyData = props.get(name);
                            if (beanPropertyData != null) {
                                beanPropertyData.getter = (ExecutableElement) element;
                            }
                        } else {

                            props.computeIfAbsent(name, propertyName -> {

                                BeanPropertyData beanPropertyData = new BeanPropertyData(propertyName);
                                beanPropertyData.declaringType = JavaClassElement.this;
                                beanPropertyData.type = mirrorToClassElement(element.asType(), visitorContext, genericTypeInfo, true);
                                return beanPropertyData;
                            });
                        }
                    }

                }, null);
            } else {

                classElement.asType().accept(new PublicMethodVisitor<Object, Object>(visitorContext) {

                    @Override
                    protected boolean isAcceptable(javax.lang.model.element.Element element) {
                        if (element.getKind() == ElementKind.FIELD) {
                            return true;
                        }
                        if (element.getKind() == ElementKind.METHOD && element instanceof ExecutableElement) {
                            Set<Modifier> modifiers = element.getModifiers();
                            if (modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.STATIC)) {
                                ExecutableElement executableElement = (ExecutableElement) element;
                                String methodName = executableElement.getSimpleName().toString();
                                if (methodName.contains("$")) {
                                    return false;
                                }

                                if (NameUtils.isGetterName(methodName) && executableElement.getParameters().isEmpty()) {
                                    return true;
                                } else {
                                    return NameUtils.isSetterName(methodName) && executableElement.getParameters().size() == 1;
                                }
                            }
                        }
                        return false;
                    }

                    @Override
                    protected void accept(DeclaredType declaringType, javax.lang.model.element.Element element, Object o) {

                        if (element instanceof VariableElement) {
                            fields.put(element.getSimpleName().toString(), (VariableElement) element);
                            return;
                        }


                        ExecutableElement executableElement = (ExecutableElement) element;
                        String methodName = executableElement.getSimpleName().toString();
                        final TypeElement declaringTypeElement = (TypeElement) executableElement.getEnclosingElement();

                        if (NameUtils.isGetterName(methodName) && executableElement.getParameters().isEmpty()) {
                            String propertyName = NameUtils.getPropertyNameForGetter(methodName);
                            TypeMirror returnType = executableElement.getReturnType();
                            ClassElement getterReturnType;
                            if (returnType instanceof TypeVariable) {
                                TypeVariable tv = (TypeVariable) returnType;
                                final String tvn = tv.toString();
                                final ClassElement classElement = getTypeArguments().get(tvn);
                                if (classElement != null) {
                                    getterReturnType = classElement;
                                } else {
                                    getterReturnType = mirrorToClassElement(returnType, visitorContext, JavaClassElement.this.genericTypeInfo, true);
                                }
                            } else {
                                getterReturnType = mirrorToClassElement(returnType, visitorContext, JavaClassElement.this.genericTypeInfo, true);
                            }

                            BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
                            configureDeclaringType(declaringTypeElement, beanPropertyData);
                            beanPropertyData.type = getterReturnType;
                            beanPropertyData.getter = executableElement;
                            if (beanPropertyData.setter != null) {
                                TypeMirror typeMirror = beanPropertyData.setter.getParameters().get(0).asType();
                                ClassElement setterParameterType = mirrorToClassElement(typeMirror, visitorContext, JavaClassElement.this.genericTypeInfo, true);
                                if (!setterParameterType.getName().equals(getterReturnType.getName())) {
                                    beanPropertyData.setter = null; // not a compatible setter
                                }
                            }
                        } else if (NameUtils.isSetterName(methodName) && executableElement.getParameters().size() == 1) {
                            String propertyName = NameUtils.getPropertyNameForSetter(methodName);
                            TypeMirror typeMirror = executableElement.getParameters().get(0).asType();
                            ClassElement setterParameterType = mirrorToClassElement(typeMirror, visitorContext, JavaClassElement.this.genericTypeInfo, true);

                            BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
                            configureDeclaringType(declaringTypeElement, beanPropertyData);
                            ClassElement propertyType = beanPropertyData.type;
                            if (propertyType != null) {
                                if (propertyType.getName().equals(setterParameterType.getName())) {
                                    beanPropertyData.setter = executableElement;
                                }
                            } else {
                                beanPropertyData.setter = executableElement;
                            }
                        }
                    }

                    private void configureDeclaringType(TypeElement declaringTypeElement, BeanPropertyData beanPropertyData) {
                        if (beanPropertyData.declaringType == null && !classElement.equals(declaringTypeElement)) {
                            beanPropertyData.declaringType = mirrorToClassElement(
                                    declaringTypeElement.asType(),
                                    visitorContext,
                                    genericTypeInfo,
                                    true);
                        }
                    }
                }, null);
            }

            if (!props.isEmpty()) {
                this.beanProperties = new ArrayList<>(props.size());
                for (Map.Entry<String, BeanPropertyData> entry : props.entrySet()) {
                    String propertyName = entry.getKey();
                    BeanPropertyData value = entry.getValue();
                    final VariableElement fieldElement = fields.get(propertyName);

                    if (value.getter != null) {
                        final AnnotationMetadata annotationMetadata;
                        if (fieldElement != null) {
                            annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(fieldElement, value.getter);
                        } else {
                            annotationMetadata = visitorContext
                                    .getAnnotationUtils()
                                    .newAnnotationBuilder().buildForMethod(value.getter);
                        }

                        JavaPropertyElement propertyElement = new JavaPropertyElement(
                                value.declaringType == null ? this : value.declaringType,
                                value.getter,
                                annotationMetadata,
                                propertyName,
                                value.type,
                                value.setter == null,
                                visitorContext) {

                            @Override
                            public ClassElement getGenericType() {
                                TypeMirror propertyType = value.getter.getReturnType();
                                if (fieldElement != null) {
                                    TypeMirror fieldType = fieldElement.asType();
                                    if (visitorContext.getTypes().isAssignable(fieldType, propertyType)) {
                                        propertyType = fieldType;
                                    }
                                }
                                Map<String, Map<String, TypeMirror>> declaredGenericInfo = getGenericTypeInfo();
                                return parameterizedClassElement(propertyType, visitorContext, declaredGenericInfo);
                            }

                            @Override
                            public Optional<String> getDocumentation() {
                                Elements elements = visitorContext.getElements();
                                String docComment = elements.getDocComment(value.getter);
                                return Optional.ofNullable(docComment);
                            }

                            @Override
                            public Optional<MethodElement> getWriteMethod() {
                                if (value.setter != null) {
                                    return Optional.of(new JavaMethodElement(
                                            JavaClassElement.this,
                                            value.setter,
                                            visitorContext.getAnnotationUtils().newAnnotationBuilder().buildForMethod(value.setter),
                                            visitorContext
                                    ));
                                }
                                return Optional.empty();
                            }

                            @Override
                            public Optional<MethodElement> getReadMethod() {
                                return Optional.of(new JavaMethodElement(
                                        JavaClassElement.this,
                                        value.getter,
                                        annotationMetadata,
                                        visitorContext
                                ));
                            }
                        };
                         beanProperties.add(propertyElement);
                    }
                }
                this.beanProperties = Collections.unmodifiableList(beanProperties);
            } else {
                this.beanProperties = Collections.emptyList();
            }
        }
        return Collections.unmodifiableList(beanProperties);
    }

    @Override
    public <T extends io.micronaut.inject.ast.Element> List<T> getEnclosedElements(@NonNull ElementQuery<T> query) {
        Objects.requireNonNull(query, "Query cannot be null");
        ElementQuery.Result<T> result = query.result();
        ElementKind kind = getElementKind(result.getElementType());
        List<T> resultingElements = new ArrayList<>();
        List<Element> enclosedElements = new ArrayList<>(getDeclaredEnclosedElements());

        boolean onlyDeclared = result.isOnlyDeclared();
        boolean onlyAbstract = result.isOnlyAbstract();
        boolean onlyConcrete = result.isOnlyConcrete();
        boolean onlyInstance = result.isOnlyInstance();

        if (!onlyDeclared) {
            Elements elements = visitorContext.getElements();

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
                            if (elements.hides(enclosedElement, superElement)) {
                                continue superElements;
                            } else if (enclosedElement.getKind() == ElementKind.METHOD && superElement.getKind() == ElementKind.METHOD) {
                                final ExecutableElement methodCandidate = (ExecutableElement) superElement;
                                if (elements.overrides((ExecutableElement) enclosedElement, methodCandidate, this.classElement)) {
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

                                boolean overriddenInjected = overridden && visitorContext.getAnnotationUtils()
                                        .getAnnotationMetadata(overridingMethod).hasDeclaredAnnotation(
                                                AnnotationUtil.INJECT);
                                String packageOfOverridingClass = visitorContext.getElements().getPackageOf(overridingMethod).getQualifiedName().toString();
                                String packageOfDeclaringClass = visitorContext.getElements().getPackageOf(element).getQualifiedName().toString();
                                boolean isPackagePrivateAndPackagesDiffer = overridden && isPackagePrivate &&
                                        !packageOfOverridingClass.equals(packageOfDeclaringClass);
                                if (!overriddenInjected && !isPackagePrivateAndPackagesDiffer && !isPrivate) {
                                    // bail out if the overridden method is package private and in the same package
                                    // and is not annotated with @Inject
                                    continue;
                                }
                            }
                        }

                        if (onlyAbstract && !superElement.getModifiers().contains(Modifier.ABSTRACT)) {
                            continue;
                        } else if (onlyConcrete && superElement.getModifiers().contains(Modifier.ABSTRACT)) {
                            continue;
                        } else if (onlyInstance && superElement.getModifiers().contains(Modifier.STATIC)) {
                            continue;
                        }
                        elementsToAdd.add(superElement);
                    }
                }
                enclosedElements.addAll(elementsToAdd);
                superclass = element.getSuperclass();
            }

            if (kind == ElementKind.METHOD) {
                // if the element kind is interfaces then we need to go through interfaces as well
                Set<TypeElement> allInterfaces = visitorContext.getModelUtils().getAllInterfaces(this.classElement);
                List<Element> elementsToAdd = new ArrayList<>(allInterfaces.size());
                for (TypeElement itfe : allInterfaces) {
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
                                    if (elements.overrides((ExecutableElement) enclosedElement, ee, this.classElement)) {
                                        continue interfaceElements;
                                    }
                                }
                            }
                            elementsToAdd.add(interfaceElement);
                        }
                    }
                }
                enclosedElements.addAll(elementsToAdd);
                elementsToAdd.clear();
            }
        }
        boolean onlyAccessible = result.isOnlyAccessible();
        if (kind == ElementKind.METHOD) {
            if (onlyAbstract) {
                if (isInterface()) {
                    enclosedElements.removeIf((e) -> e.getModifiers().contains(Modifier.DEFAULT));
                } else {
                    enclosedElements.removeIf((e) -> !e.getModifiers().contains(Modifier.ABSTRACT));
                }
            } else if (onlyConcrete) {
                if (isInterface()) {
                    enclosedElements.removeIf((e) -> !e.getModifiers().contains(Modifier.DEFAULT));
                } else {
                    enclosedElements.removeIf((e) -> e.getModifiers().contains(Modifier.ABSTRACT));
                }
            }
        }
        if (onlyInstance) {
            enclosedElements.removeIf((e) -> e.getModifiers().contains(Modifier.STATIC));
        }
        List<Predicate<Set<ElementModifier>>> modifierPredicates = result.getModifierPredicates();
        List<Predicate<String>> namePredicates = result.getNamePredicates();
        List<Predicate<AnnotationMetadata>> annotationPredicates = result.getAnnotationPredicates();
        final List<Predicate<ClassElement>> typePredicates = result.getTypePredicates();
        boolean hasNamePredicates = !namePredicates.isEmpty();
        boolean hasModifierPredicates = !modifierPredicates.isEmpty();
        boolean hasAnnotationPredicates = !annotationPredicates.isEmpty();
        boolean hasTypePredicates = !typePredicates.isEmpty();
        final JavaElementFactory elementFactory = visitorContext.getElementFactory();

        elementLoop:
        for (Element enclosedElement : enclosedElements) {
            ElementKind enclosedElementKind = enclosedElement.getKind();
            if (enclosedElementKind == kind || (enclosedElementKind == ElementKind.ENUM && kind == ElementKind.CLASS)) {
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

                final AnnotationMetadata metadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(enclosedElement);
                if (hasAnnotationPredicates) {
                    for (Predicate<AnnotationMetadata> annotationPredicate : annotationPredicates) {
                        if (!annotationPredicate.test(metadata)) {
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
                                metadata,
                                genericTypeInfo
                        );
                    break;
                    case FIELD:
                        //noinspection unchecked
                        element = (T) elementFactory.newFieldElement(
                                this,
                                (VariableElement) enclosedElement,
                                metadata
                        );
                    break;
                    case CONSTRUCTOR:
                        //noinspection unchecked
                        element = (T) elementFactory.newConstructorElement(
                                this,
                                (ExecutableElement) enclosedElement,
                                metadata
                        );
                    break;
                    case CLASS:
                    case ENUM:
                        //noinspection unchecked
                        element = (T) elementFactory.newClassElement(
                                (TypeElement) enclosedElement,
                                metadata
                        );
                    break;
                    default:
                        element = null;
                }

                if (element != null) {
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
        return new JavaClassElement(classElement, getAnnotationMetadata(), visitorContext, typeArguments, getGenericTypeInfo(), arrayDimensions, false);
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
            return new JavaPackageElement(
                    ((javax.lang.model.element.PackageElement) enclosingElement),
                    visitorContext.getAnnotationUtils().getAnnotationMetadata(enclosingElement),
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
            Types types = visitorContext.getTypes();
            TypeMirror thisType = types.erasure(classElement.asType());
            TypeMirror thatType = types.erasure(otherElement.asType());
            return types.isAssignable(thisType, thatType);
        }
        return false;
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        if (type.isPrimitive()) {
            return isAssignable(type.getName());
        } else {
            Object nativeType = type.getNativeType();
            if (nativeType instanceof TypeElement) {
                Types types = visitorContext.getTypes();
                TypeMirror thisType = types.erasure(classElement.asType());
                TypeMirror thatType = types.erasure(((TypeElement) nativeType).asType());
                return types.isAssignable(thisType, thatType);
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        final AnnotationUtils annotationUtils = visitorContext.getAnnotationUtils();
        final ModelUtils modelUtils = visitorContext.getModelUtils();
        ExecutableElement method = modelUtils.staticCreatorFor(classElement, annotationUtils);
        if (method == null) {
            if (isInner() && !isStatic()) {
                // only static inner classes can be constructed
                return Optional.empty();
            }
            method = modelUtils.concreteConstructorFor(classElement, annotationUtils);
        }

        return createMethodElement(annotationUtils, method);
    }

    @Override
    public Optional<MethodElement> getDefaultConstructor() {
        final AnnotationUtils annotationUtils = visitorContext.getAnnotationUtils();
        final ModelUtils modelUtils = visitorContext.getModelUtils();
        ExecutableElement method = modelUtils.defaultStaticCreatorFor(classElement, annotationUtils);
        if (method == null) {
            if (isInner() && !isStatic()) {
                // only static inner classes can be constructed
                return Optional.empty();
            }
            method = modelUtils.defaultConstructorFor(classElement);
        }
        return createMethodElement(annotationUtils, method);
    }

    @Override
    public Optional<ClassElement> getEnclosingType() {
        if (isInner()) {
            Element enclosingElement = this.classElement.getEnclosingElement();
            if (enclosingElement instanceof TypeElement) {
                return Optional.of(visitorContext.getElementFactory().newClassElement(
                        ((TypeElement) enclosingElement),
                        visitorContext.getAnnotationUtils().getAnnotationMetadata(enclosingElement)
                ));
            }
        }
        return Optional.empty();
    }

    private Optional<MethodElement> createMethodElement(AnnotationUtils annotationUtils, ExecutableElement method) {
        return Optional.ofNullable(method).map(executableElement -> {
            final AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(executableElement);
            if (executableElement.getKind() == ElementKind.CONSTRUCTOR) {
                return new JavaConstructorElement(this, executableElement, annotationMetadata, visitorContext);
            } else {
                return new JavaMethodElement(this, executableElement, annotationMetadata, visitorContext);
            }
        });
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
        return visitorContext.getElementFactory().newClassElement(classElement, visitorContext.getAnnotationUtils().getAnnotationMetadata(classElement))
                .withArrayDimensions(getArrayDimensions());
    }

    private static TypeMirror toTypeMirror(JavaVisitorContext visitorContext, ClassElement element) {
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
                return visitorContext.getTypes().getDeclaredType(
                        ((JavaClassElement) visitorContext.getClassElement(element.getName()).get()).classElement,
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
        if (typeMirrors.equals(this.typeArguments)) {
            return this;
        }

        Map<String, TypeMirror> boundByName = new LinkedHashMap<>();
        Iterator<? extends TypeParameterElement> tpes = classElement.getTypeParameters().iterator();
        Iterator<TypeMirror> args = typeMirrors.iterator();
        while (tpes.hasNext() && args.hasNext()) {
            boundByName.put(tpes.next().getSimpleName().toString(), args.next());
        }

        Map<String, Map<String, TypeMirror>> genericsInfo = visitorContext.getGenericUtils().buildGenericTypeArgumentElementInfo(classElement, null, boundByName);
        return new JavaClassElement(classElement, getAnnotationMetadata(), visitorContext, typeMirrors, genericsInfo, arrayDimensions);
    }

    @Override
    public ClassElement foldBoundGenericTypes(@NonNull Function<ClassElement, ClassElement> fold) {
        List<ClassElement> typeArgs = getBoundGenericTypes().stream().map(arg -> arg.foldBoundGenericTypes(fold)).collect(Collectors.toList());
        if (typeArgs.contains(null)) {
            typeArgs = Collections.emptyList();
        }
        return fold.apply(withBoundGenericTypes(typeArgs));
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

    /**
     * Internal holder class for getters and setters.
     */
    private static class BeanPropertyData {
        ClassElement type;
        ClassElement declaringType;
        ExecutableElement getter;
        ExecutableElement setter;
        final String propertyName;

        public BeanPropertyData(String propertyName) {
            this.propertyName = propertyName;
        }
    }
}
