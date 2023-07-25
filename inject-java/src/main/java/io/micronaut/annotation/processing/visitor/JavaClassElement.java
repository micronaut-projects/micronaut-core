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
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
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
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
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
    private final boolean isTypeVariable;
    private List<PropertyElement> beanProperties;
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
    @Nullable
    private ElementAnnotationMetadata elementTypeAnnotationMetadata;
    @Nullable
    private ClassElement theType;
    @Nullable
    private AnnotationMetadata annotationMetadata;
    @Nullable
    // Not null means raw type definition: "List myMethod()"
    // Null value means a class definition: "class List<T> {}"
    final List<? extends TypeMirror> typeArguments;

    /**
     * @param nativeType                The native type
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    @Internal
    public JavaClassElement(JavaNativeElement.Class nativeType, ElementAnnotationMetadataFactory annotationMetadataFactory, JavaVisitorContext visitorContext) {
        this(nativeType, annotationMetadataFactory, visitorContext, null, null, 0, false);
    }

    /**
     * @param nativeType                The native type
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     * @param typeArguments             The declared type arguments
     * @param resolvedTypeArguments     The resolvedTypeArguments
     */
    JavaClassElement(JavaNativeElement.Class nativeType,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext,
                     List<? extends TypeMirror> typeArguments,
                     @Nullable
                     Map<String, ClassElement> resolvedTypeArguments) {
        this(nativeType, annotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, 0, false);
    }

    /**
     * @param nativeType                The native type
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     * @param typeArguments             The declared type arguments
     * @param resolvedTypeArguments     The resolvedTypeArguments
     * @param arrayDimensions           The number of array dimensions
     */
    JavaClassElement(JavaNativeElement.Class nativeType,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext,
                     List<? extends TypeMirror> typeArguments,
                     @Nullable
                     Map<String, ClassElement> resolvedTypeArguments,
                     int arrayDimensions) {
        this(nativeType, annotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, arrayDimensions, false);
    }

    /**
     * @param nativeType                The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     * @param typeArguments             The declared type arguments
     * @param resolvedTypeArguments     The resolvedTypeArguments
     * @param arrayDimensions           The number of array dimensions
     * @param isTypeVariable            Is the type a type variable
     */
    JavaClassElement(JavaNativeElement.Class nativeType,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext,
                     List<? extends TypeMirror> typeArguments,
                     @Nullable
                     Map<String, ClassElement> resolvedTypeArguments,
                     int arrayDimensions,
                     boolean isTypeVariable) {
        super(nativeType, annotationMetadataFactory, visitorContext);
        this.classElement = nativeType.element();
        this.typeArguments = typeArguments;
        this.resolvedTypeArguments = resolvedTypeArguments;
        this.arrayDimensions = arrayDimensions;
        this.isTypeVariable = isTypeVariable;
    }

    @Override
    public JavaNativeElement.Class getNativeType() {
        return (JavaNativeElement.Class) super.getNativeType();
    }

    @Override
    protected JavaClassElement copyThis() {
        return new JavaClassElement(getNativeType(), elementAnnotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, arrayDimensions);
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> newTypeArguments) {
        return new JavaClassElement(getNativeType(), elementAnnotationMetadataFactory, visitorContext, typeArguments, newTypeArguments, arrayDimensions);
    }

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ClassElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        if (getNativeType().typeMirror() == null) {
            return super.getAnnotationMetadataToWrite();
        }
        return getTypeAnnotationMetadata();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (annotationMetadata == null) {
            if (getNativeType().typeMirror() == null) {
                annotationMetadata = super.getAnnotationMetadata();
            } else {
                annotationMetadata = new AnnotationMetadataHierarchy(true, super.getAnnotationMetadata(), getTypeAnnotationMetadata());
            }
        }
        return annotationMetadata;
    }

    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        if (elementTypeAnnotationMetadata == null) {
            elementTypeAnnotationMetadata = elementAnnotationMetadataFactory.buildTypeAnnotations(this);
        }
        return elementTypeAnnotationMetadata;
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

    @Override
    public boolean isPrimitive() {
        return ClassUtils.getPrimitiveType(getName()).isPresent();
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        return classElement.getInterfaces().stream().map(mirror -> newClassElement(mirror, getTypeArguments())).toList();
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
                resolvedSuperType = newClassElement(superclass, getTypeArguments());
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
        if (isKotlinClass(getNativeType().element())) {
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
        classElement.asType().accept(new SuperclassAwareTypeVisitor<>(visitorContext) {

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
                if (element instanceof ExecutableElement executableElement) {
                    if (recordComponents.contains(name)) {
                        methodElements.add(
                            new JavaMethodElement(
                                JavaClassElement.this,
                                new JavaNativeElement.Method(executableElement),
                                elementAnnotationMetadataFactory,
                                visitorContext)
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
        classElement.asType().accept(new SuperclassAwareTypeVisitor<>(visitorContext) {

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
                        if (enclosedElement instanceof VariableElement) {
                            accept(type, enclosedElement, o);
                        }
                    }
                }
                return o;
            }

            @Override
            protected void accept(DeclaredType type, Element element, Object o) {
                if (element instanceof VariableElement variableElement) {
                    fieldElements.add(
                        new JavaFieldElement(
                            JavaClassElement.this,
                            new JavaNativeElement.Variable(variableElement),
                            elementAnnotationMetadataFactory,
                            visitorContext)
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
        return enclosedElementsQuery.getEnclosedElements(this, query);
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
        return new JavaClassElement(getNativeType(), elementAnnotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, arrayDimensions, false);
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
        if (type instanceof JavaClassElement javaClassElement) {
            return isAssignable(javaClassElement.getNativeType().element());
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
            constructorSearch:
            for (ConstructorElement constructor : constructors) {
                ParameterElement[] parameters = constructor.getParameters();
                if (parameters.length == recordComponents.size()) {
                    for (int i = 0; i < parameters.length; i++) {
                        ParameterElement parameter = parameters[i];
                        RecordComponentElement rce = recordComponents.get(i);
                        VariableElement ve = ((JavaNativeElement.Variable) parameter.getNativeType()).element();
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
                .annotated(am -> am.hasStereotype(Creator.class))).stream().findFirst()
            )
            .filter(method -> !method.isPrivate() && method.getReturnType().equals(this))
            .stream().toList();
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
        if (typeArguments == null) {
            return Collections.emptyList();
        }
        return typeArguments.stream()
            .map(tm -> newClassElement(tm, getTypeArguments()))
            .toList();
    }

    @NonNull
    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        return classElement.getTypeParameters().stream()
            // we want the *declared* variables, so we don't pass in our genericsInfo.
            .map(tpe -> (GenericPlaceholderElement) newClassElement(tpe.asType(), Collections.emptyMap()))
            .toList();
    }

    @NonNull
    @Override
    public ClassElement getRawClassElement() {
        return visitorContext.getElementFactory().newClassElement(classElement, elementAnnotationMetadataFactory)
            .withArrayDimensions(getArrayDimensions());
    }

    @NonNull
    @Override
    public ClassElement withTypeArguments(@NonNull Collection<ClassElement> typeArguments) {
        if (getTypeArguments().equals(typeArguments)) {
            return this;
        }
        Map<String, ClassElement> boundByName = new LinkedHashMap<>();
        Iterator<? extends TypeParameterElement> tpes = classElement.getTypeParameters().iterator();
        Iterator<? extends ClassElement> args = typeArguments.iterator();
        while (tpes.hasNext() && args.hasNext()) {
            ClassElement next = args.next();
            Object nativeType = next.getNativeType();
            if (nativeType instanceof Class<?> aClass) {
                next = visitorContext.getClassElement(aClass).orElse(next);
            }
            boundByName.put(tpes.next().getSimpleName().toString(), next);
        }
        return withTypeArguments(boundByName);
    }

    @Override
    @NonNull
    public Map<String, ClassElement> getTypeArguments() {
        if (resolvedTypeArguments == null) {
            resolvedTypeArguments = resolveTypeArguments(classElement, typeArguments);
        }
        return resolvedTypeArguments;
    }

    @NonNull
    @Override
    public Map<String, Map<String, ClassElement>> getAllTypeArguments() {
        if (resolvedAllTypeArguments == null) {
            resolvedAllTypeArguments = ArrayableClassElement.super.getAllTypeArguments();
        }
        return resolvedAllTypeArguments;
    }

    @Override
    public ClassElement getType() {
        if (theType == null) {
            if (getNativeType().typeMirror() == null) {
                theType = this;
            } else {
                // Strip the type mirror
                // This should eliminate type annotations
                theType = new JavaClassElement(new JavaNativeElement.Class(getNativeType().element()), elementAnnotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, arrayDimensions);
            }
        }
        return theType;
    }

    private final class JavaEnclosedElementsQuery extends EnclosedElementsQuery<TypeElement, Element> {

        private List<? extends Element> enclosedElements;

        @Override
        protected TypeElement getNativeClassType(ClassElement classElement) {
            return ((JavaClassElement) classElement).getNativeType().element();
        }

        @Override
        protected Element getNativeType(io.micronaut.inject.ast.Element element) {
            return ((AbstractJavaElement) element).getNativeType().element();
        }

        @Override
        protected String getElementName(Element element) {
            return element.getSimpleName().toString();
        }

        @Override
        protected Set<Element> getExcludedNativeElements(ElementQuery.Result<?> result) {
            if (result.isExcludePropertyElements()) {
                Set<Element> excludeElements = new HashSet<>();
                for (PropertyElement excludePropertyElement : getBeanProperties()) {
                    excludePropertyElement.getReadMethod().ifPresent(methodElement -> excludeElements.add(((JavaNativeElement) methodElement.getNativeType()).element()));
                    excludePropertyElement.getWriteMethod().ifPresent(methodElement -> excludeElements.add(((JavaNativeElement) methodElement.getNativeType()).element()));
                    excludePropertyElement.getField().ifPresent(methodElement -> excludeElements.add(((JavaNativeElement) methodElement.getNativeType()).element()));
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
        protected io.micronaut.inject.ast.Element toAstElement(Element nativeType, Class<?> elementType) {
            final JavaElementFactory elementFactory = visitorContext.getElementFactory();
            return switch (nativeType.getKind()) {
                case METHOD -> elementFactory.newMethodElement(
                    JavaClassElement.this,
                    (ExecutableElement) nativeType,
                    elementAnnotationMetadataFactory
                );
                case FIELD -> elementFactory.newFieldElement(
                    JavaClassElement.this,
                    (VariableElement) nativeType,
                    elementAnnotationMetadataFactory
                );
                case ENUM_CONSTANT -> elementFactory.newEnumConstantElement(
                    JavaClassElement.this,
                    (VariableElement) nativeType,
                    elementAnnotationMetadataFactory
                );
                case CONSTRUCTOR -> elementFactory.newConstructorElement(
                    JavaClassElement.this,
                    (ExecutableElement) nativeType,
                    elementAnnotationMetadataFactory
                );
                case CLASS, ENUM -> elementFactory.newClassElement(
                    (TypeElement) nativeType,
                    elementAnnotationMetadataFactory
                );
                default -> throw new IllegalStateException("Unknown element: " + nativeType);
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
