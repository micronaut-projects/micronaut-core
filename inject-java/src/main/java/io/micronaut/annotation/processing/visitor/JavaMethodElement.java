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
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationsHelper;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

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
    private Map<String, ClassElement> typeArguments;
    private Map<String, ClassElement> declaredTypeArguments;
    private final MethodElementAnnotationsHelper helper;

    /**
     * @param owningType                The declaring class
     * @param nativeElement             The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    public JavaMethodElement(JavaClassElement owningType,
                             JavaNativeElement.Method nativeElement,
                             ElementAnnotationMetadataFactory annotationMetadataFactory,
                             JavaVisitorContext visitorContext) {
        super(nativeElement, annotationMetadataFactory, visitorContext);
        this.executableElement = nativeElement.element();
        this.owningType = owningType;
        this.helper = new MethodElementAnnotationsHelper(this, annotationMetadataFactory);
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public ElementAnnotationMetadata getMethodAnnotationMetadata() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return helper.getAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public JavaNativeElement.Method getNativeType() {
        return (JavaNativeElement.Method) super.getNativeType();
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaMethodElement(owningType, getNativeType(), elementAnnotationMetadataFactory, visitorContext);
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
                final ClassElement classElement = newClassElement(receiverType, getDeclaringType().getTypeArguments());
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
                .map(tm -> newClassElement(tm, getDeclaringType().getTypeArguments()))
                .toArray(ClassElement[]::new);
        }

        return ClassElement.ZERO_CLASS_ELEMENTS;
    }

    @Override
    public boolean isDefault() {
        return executableElement.isDefault();
    }

    @Override
    public boolean isVarArgs() {
        return executableElement.isVarArgs();
    }

    @Override
    public boolean overrides(MethodElement overridden) {
        if (this.equals(overridden) || isStatic() || overridden.isStatic()) {
            return false;
        }
        if (overridden instanceof JavaMethodElement javaMethodElement) {
            boolean overrides = visitorContext.getElements().overrides(
                executableElement,
                javaMethodElement.executableElement,
                owningType.classElement
            );
            if (overrides) {
                return true;
            }
        }
        return MethodElement.super.overrides(overridden);
    }

    @Override
    public boolean hides(MemberElement hidden) {
        if (isStatic() && getDeclaringType().isInterface()) {
            return false;
        }
        return visitorContext.getElements().hides(getNativeType().element(), ((JavaNativeElement.Method) hidden.getNativeType()).element());
    }

    @NonNull
    @Override
    public ClassElement getGenericReturnType() {
        if (genericReturnType == null) {
            genericReturnType = returnType(getDeclaringType().getTypeArguments());
        }
        return genericReturnType;
    }

    @Override
    @NonNull
    public ClassElement getReturnType() {
        if (returnType == null) {
            returnType = returnType(Collections.emptyMap());
        }
        return returnType;
    }

    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredTypeVariables() {
        return executableElement.getTypeParameters().stream()
            .map(tpe -> (GenericPlaceholderElement) newClassElement(tpe.asType(), Collections.emptyMap()))
            .toList();
    }

    @Override
    public Map<String, ClassElement> getTypeArguments() {
        if (typeArguments == null) {
            typeArguments = MethodElement.super.getTypeArguments();
        }
        return typeArguments;
    }

    @Override
    public Map<String, ClassElement> getDeclaredTypeArguments() {
        if (declaredTypeArguments == null) {
            declaredTypeArguments = resolveTypeArguments(executableElement, getDeclaringType().getTypeArguments());
        }
        return declaredTypeArguments;
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
        JavaMethodElement javaMethodElement = new JavaMethodElement((JavaClassElement) owningType, getNativeType(), elementAnnotationMetadataFactory, visitorContext);
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
        return new JavaParameterElement(owningType, methodElement, new JavaNativeElement.Variable(variableElement), elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public JavaClassElement getDeclaringType() {
        if (resolvedDeclaringClass == null) {
            Element enclosingElement = executableElement.getEnclosingElement();
            if (enclosingElement instanceof TypeElement te) {
                String typeName = te.getQualifiedName().toString();
                if (owningType.getName().equals(typeName)) {
                    resolvedDeclaringClass = owningType;
                } else {
                    Map<String, ClassElement> parentTypeArguments = owningType.getTypeArguments(typeName);
                    resolvedDeclaringClass = (JavaClassElement) newClassElement(te.asType(), parentTypeArguments);
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

    private ClassElement returnType(Map<String, ClassElement> genericInfo) {
        VariableElement varElement = CollectionUtils.last(executableElement.getParameters());
        if (isSuspend(varElement)) {
            DeclaredType dType = (DeclaredType) varElement.asType();
            TypeMirror tm = dType.getTypeArguments().iterator().next();
            if (tm.getKind() == TypeKind.WILDCARD) {
                tm = ((WildcardType) tm).getSuperBound();
            }
            // check Void
            if ((tm instanceof DeclaredType dt) && sameType("kotlin.Unit", dt)) {
                return PrimitiveElement.VOID;
            } else {
                return newClassElement(tm, genericInfo);
            }
        }
        final TypeMirror returnType = executableElement.getReturnType();
        return newClassElement(getNativeType(), returnType, genericInfo);
    }

    private static boolean sameType(String type, DeclaredType dt) {
        Element elt = dt.asElement();
        return (elt instanceof TypeElement) && type.equals(((TypeElement) elt).getQualifiedName().toString());
    }

    private boolean isSuspend(VariableElement ve) {
        if (ve != null && ve.asType() instanceof DeclaredType dt) {
            return sameType("kotlin.coroutines.Continuation", dt);
        }
        return false;
    }

}
