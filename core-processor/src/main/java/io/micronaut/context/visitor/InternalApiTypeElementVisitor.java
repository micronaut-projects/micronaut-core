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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Set;

/**
 * Logs warnings during compilation if any class extends an internal or
 * experimental Micronaut API.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
@Internal
public class InternalApiTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    private static final String IO_MICRONAUT = "io.micronaut";

    private static final String MICRONAUT_PROCESSING_INTERNAL_WARNINGS = "micronaut.processing.internal.warnings";

    private boolean isMicronautClass;
    private boolean hasMicronautSuperClass;
    private boolean warned;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(
            Internal.class.getName(),
            Experimental.class.getName()
        );
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(MICRONAUT_PROCESSING_INTERNAL_WARNINGS);
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        reset();
        isMicronautClass = isMicronautElement(element);
        if (isMicronautClass) {
            return;
        }
        ClassElement currentElement = element;
        while (true) {
            currentElement = currentElement.getSuperType().orElse(null);
            if (currentElement == null) {
                hasMicronautSuperClass = false;
                break;
            }
            if (isMicronautElement(currentElement)) {
                hasMicronautSuperClass = true;
                if (isInternalOrExperimental(currentElement)) {
                    warn(element, context);
                }
                break;
            }
        }
    }

    private void reset() {
        warned = false;
        hasMicronautSuperClass = false;
        isMicronautClass = false;
    }

    private boolean isMicronautElement(ClassElement element) {
        return element.getName().startsWith(IO_MICRONAUT);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        warnMember(element, context);
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        warnMember(element, context);
    }

    private void warnMember(MethodElement element, VisitorContext context) {
        if (isMicronautClass || !hasMicronautSuperClass) {
            return;
        }
        if (!element.getDeclaringType().equals(element.getOwningType())) {
            // We are only interested in declared methods
            return;
        }
        if (!isInternalOrExperimental(element.getMethodAnnotationMetadata())) {
            return;
        }
        // We can probably check if the method is actually overridden but let's avoid it for perf reasons
        warn(element, context);
    }

    private void warn(Element element, VisitorContext context) {
        warned = true;
        if (warnEnabled(context)) {
            context.warn("Element extends or implements an internal or experimental Micronaut API", element);
        }
    }

    private boolean isInternalOrExperimental(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasAnnotation(Internal.class) || annotationMetadata.hasAnnotation(Experimental.class);
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (warned && warnEnabled(visitorContext)) {
            visitorContext.warn("Overriding an internal Micronaut API may result in breaking changes in minor or patch versions of the framework. Proceed with caution!", null);
        }
        reset();
    }

    private boolean warnEnabled(VisitorContext visitorContext) {
        String value = visitorContext.getOptions().get(MICRONAUT_PROCESSING_INTERNAL_WARNINGS);
        return value == null || StringUtils.TRUE.equals(value);
    }
}
