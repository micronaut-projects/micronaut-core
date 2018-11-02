/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.annotation.processing.visitor;

import io.micronaut.annotation.processing.PublicMethodVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

/**
 * A class element returning data from a {@link TypeElement}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class JavaClassElement extends AbstractJavaElement implements ClassElement {

    private final TypeElement classElement;
    private final JavaVisitorContext visitorContext;
    private final List<? extends TypeMirror> typeArguments;

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext The visitor context
     */
    JavaClassElement(TypeElement classElement, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        super(classElement, annotationMetadata);
        this.classElement = classElement;
        this.visitorContext = visitorContext;
        this.typeArguments = Collections.emptyList();
    }

    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext The visitor context
     * @param typeArguments The type arguments
     */
    JavaClassElement(TypeElement classElement, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext, List<? extends TypeMirror> typeArguments) {
        super(classElement, annotationMetadata);
        this.classElement = classElement;
        this.visitorContext = visitorContext;
        this.typeArguments = typeArguments;
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
                    if (modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.STATIC) && !modifiers.contains(Modifier.ABSTRACT)) {
                        ExecutableElement executableElement = (ExecutableElement) element;
                        String methodName = executableElement.getSimpleName().toString();
                        if (methodName.contains("$")) {
                            return false;
                        }

                        if (NameUtils.isGetterName(methodName) && executableElement.getParameters().size() == 0) {
                            return true;
                        } else {
                            return NameUtils.isSetterName(methodName) && executableElement.getParameters().size() == 1;
                        }
                    }
                }
                return false;
            }

            @Override
            protected void accept(DeclaredType type, javax.lang.model.element.Element element, Object o) {

                if (element instanceof VariableElement) {
                    fields.put(element.getSimpleName().toString(), (VariableElement) element);
                    return;
                }

                ExecutableElement executableElement = (ExecutableElement) element;
                String methodName = executableElement.getSimpleName().toString();

                if (NameUtils.isGetterName(methodName) && executableElement.getParameters().size() == 0) {
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
                            getterReturnType = mirrorToClassElement(returnType, visitorContext);
                        }
                    } else {
                        getterReturnType = mirrorToClassElement(returnType, visitorContext);
                    }

                    if (getterReturnType != null) {

                        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
                        beanPropertyData.type = getterReturnType;
                        beanPropertyData.getter = executableElement;
                        if (beanPropertyData.setter != null) {
                            TypeMirror typeMirror = beanPropertyData.setter.getParameters().get(0).asType();
                            ClassElement setterParameterType = mirrorToClassElement(typeMirror, visitorContext);
                            if (setterParameterType == null || !setterParameterType.getName().equals(getterReturnType.getName())) {
                                beanPropertyData.setter = null; // not a compatible setter
                            }
                        }
                    }
                } else if (NameUtils.isSetterName(methodName) && executableElement.getParameters().size() == 1) {
                    String propertyName = NameUtils.getPropertyNameForSetter(methodName);
                    TypeMirror typeMirror = executableElement.getParameters().get(0).asType();
                    ClassElement setterParameterType = mirrorToClassElement(typeMirror, visitorContext);

                    if (setterParameterType != null) {

                        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
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
                        annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(value.getter);
                    }
                    JavaPropertyElement propertyElement = new JavaPropertyElement(value.getter, annotationMetadata, propertyName, value.type, value.setter == null) {
                        @Override
                        public Optional<String> getDocumentation() {
                            Elements elements = visitorContext.getElements();
                            String docComment = elements.getDocComment(value.getter);
                            return Optional.ofNullable(docComment);
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
    public boolean isArray() {
        return classElement.asType().getKind() == TypeKind.ARRAY;
    }

    @Override
    public String getName() {
        return classElement.getQualifiedName().toString();
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
    public Map<String, ClassElement> getTypeArguments() {
        List<? extends TypeParameterElement> typeParameters = classElement.getTypeParameters();
        if (typeParameters.size() == typeArguments.size()) {
            Iterator<? extends TypeParameterElement> tpi = typeParameters.iterator();
            Iterator<? extends TypeMirror> tai = typeArguments.iterator();

            Map<String, ClassElement> map = new LinkedHashMap<>();
            while (tpi.hasNext()) {
                TypeParameterElement tpe = tpi.next();
                TypeMirror typeMirror = tai.next();

                ClassElement classElement = mirrorToClassElement(typeMirror, visitorContext);
                if (classElement != null) {
                    map.put(tpe.toString(), classElement);
                }
            }

            return Collections.unmodifiableMap(map);
        }
        return Collections.emptyMap();
    }

    /**
     * Internal holder class for getters and setters.
     */
    private class BeanPropertyData {
        ClassElement type;
        ExecutableElement getter;
        ExecutableElement setter;
        final String propertyName;

        public BeanPropertyData(String propertyName) {
            this.propertyName = propertyName;
        }
    }
}
