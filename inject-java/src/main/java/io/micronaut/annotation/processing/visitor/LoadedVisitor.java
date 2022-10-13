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

import io.micronaut.annotation.processing.GenericUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Used to store a reference to an underlying {@link TypeElementVisitor} and
 * optionally invoke the visit methods on the visitor if it matches the
 * element being visited by the annotation processor.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class LoadedVisitor implements Ordered {

    private static final String OBJECT_CLASS = Object.class.getName();

    private final TypeElementVisitor visitor;
    private final String classAnnotation;
    private final String elementAnnotation;

    /**
     * @param visitor               The {@link TypeElementVisitor}
     * @param genericUtils          The generic utils
     * @param processingEnvironment The {@link ProcessingEnvironment}
     */
    public LoadedVisitor(TypeElementVisitor visitor,
                         GenericUtils genericUtils,
                         ProcessingEnvironment processingEnvironment) {
        this.visitor = visitor;
        Class<? extends TypeElementVisitor> aClass = visitor.getClass();

        TypeElement typeElement = processingEnvironment.getElementUtils().getTypeElement(aClass.getName());
        if (typeElement != null) {
            List<? extends TypeMirror> generics = genericUtils.interfaceGenericTypesFor(typeElement, TypeElementVisitor.class.getName());
            String typeName = generics.get(0).toString();
            if (typeName.equals(OBJECT_CLASS)) {
                classAnnotation = visitor.getClassType();
            } else {
                classAnnotation = typeName;
            }
            String elementName = generics.get(1).toString();
            if (elementName.equals(OBJECT_CLASS)) {
                elementAnnotation = visitor.getElementType();
            } else {
                elementAnnotation = elementName;
            }
        } else {
            Class[] classes = GenericTypeUtils.resolveInterfaceTypeArguments(aClass, TypeElementVisitor.class);
            if (classes != null && classes.length == 2) {
                Class classGeneric = classes[0];
                if (classGeneric == Object.class) {
                    classAnnotation = visitor.getClassType();
                } else {
                    classAnnotation = classGeneric.getName();
                }
                Class elementGeneric = classes[1];
                if (elementGeneric == Object.class) {
                    elementAnnotation = visitor.getElementType();
                } else {
                    elementAnnotation = elementGeneric.getName();
                }
            } else {
                classAnnotation = Object.class.getName();
                elementAnnotation = Object.class.getName();
            }
        }
    }

    @Override
    public int getOrder() {
        return visitor.getOrder();
    }

    /**
     * @return The visitor
     */
    public TypeElementVisitor getVisitor() {
        return visitor;
    }

    /**
     * @param annotationMetadata The annotation data
     * @return True if the class element should be visited
     */
    public boolean matchesClass(AnnotationMetadata annotationMetadata) {
        if (classAnnotation.equals("java.lang.Object")) {
            return true;
        }
        return annotationMetadata.hasStereotype(classAnnotation);
    }

    /**
     * @param annotationMetadata The annotation data
     * @return True if the element should be visited
     */
    public boolean matchesElement(AnnotationMetadata annotationMetadata) {
        if (elementAnnotation.equals("java.lang.Object")) {
            return true;
        }
        return annotationMetadata.hasStereotype(elementAnnotation);
    }

    @Override
    public String toString() {
        return visitor.toString();
    }
}
