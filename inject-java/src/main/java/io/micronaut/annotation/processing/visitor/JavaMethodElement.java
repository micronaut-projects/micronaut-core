/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A method element returning data from a {@link ExecutableElement}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class JavaMethodElement extends AbstractJavaElement implements MethodElement {

    protected final JavaClassElement owningType;
    protected final ExecutableElement executableElement;
    private JavaClassElement resolvedDeclaringClass;
    private ParameterElement[] parameters;
    private ParameterElement continuationParameter;
    private ClassElement genericReturnType;
    private ClassElement returnType;

    /**
     * @param owningType                The declaring class
     * @param executableElement         The {@link ExecutableElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    public JavaMethodElement(JavaClassElement owningType,
                             ExecutableElement executableElement,
                             ElementAnnotationMetadataFactory annotationMetadataFactory,
                             JavaVisitorContext visitorContext) {
        super(executableElement, annotationMetadataFactory, visitorContext);
        this.executableElement = executableElement;
        this.owningType = owningType;
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaMethodElement(owningType, executableElement, elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    protected void copyValues(AbstractJavaElement element) {
        super.copyValues(element);
        ((JavaMethodElement) element).parameters = parameters;
    }

    @Override
    public MethodElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (MethodElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public MethodElement withParameters(ParameterElement... parameters) {
        JavaMethodElement methodElement = (JavaMethodElement) makeCopy();
        methodElement.parameters = parameters;
        return methodElement;
    }

    @Override
    public Optional<ClassElement> getReceiverType() {
        final TypeMirror receiverType = executableElement.getReceiverType();
        if (receiverType != null) {
            if (receiverType.getKind() != TypeKind.NONE) {
                final ClassElement classElement = mirrorToClassElement(receiverType,
                    visitorContext,
                    owningType.getGenericTypeInfo());
                return Optional.of(classElement);
            }
        }
        return Optional.empty();
    }

    @Override
    @NonNull
    public ClassElement[] getThrownTypes() {
        final List<? extends TypeMirror> thrownTypes = executableElement.getThrownTypes();
        if (!thrownTypes.isEmpty()) {
            return thrownTypes.stream()
                .map(tm -> mirrorToClassElement(
                    tm,
                    visitorContext,
                    owningType.getGenericTypeInfo()
                )).toArray(ClassElement[]::new);
        }

        return ClassElement.ZERO_CLASS_ELEMENTS;
    }

    @Override
    public boolean isDefault() {
        return executableElement.isDefault();
    }

    @Override
    public boolean overrides(MethodElement overridden) {
//        if (this.equals(overridden) || isStatic() || overridden.isStatic()) {
//            return false;
//        }
//        if (overridden instanceof JavaMethodElement) {
//            boolean overrides = visitorContext.getElements().overrides(
//                executableElement,
//                ((JavaMethodElement) overridden).executableElement,
//                owningType.classElement
//            );
//            if (overrides) {
//                return true;
//            }
//        }
        return MethodElement.super.overrides(overridden);
    }

    @NonNull
    @Override
    public ClassElement getGenericReturnType() {
        if (this.genericReturnType == null) {
            this.genericReturnType = returnType(owningType.getGenericTypeInfo());
        }
        return this.genericReturnType;
    }

    @Override
    @NonNull
    public ClassElement getReturnType() {
        if (this.returnType == null) {
            this.returnType = returnType(Collections.emptyMap());
        }
        return this.returnType;
    }

    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredTypeVariables() {
        return executableElement.getTypeParameters().stream()
            .map(tpe -> (GenericPlaceholderElement) mirrorToClassElement(tpe.asType(), visitorContext))
            .collect(Collectors.toList());
    }

    @Override
    public boolean isSuspend() {
        getParameters();
        return this.continuationParameter != null;
    }

    @Override
    public ParameterElement[] getParameters() {
        if (this.parameters == null) {
            List<? extends VariableElement> parameters = executableElement.getParameters();
            List<ParameterElement> elts = new ArrayList<>(parameters.size());
            for (Iterator<? extends VariableElement> i = parameters.iterator(); i.hasNext(); ) {
                VariableElement variableElement = i.next();
                if (!i.hasNext() && isSuspend(variableElement)) {
                    this.continuationParameter = newParameterElement(this, variableElement);
                    continue;
                }
                elts.add(newParameterElement(this, variableElement));
            }
            this.parameters = elts.toArray(new ParameterElement[0]);
        }
        return this.parameters;
    }

    @Override
    public MethodElement withNewOwningType(ClassElement owningType) {
        JavaMethodElement javaMethodElement = new JavaMethodElement((JavaClassElement) owningType, executableElement, elementAnnotationMetadataFactory, visitorContext);
        copyValues(javaMethodElement);
        return javaMethodElement;
    }

    @Override
    public ParameterElement[] getSuspendParameters() {
        ParameterElement[] parameters = getParameters();
        if (isSuspend()) {
            return ArrayUtils.concat(parameters, continuationParameter);
        } else {
            return parameters;
        }
    }

    /**
     * Creates a new parameter element for the given args.
     *
     * @param methodElement   The method element
     * @param variableElement The variable element
     * @return The parameter element
     */
    @NonNull
    protected JavaParameterElement newParameterElement(@NonNull MethodElement methodElement, @NonNull VariableElement variableElement) {
        return new JavaParameterElement(owningType, methodElement, variableElement, elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public JavaClassElement getDeclaringType() {
        if (resolvedDeclaringClass == null) {
            Element enclosingElement = executableElement.getEnclosingElement();
            if (enclosingElement instanceof TypeElement) {
                TypeElement te = (TypeElement) enclosingElement;
                if (owningType.getName().equals(te.getQualifiedName().toString())) {
                    resolvedDeclaringClass = owningType;
                } else {
                    resolvedDeclaringClass = (JavaClassElement) mirrorToClassElement(te.asType(), visitorContext, owningType.getGenericTypeInfo());
                }
            } else {
                return owningType;
            }
        }
        return resolvedDeclaringClass;
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }

    /**
     * The return type for the given info.
     *
     * @param info The info
     * @return The return type
     */
    protected ClassElement returnType(Map<String, Map<String, TypeMirror>> info) {
        VariableElement varElement = CollectionUtils.last(executableElement.getParameters());
        if (isSuspend(varElement)) {
            DeclaredType dType = (DeclaredType) varElement.asType();
            TypeMirror tm = dType.getTypeArguments().iterator().next();
            if (tm.getKind() == TypeKind.WILDCARD) {
                tm = ((WildcardType) tm).getSuperBound();
            }
            // check Void
            if ((tm instanceof DeclaredType) && sameType("kotlin.Unit", (DeclaredType) tm)) {
                return PrimitiveElement.VOID;
            } else {
                return mirrorToClassElement(tm, visitorContext, info, true);
            }
        }
        final TypeMirror returnType = executableElement.getReturnType();
        return mirrorToClassElement(returnType, visitorContext, info, true);
    }

    private static boolean sameType(String type, DeclaredType dt) {
        Element elt = dt.asElement();
        return (elt instanceof TypeElement) && type.equals(((TypeElement) elt).getQualifiedName().toString());
    }

    private boolean isSuspend(VariableElement ve) {
        if (ve != null && ve.asType() instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) ve.asType();
            return sameType("kotlin.coroutines.Continuation", dt);
        }
        return false;
    }

}
