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

import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.annotation.processing.ModelUtils;
import io.micronaut.annotation.processing.PublicMethodVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.processing.JavaModelUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.lang.model.element.*;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
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
public class JavaClassElement extends AbstractJavaElement implements ClassElement {

    protected final TypeElement classElement;
    protected int arrayDimensions;
    private final JavaVisitorContext visitorContext;
    private Map<String, Map<String, TypeMirror>> genericTypeInfo;

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     */
    @Internal
    public JavaClassElement(TypeElement classElement, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        this(classElement, annotationMetadata, visitorContext, null, 0);
    }

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     * @param genericsInfo       The generic type info
     */
    JavaClassElement(
            TypeElement classElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext,
            Map<String, Map<String, TypeMirror>> genericsInfo) {
        this(classElement, annotationMetadata, visitorContext, genericsInfo, 0);
    }

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     * @param genericsInfo       The generic type info
     * @param arrayDimensions    The number of array dimensions
     */
    JavaClassElement(
            TypeElement classElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext,
            Map<String, Map<String, TypeMirror>> genericsInfo,
            int arrayDimensions) {
        super(classElement, annotationMetadata, visitorContext);
        this.classElement = classElement;
        this.visitorContext = visitorContext;
        this.genericTypeInfo = genericsInfo;
        this.arrayDimensions = arrayDimensions;
    }

    @NonNull
    @Override
    public Map<String, ClassElement> getTypeArguments(@NonNull String type) {
        if (StringUtils.isNotEmpty(type)) {
            Map<String, Map<String, Object>> data = visitorContext.getGenericUtils().buildGenericTypeArgumentInfo(classElement);
            Map<String, Object> forType = data.get(type);
            if (forType != null) {
                Map<String, ClassElement> typeArgs = new LinkedHashMap<>(forType.size());
                for (Map.Entry<String, Object> entry : forType.entrySet()) {
                    Object v = entry.getValue();
                    ClassElement ce = v != null ? visitorContext.getClassElement(v.toString()).orElse(null) : null;
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
                                    visitorContext.getGenericUtils().buildGenericTypeArgumentElementInfo(classElement)));
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
        Map<String, BeanPropertyData> props = new LinkedHashMap<>();
        Map<String, VariableElement> fields = new LinkedHashMap<>();

        classElement.asType().accept(new PublicMethodVisitor<Object, Object>(visitorContext.getTypes()) {

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
                            getterReturnType = mirrorToClassElement(returnType, visitorContext, JavaClassElement.this.genericTypeInfo);
                        }
                    } else {
                        getterReturnType = mirrorToClassElement(returnType, visitorContext, JavaClassElement.this.genericTypeInfo);
                    }

                    BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
                    configureDeclaringType(declaringTypeElement, beanPropertyData);
                    beanPropertyData.type = getterReturnType;
                    beanPropertyData.getter = executableElement;
                    if (beanPropertyData.setter != null) {
                        TypeMirror typeMirror = beanPropertyData.setter.getParameters().get(0).asType();
                        ClassElement setterParameterType = mirrorToClassElement(typeMirror, visitorContext, JavaClassElement.this.genericTypeInfo);
                        if (!setterParameterType.getName().equals(getterReturnType.getName())) {
                            beanPropertyData.setter = null; // not a compatible setter
                        }
                    }
                } else if (NameUtils.isSetterName(methodName) && executableElement.getParameters().size() == 1) {
                    String propertyName = NameUtils.getPropertyNameForSetter(methodName);
                    TypeMirror typeMirror = executableElement.getParameters().get(0).asType();
                    ClassElement setterParameterType = mirrorToClassElement(typeMirror, visitorContext, JavaClassElement.this.genericTypeInfo);

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
                            genericTypeInfo
                    );
                }
            }
        }, null);

        if (!props.isEmpty()) {
            List<PropertyElement> propertyElements = new ArrayList<>();
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
                    propertyElements.add(propertyElement);
                }
            }
            return Collections.unmodifiableList(propertyElements);
        } else {
            return Collections.emptyList();
        }

    }

    @Override
    public List<FieldElement> getFields(@NonNull Predicate<Set<ElementModifier>> modifierFilter) {
        List<FieldElement> fields = new ArrayList<>();
        classElement.asType().accept(new PublicMethodVisitor<Object, Object>(visitorContext.getTypes()) {
            @Override
            protected boolean isAcceptable(javax.lang.model.element.Element element) {
                final Set<ElementModifier> mods = element.getModifiers().stream().map(m -> ElementModifier.valueOf(m.name())).collect(Collectors.toSet());
                return element.getKind() == ElementKind.FIELD && element instanceof VariableElement && modifierFilter.test(mods);
            }

            @Override
            protected void accept(DeclaredType type, Element element, Object o) {
                final AnnotationMetadata fieldMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(element);
                fields.add(new JavaFieldElement(JavaClassElement.this, (VariableElement) element, fieldMetadata, visitorContext));
            }

        }, null);

        return Collections.unmodifiableList(fields);
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
    public ClassElement toArray() {
        return new JavaClassElement(classElement, getAnnotationMetadata(), visitorContext, getGenericTypeInfo(), arrayDimensions + 1);
    }

    @Override
    public String getName() {
        return JavaModelUtils.getClassName(classElement);
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

    @NonNull
    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        final AnnotationUtils annotationUtils = visitorContext.getAnnotationUtils();
        final ModelUtils modelUtils = visitorContext.getModelUtils();
        ExecutableElement method = modelUtils.staticCreatorFor(classElement, annotationUtils);
        if (method == null) {
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
            method = modelUtils.defaultConstructorFor(classElement);
        }
        return createMethodElement(annotationUtils, method);
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

    @Override
    public @NonNull
    Map<String, ClassElement> getTypeArguments() {
        List<? extends TypeParameterElement> typeParameters = classElement.getTypeParameters();
        Iterator<? extends TypeParameterElement> tpi = typeParameters.iterator();

        Map<String, ClassElement> map = new LinkedHashMap<>();
        while (tpi.hasNext()) {
            TypeParameterElement tpe = tpi.next();
            ClassElement classElement = mirrorToClassElement(tpe.asType(), visitorContext, this.genericTypeInfo);
            map.put(tpe.toString(), classElement);
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * @return The generic type info for this class.
     */
    Map<String, Map<String, TypeMirror>> getGenericTypeInfo() {
        if (genericTypeInfo == null) {
            genericTypeInfo = visitorContext.getGenericUtils().buildGenericTypeArgumentElementInfo(classElement);
        }
        return genericTypeInfo;
    }

    /**
     * Internal holder class for getters and setters.
     */
    private class BeanPropertyData {
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
