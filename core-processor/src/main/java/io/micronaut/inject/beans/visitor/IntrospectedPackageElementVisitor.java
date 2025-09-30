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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.PackageElementVisitor;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * A {@link PackageElementVisitor} that visits classes annotated with {@link Introspected}.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public final class IntrospectedPackageElementVisitor implements PackageElementVisitor<Object> {

    @Override
    public void visitPackage(PackageElement element, VisitorContext context) throws ProcessingException {
        AnnotationValue<Introspected> introspectedAnnotation = element.getAnnotation(Introspected.class);
        if (introspectedAnnotation != null) {
            AnnotationValue<AccessorsStyle> accessorsStyleAnnotation = element.getAnnotation(AccessorsStyle.class);
            for (ClassElement classElement : context.getClassElements(element)) {
                if (accessorsStyleAnnotation != null) {
                    classElement.annotate(accessorsStyleAnnotation);
                    classElement.annotate(introspectedAnnotation);
                }
            }
        }
    }

    @NonNull
    @Override
    public TypeElementVisitor.VisitorKind getVisitorKind() {
        return TypeElementVisitor.VisitorKind.ISOLATING;
    }

}
