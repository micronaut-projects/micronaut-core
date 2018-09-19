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

import io.micronaut.annotation.processing.SuperclassAwareTypeVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.visitor.ClassElement;
import io.micronaut.inject.visitor.Element;
import io.micronaut.inject.visitor.VisitorContext;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
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

    @Override
    public List<Element> getElements(VisitorContext visitorContext) {
        List<Element> elements = new ArrayList<>();
        JavaVisitorContext ctx = (JavaVisitorContext) visitorContext;

        classElement.asType().accept(new SuperclassAwareTypeVisitor<Object, Object>() {
            @Override
            protected boolean isAcceptable(javax.lang.model.element.Element element) {
                return true;
            }

            @Override
            protected void accept(DeclaredType type, javax.lang.model.element.Element element, Object o) {
                AnnotationMetadata metadata = ctx.getAnnotationUtils().getAnnotationMetadata(element);
                if (element.getKind() == ElementKind.FIELD) {
                    elements.add(new JavaFieldElement((VariableElement) element, metadata));
                }
                if (element.getKind() == ElementKind.METHOD) {
                    elements.add(new JavaMethodElement((ExecutableElement) element, metadata, ctx));
                }
            }
        }, null);

        return elements;
    }
}
