/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.RegisterAnnotations;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.PackageElementVisitor;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates the annotation builders requested by {@link RegisterAnnotations}, on a type or on a package.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RegisterAnnotationsVisitor implements TypeElementVisitor<RegisterAnnotations, Object> {

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        generate(element, element.getName(), context);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    static void generate(Element element, String holderName, VisitorContext context) {
        AnnotationValue<RegisterAnnotations> annotation = element.getAnnotation(RegisterAnnotations.class);
        if (annotation == null) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        for (AnnotationClassValue<?> classValue : annotation.annotationClassValues(AnnotationMetadata.VALUE_MEMBER)) {
            names.add(classValue.getName());
        }
        for (String name : names) {
            ClassElement annotationType = context.getClassElement(name)
                .orElseThrow(() -> new ProcessingException(element, "Annotation type not found on the compilation classpath: " + name));
            if (!annotationType.isPublic()) {
                throw new ProcessingException(element, "The annotation type has to be public to have a builder: " + name);
            }
            context.getAnnotationBuilderRequests().registerListed(holderName, annotationType, element, context);
        }
    }

    /**
     * The {@link RegisterAnnotations} on a package.
     */
    @Internal
    public static final class OnPackage implements PackageElementVisitor<RegisterAnnotations> {

        @Override
        public void visitPackage(PackageElement element, VisitorContext context) throws ProcessingException {
            generate(element, element.getName() + ".package-info", context);
        }

        @Override
        public TypeElementVisitor.VisitorKind getVisitorKind() {
            return TypeElementVisitor.VisitorKind.ISOLATING;
        }
    }
}
