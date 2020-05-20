/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.validation.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Logs warnings during compilation if any class extends an internal or
 * experimental Micronaut API.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
public class InternalApiTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    private static final String IO_MICRONAUT = "io.micronaut";

    private boolean warned = false;

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.getName().startsWith(IO_MICRONAUT)) {
            warn(element, context);
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        warnMember(element, context);
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        warnMember(element, context);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        warnMember(element, context);
    }

    private void warnMember(MemberElement element, VisitorContext context) {
        if (!element.getDeclaringType().getName().startsWith(IO_MICRONAUT)) {
            warn(element, context);
        }
    }

    private void warn(Element element, VisitorContext context) {
        if (element.hasAnnotation(Internal.class) || element.hasAnnotation(Experimental.class)) {
            warned = true;
            context.warn("Element extends or implements an internal or experimental Micronaut API", element);
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (warned) {
            visitorContext.warn("Overriding an internal Micronaut API may result in breaking changes in minor or patch versions of the framework. Proceed with caution!", null);
        }
    }
}
