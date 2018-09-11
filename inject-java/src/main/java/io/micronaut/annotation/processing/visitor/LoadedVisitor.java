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

import io.micronaut.annotation.processing.GenericUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
public class LoadedVisitor {

    private final TypeElementVisitor visitor;
    private final String classAnnotation;
    private final String elementAnnotation;
    private final JavaVisitorContext visitorContext;

    /**
     * @param visitor               The {@link TypeElementVisitor}
     * @param visitorContext        The visitor context
     * @param genericUtils          The generic utils
     * @param processingEnvironment The {@link ProcessEnvironment}
     */
    public LoadedVisitor(TypeElementVisitor visitor,
                         JavaVisitorContext visitorContext,
                         GenericUtils genericUtils,
                         ProcessingEnvironment processingEnvironment) {
        this.visitorContext = visitorContext;
        this.visitor = visitor;
        TypeElement typeElement = processingEnvironment.getElementUtils().getTypeElement(visitor.getClass().getName());
        List<? extends TypeMirror> generics = genericUtils.interfaceGenericTypesFor(typeElement, TypeElementVisitor.class.getName());
        classAnnotation = generics.get(0).toString();
        elementAnnotation = generics.get(1).toString();
    }

    /**
     * @return The visitor
     */
    public TypeElementVisitor getVisitor() {
        return visitor;
    }

    /**
     * @param typeElement The class element
     * @return True if the class element should be visited
     */
    public boolean matches(TypeElement typeElement) {
        if (classAnnotation.equals("java.lang.Object")) {
            return true;
        }
        AnnotationMetadata annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(typeElement);
        return annotationMetadata.hasAnnotation(classAnnotation);
    }

    /**
     * @param annotationMetadata The annotation data
     * @return True if the element should be visited
     */
    public boolean matches(AnnotationMetadata annotationMetadata) {
        if (elementAnnotation.equals("java.lang.Object")) {
            return true;
        }
        return annotationMetadata.hasDeclaredAnnotation(elementAnnotation);
    }

    /**
     * Invoke the underlying visitor for the given element.
     *
     * @param element            The element to visit
     * @param annotationMetadata The annotation data for the node
     */
    public void visit(Element element, AnnotationMetadata annotationMetadata) {
        if (element instanceof VariableElement) {
            visitor.visitField(
                    new JavaFieldElement(
                            (VariableElement) element,
                            annotationMetadata),
                    visitorContext
            );
        } else if (element instanceof ExecutableElement) {
            visitor.visitMethod(
                    new JavaMethodElement(
                        (ExecutableElement) element,
                        annotationMetadata, visitorContext),
                    visitorContext
            );
        } else if (element instanceof TypeElement) {
            visitor.visitClass(
                    new JavaClassElement(
                            (TypeElement) element,
                            annotationMetadata,
                            visitorContext),
                    visitorContext
            );
        }
    }
}
