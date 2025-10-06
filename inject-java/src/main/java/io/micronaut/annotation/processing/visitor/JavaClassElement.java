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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.NullMarked;
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
import io.micronaut.inject.ast.UnresolvedTypeKind;
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
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
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
public class JavaClassElement extends AbstractTypeAwareJavaElement implements ArrayableClassElement {
    private static final String KOTLIN_METADATA = "kotlin.Metadata";
    private static final String PREFIX_IS = "is";
    protected final TypeElement classElement;
    protected final int arrayDimensions;
    @Nullable
    protected String doc;
    @Nullable
    // Not null means raw type definition: "List myMethod()"
    // Null value means a class definition: "class List<T> {}"
    final List<? extends TypeMirror> typeArguments;

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
    @Nullable
    private List<ClassElement> resolvedInterfaces;
    private boolean hasErrorousInterface;
    private final JavaEnclosedElementsQuery enclosedElementsQuery = new JavaEnclosedElementsQuery(false);
    private final JavaEnclosedElementsQuery sourceEnclosedElementsQuery = new JavaEnclosedElementsQuery(true);
    @Nullable
    private ElementAnnotationMetadata elementTypeAnnotationMetadata;
    @Nullable
    private ClassElement theType;
    @Nullable
    private AnnotationMetadata annotationMetadata;

    /**
     * @param nativeType The native type
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     */
    @Internal
    public JavaClassElement(JavaNativeElement.Class nativeType, ElementAnnotationMetadataFactory annotationMetadataFactory, JavaVisitorContext visitorContext) {
        this(nativeType, annotationMetadataFactory, visitorContext, null, null, 0, false, null);
    }

    /**
     * @param nativeType The native type
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     * @param typeArguments The declared type arguments
     * @param resolvedTypeArguments The resolvedTypeArguments
     */
    JavaClassElement(JavaNativeElement.Class nativeType,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext,
                     List<? extends TypeMirror> typeArguments,
                     @Nullable
                     Map<String, ClassElement> resolvedTypeArguments) {
        this(nativeType, annotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, 0, false, null);
    }

    /**
     * @param nativeType The native type
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     * @param typeArguments The declared type arguments
     * @param resolvedTypeArguments The resolvedTypeArguments
     * @param doc The optional documentation
     */
    JavaClassElement(JavaNativeElement.Class nativeType,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext,
                     List<? extends TypeMirror> typeArguments,
                     @Nullable
                     Map<String, ClassElement> resolvedTypeArguments,
                     String doc) {
        this(nativeType, annotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, 0, false, doc);
    }

    /**
     * @param nativeType The native type
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     * @param typeArguments The declared type arguments
     * @param resolvedTypeArguments The resolvedTypeArguments
     * @param arrayDimensions The number of array dimensions
     */
    JavaClassElement(JavaNativeElement.Class nativeType,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext,
                     List<? extends TypeMirror> typeArguments,
                     @Nullable
                     Map<String, ClassElement> resolvedTypeArguments,
                     int arrayDimensions) {
        this(nativeType, annotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, arrayDimensions, false, null);
    }

    /**
     * @param nativeType The native type
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     * @param typeArguments The declared type arguments
     * @param resolvedTypeArguments The resolvedTypeArguments
     * @param arrayDimensions The number of array dimensions
     * @param doc The optional documentation
     */
    JavaClassElement(JavaNativeElement.Class nativeType,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext,
                     List<? extends TypeMirror> typeArguments,
                     @Nullable
                     Map<String, ClassElement> resolvedTypeArguments,
                     int arrayDimensions,
                     String doc) {
        this(nativeType, annotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, arrayDimensions, false, doc);
    }

    /**
     * @param nativeType The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     * @param typeArguments The declared type arguments
     * @param resolvedTypeArguments The resolvedTypeArguments
     * @param arrayDimensions The number of array dimensions
     * @param isTypeVariable Is the type a type variable
     * @param doc            The optional documentation
     */
    JavaClassElement(JavaNativeElement.Class nativeType,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext,
                     @Nullable List<? extends TypeMirror> typeArguments,
                     @Nullable
                     Map<String, ClassElement> resolvedTypeArguments,
                     int arrayDimensions,
                     boolean isTypeVariable,
                     @Nullable
                     String doc) {
        super(nativeType, annotationMetadataFactory, visitorContext);
        this.classElement = nativeType.element();
        this.typeArguments = typeArguments;
        this.resolvedTypeArguments = resolvedTypeArguments;
        this.arrayDimensions = arrayDimensions;
        this.isTypeVariable = isTypeVariable;
        this.doc = doc;
    }

