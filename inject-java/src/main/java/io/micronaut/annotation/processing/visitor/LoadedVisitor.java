/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.TypeElementVisitor;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
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

    private final TypeElementVisitor visitor;
    private final String classAnnotation;
    private final String elementAnnotation;
    private final JavaVisitorContext visitorContext;
    private JavaClassElement rootClassElement;

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
        Class<? extends TypeElementVisitor> aClass = visitor.getClass();

        TypeElement typeElement = processingEnvironment.getElementUtils().getTypeElement(aClass.getName());
        if (typeElement != null) {
            List<? extends TypeMirror> generics = genericUtils.interfaceGenericTypesFor(typeElement, TypeElementVisitor.class.getName());
            classAnnotation = generics.get(0).toString();
            elementAnnotation = generics.get(1).toString();
        } else {
            Class[] classes = GenericTypeUtils.resolveInterfaceTypeArguments(aClass, TypeElementVisitor.class);
            if (classes != null && classes.length == 2) {
                classAnnotation = classes[0].getName();
                elementAnnotation = classes[1].getName();
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
     * @param typeElement The class element
     * @return True if the class element should be visited
     */
    public boolean matches(TypeElement typeElement) {
        if (classAnnotation.equals("java.lang.Object")) {
            return true;
        }
        AnnotationMetadata annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(typeElement);
        return annotationMetadata.hasStereotype(classAnnotation);
    }

    /**
     * @param annotationMetadata The annotation data
     * @return True if the element should be visited
     */
    public boolean matches(AnnotationMetadata annotationMetadata) {
        if (elementAnnotation.equals("java.lang.Object")) {
            return true;
        }
        return annotationMetadata.hasStereotype(elementAnnotation);
    }

    /**
     * Invoke the underlying visitor for the given element.
     *
     * @param element            The element to visit
     * @param annotationMetadata The annotation data for the node
     *
     * @return The element if one was created or null
     */
    public @Nullable io.micronaut.inject.ast.Element visit(
            Element element, AnnotationMetadata annotationMetadata) {
        if (element instanceof VariableElement) {
            final JavaFieldElement e = new JavaFieldElement(
                    (VariableElement) element,
                    annotationMetadata,
                    visitorContext);
            visitor.visitField(
                    e,
                    visitorContext
            );
            return e;
        } else if (element instanceof ExecutableElement) {
            ExecutableElement executableElement = (ExecutableElement) element;
            if (rootClassElement != null) {

                if (executableElement.getSimpleName().toString().equals("<init>")) {
                    final JavaConstructorElement e = new JavaConstructorElement(
                            rootClassElement,
                            executableElement,
                            annotationMetadata,
                            visitorContext);
                    visitor.visitConstructor(
                            e,
                            visitorContext
                    );
                    return e;
                } else {
                    final JavaMethodElement e = new JavaMethodElement(
                            rootClassElement,
                            executableElement,
                            annotationMetadata, visitorContext);
                    visitor.visitMethod(
                            e,
                            visitorContext
                    );
                    return e;
                }
            }
        } else if (element instanceof TypeElement) {
            TypeElement typeElement = (TypeElement) element;
            boolean isEnum = JavaModelUtils.resolveKind(typeElement, ElementKind.ENUM).isPresent();
            if (isEnum) {
                this.rootClassElement = new JavaEnumElement(
                        typeElement,
                        annotationMetadata,
                        visitorContext);
                visitor.visitClass(
                        rootClassElement,
                        visitorContext
                );
                return rootClassElement;
            } else {
                this.rootClassElement =  new JavaClassElement(
                        typeElement,
                        annotationMetadata,
                        visitorContext);
                visitor.visitClass(
                        rootClassElement,
                        visitorContext
                );
                return rootClassElement;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return visitor.toString();
    }
}
