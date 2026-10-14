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
package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.RecordTypeHierarchy;
import io.micronaut.inject.ast.ClassElement;

/**
 * Records the generic hierarchies that {@link RecordTypeHierarchy} asks for.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class TypeHierarchyVisitor implements TypeElementVisitor<RecordTypeHierarchy, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        AnnotationValue<RecordTypeHierarchy> annotation = element.getAnnotation(RecordTypeHierarchy.class);
        if (annotation == null) {
            return;
        }
        AnnotationClassValue<?>[] classes = annotation.annotationClassValues("classes");
        if (classes.length == 0) {
            context.recordTypeHierarchy(element, element);
            return;
        }
        for (AnnotationClassValue<?> classValue : classes) {
            ClassElement type = context.getClassElement(classValue.getName()).orElse(null);
            if (type == null) {
                context.fail("The hierarchy of [" + classValue.getName() + "] cannot be recorded: the class is not on the class path", element);
            } else {
                context.recordTypeHierarchy(type, element);
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