    @Override
    public Optional<String> getDocumentation(boolean parse) {
        return doc == null ? super.getDocumentation(parse) : Optional.of(doc);
    }

    @Override
    public String getCanonicalName() {
        return classElement.getQualifiedName().toString();
    }

    @Override
    public boolean hasUnresolvedTypes(UnresolvedTypeKind... kind) {
        List<? extends TypeMirror> interfaces = this.classElement.getInterfaces();
        for (UnresolvedTypeKind unresolvedTypeKind : kind) {
            switch (unresolvedTypeKind) {
                case INTERFACE -> {
                    for (TypeMirror anInterface : interfaces) {
                        if (anInterface.getKind() == TypeKind.ERROR) {
                            return true;
                        }
                    }
                }
                case SUPERCLASS -> {
                    TypeMirror superclass = this.classElement.getSuperclass();
                    if (superclass.getKind() == TypeKind.ERROR) {
                        return true;
                    }
                }
                default -> {
                    // no-op
                }
            }
        }
        return false;
    }

    @Override
    public JavaNativeElement.@NonNull Class getNativeType() {
        return (JavaNativeElement.Class) super.getNativeType();
    }

    @Override
    protected JavaClassElement copyThis() {
        return new JavaClassElement(getNativeType(), elementAnnotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, arrayDimensions);
    }

    @NonNull
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

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (presetAnnotationMetadata != null) {
            return presetAnnotationMetadata;
        }
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
    protected boolean hasNullMarked() {
        return getPackage().hasStereotype(NullMarked.class) || hasStereotype(NullMarked.class);
    }

