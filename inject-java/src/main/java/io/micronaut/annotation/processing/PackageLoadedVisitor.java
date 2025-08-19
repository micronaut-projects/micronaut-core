/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.visitor.PackageElementVisitor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;

/**
 * Used to store a reference to an underlying {@link PackageLoadedVisitor} and it's annotation.
 *
 * @param visitor The visitor
 * @param annotation The annotation
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
record PackageLoadedVisitor(@NonNull PackageElementVisitor<?> visitor,
                            @NonNull String annotation) implements Ordered {

    private static final String OBJECT_CLASS = Object.class.getName();

    /**
     * @param visitor               The {@link PackageElementVisitor}
     * @param processingEnvironment The {@link ProcessingEnvironment}
     */
    public static PackageLoadedVisitor of(PackageElementVisitor<?> visitor, ProcessingEnvironment processingEnvironment) {
        Class<? extends PackageElementVisitor> aClass = visitor.getClass();

        String annotation;
        TypeElement typeElement = processingEnvironment.getElementUtils().getTypeElement(aClass.getName());
        if (typeElement != null) {
            List<? extends TypeMirror> generics = interfaceGenericTypesFor(typeElement, PackageElementVisitor.class.getName());
            if (generics.size() == 1) {
                String annotationName = generics.get(0).toString();
                if (annotationName.equals(OBJECT_CLASS)) {
                    annotation = visitor.getPackageAnnotationName();
                } else {
                    annotation = annotationName;
                }
            } else {
                Class<?>[] classes = GenericTypeUtils.resolveInterfaceTypeArguments(aClass, PackageElementVisitor.class);
                if (classes != null && classes.length == 1) {
                    Class<?> annotationClass = classes[0];
                    if (annotationClass == Object.class) {
                        annotation = visitor.getPackageAnnotationName();
                    } else {
                        annotation = annotationClass.getName();
                    }
                } else {
                    annotation = Object.class.getName();
                }
            }
        } else {
            Class<?>[] classes = GenericTypeUtils.resolveInterfaceTypeArguments(aClass, PackageElementVisitor.class);
            if (classes != null && classes.length == 1) {
                Class<?> classGeneric = classes[0];
                if (classGeneric == Object.class) {
                    annotation = visitor.getPackageAnnotationName();
                } else {
                    annotation = classGeneric.getName();
                }
            } else {
                annotation = Object.class.getName();
            }
        }
        return new PackageLoadedVisitor(visitor, annotation);
    }

    /**
     * Finds the generic types for the given interface for the given class element.
     *
     * @param element       The class element
     * @param interfaceName The interface
     * @return The generic types or an empty list
     */
    private static List<? extends TypeMirror> interfaceGenericTypesFor(TypeElement element, String interfaceName) {
        for (TypeMirror tm : element.getInterfaces()) {
            DeclaredType declaredType = (DeclaredType) tm;
            Element declaredElement = declaredType.asElement();
            if (declaredElement instanceof TypeElement te) {
                if (interfaceName.equals(te.getQualifiedName().toString())) {
                    return declaredType.getTypeArguments();
                }
            }
        }
        return Collections.emptyList();
    }

    @Override
    public int getOrder() {
        return visitor.getOrder();
    }

    /**
     * @param annotationMetadata The annotation data
     * @return True if the package element should be visited
     */
    public boolean matches(AnnotationMetadata annotationMetadata) {
        if (annotation.equals("java.lang.Object")) {
            return true;
        }
        return annotationMetadata.hasStereotype(annotation);
    }

}
