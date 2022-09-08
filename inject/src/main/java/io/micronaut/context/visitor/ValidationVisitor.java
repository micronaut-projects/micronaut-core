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
package io.micronaut.context.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.RequiresValidation;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The visitor adds Validated annotation if one of the parameters is a constraint or @Valid.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@NextMajorVersion("This class needs to be moved to the validation module")
public class ValidationVisitor implements TypeElementVisitor<Object, Object> {

    private static final String ANN_CONSTRAINT = "javax.validation.Constraint";
    private static final String ANN_VALID = "javax.validation.Valid";

    private ClassElement classElement;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return new HashSet<>(Arrays.asList(ANN_CONSTRAINT, ANN_VALID));
    }

    @Override
    public int getOrder() {
        return 10; // Should run before ConfigurationReaderVisitor
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        classElement = element;
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        if (classElement == null) {
            return;
        }
        if (requiresValidation(element) || parametersRequireValidation(element)) {
            element.annotate(RequiresValidation.class);
            classElement.annotate(RequiresValidation.class);
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (classElement == null) {
            return;
        }
        if (requiresValidation(element) || parametersRequireValidation(element)) {
            element.annotate(RequiresValidation.class);
            classElement.annotate(RequiresValidation.class);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (classElement == null) {
            return;
        }
        if (requiresValidation(element)) {
            element.annotate(RequiresValidation.class);
            classElement.annotate(RequiresValidation.class);
        }
    }

    private boolean parametersRequireValidation(MethodElement element) {
        return Arrays.stream(element.getParameters()).anyMatch(this::requiresValidation);
    }

    private boolean requiresValidation(Element e) {
        return e.hasStereotype(ANN_VALID) || e.hasStereotype(ANN_CONSTRAINT);
    }
}