    @NonNull
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
        if (resolvedInterfaces == null) {
            if (!visitorContext.isVisitUnresolvedInterfaces()) {
                resolvedInterfaces = classElement.getInterfaces().stream()
                    .filter(this::onlyAvailable)
                    .map(mirror -> newClassElement(mirror, getTypeArguments())).toList();
                hasErrorousInterface = classElement.getInterfaces().size() != resolvedInterfaces.size();
            } else {
                resolvedInterfaces = classElement.getInterfaces().stream()
                    .map(mirror -> newClassElement(mirror, getTypeArguments())).toList();
                hasErrorousInterface = false;
            }
        } else if (hasErrorousInterface && visitorContext.isVisitUnresolvedInterfaces()) {
            resolvedInterfaces = classElement.getInterfaces().stream()
                .map(mirror -> newClassElement(mirror, getTypeArguments())).toList();
            hasErrorousInterface = false;
        }
        return resolvedInterfaces;
    }

    private boolean onlyAvailable(TypeMirror mirror) {
        return !(mirror instanceof DeclaredType declaredType) || declaredType.getKind() != TypeKind.ERROR;
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
    public @NonNull List<PropertyElement> getBeanProperties() {
        if (beanProperties == null) {
            beanProperties = getBeanProperties(PropertyElementQuery.of(this));
        }
        return Collections.unmodifiableList(beanProperties);
    }

    @Override
    public @NonNull List<PropertyElement> getBeanProperties(@NonNull PropertyElementQuery propertyElementQuery) {
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
            visitorContext,
            findPropertyDoc(value));
    }

    @Nullable
    private String findPropertyDoc(AstBeanPropertiesUtils.BeanPropertyData value) {
        if (isRecord()) {
             try {
                 String docComment = visitorContext.getElements().getDocComment(getNativeType().element());
                 if (docComment != null) {
                     Javadoc javadoc = StaticJavaParser.parseJavadoc(docComment);
                     for (JavadocBlockTag t : javadoc.getBlockTags()) {
                         if (t.getType() == JavadocBlockTag.Type.PARAM && t.getName().map(n -> n.equals(value.propertyName)).orElse(false)) {
                             return t.getContent().toText();
                         }
                     }
                 }
             } catch (Exception ignore) {
                 // Ignore
             }
        }
        return null;
    }

    private List<MethodElement> getRecordMethods() {
        var recordComponents = new HashSet<String>();
        var methodElements = new ArrayList<MethodElement>();
        for (Element enclosedElement : classElement.getEnclosedElements()) {
            if (JavaModelUtils.isRecordComponent(enclosedElement) || enclosedElement instanceof ExecutableElement) {
                if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
                    continue;
                }
                String name = enclosedElement.getSimpleName().toString();
                if (enclosedElement instanceof ExecutableElement executableElement) {
                    if (recordComponents.contains(name)) {
                        methodElements.add(
                            new JavaMethodElement(
                                JavaClassElement.this,
                                new JavaNativeElement.Method(executableElement),
                                elementAnnotationMetadataFactory,
                                visitorContext)
                        );
                    }
                } else if (enclosedElement instanceof VariableElement) {
                    recordComponents.add(name);
                }
            }
        }
        return methodElements;
    }

    private List<FieldElement> getRecordFields() {
        var fieldElements = new ArrayList<FieldElement>();
        for (Element enclosedElement : classElement.getEnclosedElements()) {
            if (!JavaModelUtils.isRecordComponent(enclosedElement) && enclosedElement instanceof VariableElement variableElement) {
                fieldElements.add(
                    new JavaFieldElement(
                        JavaClassElement.this,
                        new JavaNativeElement.Variable(variableElement),
                        elementAnnotationMetadataFactory,
                        visitorContext)
                );
            }
        }
        return fieldElements;
    }

    private boolean isKotlinClass(Element element) {
        return element.getAnnotationMirrors().stream().anyMatch(am -> am.getAnnotationType().asElement().toString().equals(KOTLIN_METADATA));
    }

    @NonNull
    @Override
    public <T extends io.micronaut.inject.ast.Element> List<T> getEnclosedElements(@NonNull ElementQuery<T> query) {
        return enclosedElementsQuery.getEnclosedElements(this, query);
    }

    /**
     * This method will produce the elements just like {@link #getEnclosedElements(ElementQuery)}
     * but the elements are constructed as the source ones.
     * {@link io.micronaut.inject.ast.ElementFactory#newSourceMethodElement(ClassElement, Object, ElementAnnotationMetadataFactory)}.
     *
     * @param query The query
     * @param <T> The element type
     * @return The list of elements
     */
    public final <T extends io.micronaut.inject.ast.Element> List<T> getSourceEnclosedElements(@NonNull ElementQuery<T> query) {
        return sourceEnclosedElementsQuery.getEnclosedElements(this, query);
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
        JavaNativeElement.Class nativeType = getNativeType();
        if (this.arrayDimensions - 1 == arrayDimensions  && nativeType.typeMirror() instanceof ArrayType array) {
            nativeType = new JavaNativeElement.Class(nativeType.element(), array.getComponentType(), nativeType.owner());
        }
        return new JavaClassElement(nativeType, elementAnnotationMetadataFactory, visitorContext, typeArguments, resolvedTypeArguments, arrayDimensions, false, doc);
    }

    @Override
    public @NonNull String getSimpleName() {
        if (simpleName == null) {
            simpleName = JavaModelUtils.getClassNameWithoutPackage(classElement);
        }
        return simpleName;
    }

    @Override
    public @NonNull String getName() {
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
        if (getName().equals(type)) {
            return true; // Same type
        }
        TypeElement otherElement = visitorContext.getElements().getTypeElement(type);
        if (otherElement != null) {
            return isAssignable(otherElement);
        }
        return false;
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        if (equals(type)) {
            return true; // Same type
        }
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
                        String parameterTypeString = parameter.getType().getName();
                        TypeMirror recordType = rce.asType();
                        Element element = visitorContext.getTypes().asElement(recordType);
                        String recordTypeString = element == null ? recordType.toString() : element.toString();
                        if (!parameterTypeString.equals(recordTypeString)) {
                            // types don't match, continue searching constructors
                            continue constructorSearch;
                        }
                    }
                    return Optional.of(constructor);
                }
            }
            if (constructors.isEmpty()) {
                // constructor not accessible
                return Optional.empty();
            } else {
                return Optional.of(constructors.get(constructors.size() - 1));
            }
        }
        return ArrayableClassElement.super.getPrimaryConstructor();
    }

    @Override
    public @NonNull List<MethodElement> getAccessibleStaticCreators() {
        var staticCreators = new ArrayList<>(ArrayableClassElement.super.getAccessibleStaticCreators());
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
        var boundByName = new LinkedHashMap<String, ClassElement>();
        Iterator<? extends TypeParameterElement> types = classElement.getTypeParameters().iterator();
        Iterator<? extends ClassElement> args = typeArguments.iterator();
        while (types.hasNext() && args.hasNext()) {
            ClassElement next = args.next();
            Object nativeType = next.getNativeType();
            if (nativeType instanceof Class<?> aClass) {
                next = visitorContext.getClassElement(aClass).orElse(next);
            }
            boundByName.put(types.next().getSimpleName().toString(), next);
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
    public @NonNull ClassElement getType() {
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

        private final boolean isSource;
        private List<? extends Element> enclosedElements;

        private JavaEnclosedElementsQuery(boolean isSource) {
            this.isSource = isSource;
        }

        @Override
        protected boolean hasAnnotation(Element element, Class<? extends Annotation> annotation) {
            return element.getAnnotation(annotation) != null;
        }

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
        protected Set<Element> getExcludedNativeElements(ElementQuery.@NonNull Result<?> result) {
            if (result.isExcludePropertyElements()) {
                var excludeElements = new HashSet<Element>();
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
                if (element instanceof TypeElement typeElement) {
                    return typeElement;
                }
            }
            return null;
        }

        @NonNull
        @Override
        protected Collection<TypeElement> getInterfaces(TypeElement classNode) {
            List<? extends TypeMirror> interfaces = classNode.getInterfaces();
            var result = new ArrayList<TypeElement>(interfaces.size());
            for (TypeMirror ifaceMirror : interfaces) {
                final Element ifaceEl = visitorContext.getTypes().asElement(ifaceMirror);
                if (ifaceEl instanceof TypeElement element) {
                    result.add(element);
                }
            }
            return result;
        }

        @NonNull
        @Override
        protected List<Element> getEnclosedElements(TypeElement classNode, ElementQuery.Result<?> result, boolean includeAbstract) {
            List<? extends Element> ee;
            if (classNode == classElement) {
                ee = getEnclosedElements();
            } else {
                ee = classNode.getEnclosedElements();
            }
            EnumSet<ElementKind> elementKinds = getElementKind(result);
            var list = new ArrayList<Element>(ee.size());
            for (Element element : ee) {
                Set<Modifier> modifiers = element.getModifiers();
                if (elementKinds.contains(element.getKind()) && (includeAbstract || isNonAbstractMethod(modifiers))) {
                    list.add(element);
                }
            }
            return list;
        }

        private boolean isNonAbstractMethod(Set<Modifier> modifiers) {
            if (modifiers.contains(Modifier.DEFAULT)) {
                return true;
            }
            if (modifiers.contains(Modifier.PRIVATE)) {
                return true;
            }
            return !modifiers.contains(Modifier.ABSTRACT);
        }

        @Override
        protected boolean excludeClass(TypeElement classNode) {
            String qualifiedName = classNode.getQualifiedName().toString();
            return qualifiedName.equals(Object.class.getName())
                || qualifiedName.equals(Record.class.getName())
                || qualifiedName.equals(Enum.class.getName());
        }

        @Override
        protected boolean isAbstractClass(TypeElement classNode) {
            return classNode.getModifiers().contains(Modifier.ABSTRACT);
        }

        @Override
        protected boolean isInterface(TypeElement classNode) {
            return classNode.getKind() == ElementKind.INTERFACE;
        }

        @Override
        protected io.micronaut.inject.ast.@NonNull Element toAstElement(Element nativeType, Class<?> elementType) {
            final JavaElementFactory elementFactory = visitorContext.getElementFactory();
            return switch (nativeType.getKind()) {
                case METHOD -> {
                    if (isSource) {
                        yield elementFactory.newSourceMethodElement(
                            JavaClassElement.this,
                            (ExecutableElement) nativeType,
                            elementAnnotationMetadataFactory
                        );
                    } else {
                        yield elementFactory.newMethodElement(
                            JavaClassElement.this,
                            (ExecutableElement) nativeType,
                            elementAnnotationMetadataFactory
                        );
                    }
                }
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
                case CLASS, ENUM, RECORD, INTERFACE, ANNOTATION_TYPE -> {
                    if (isSource) {
                        yield elementFactory.newSourceClassElement(
                            (TypeElement) nativeType,
                            elementAnnotationMetadataFactory
                        );
                    } else {
                        yield elementFactory.newClassElement(
                            (TypeElement) nativeType,
                            elementAnnotationMetadataFactory
                        );
                    }
                }
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
                return EnumSet.of(ElementKind.CLASS, ElementKind.ENUM, ElementKind.RECORD, ElementKind.INTERFACE, ElementKind.ANNOTATION_TYPE);
            }
            throw new IllegalArgumentException("Unsupported element type for query: " + elementType);
        }

    }

}
