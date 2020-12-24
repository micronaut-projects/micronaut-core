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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.inject.ast.PrimitiveElement;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
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

    private final ExecutableElement executableElement;
    private final JavaVisitorContext visitorContext;
    private final JavaClassElement declaringClass;
    private JavaClassElement resolvedDeclaringClass;
    private ParameterElement[] parameters;
    private ParameterElement continuationParameter;
    private ClassElement genericReturnType;
    private ClassElement returnType;

    /**
     * @param declaringClass     The declaring class
     * @param executableElement  The {@link ExecutableElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext The visitor context
     */
    public JavaMethodElement(
            JavaClassElement declaringClass,
            ExecutableElement executableElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext) {
        super(executableElement, annotationMetadata, visitorContext);
        this.executableElement = executableElement;
        this.visitorContext = visitorContext;
        this.declaringClass = declaringClass;
    }

    @Override
    public boolean isDefault() {
        return executableElement.isDefault();
    }

    @NonNull
    @Override
    public ClassElement getGenericReturnType() {
        if (this.genericReturnType == null) {
            this.genericReturnType = returnType(declaringClass.getGenericTypeInfo());
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
    public Optional<String> getDocumentation() {
        return Optional.ofNullable(visitorContext.getElements().getDocComment(executableElement));
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
            for (Iterator<? extends VariableElement> i = parameters.iterator(); i.hasNext();) {
                VariableElement variableElement = i.next();
                if (! i.hasNext() && isSuspend(variableElement)) {
                    this.continuationParameter = newParameterElement(variableElement, AnnotationMetadata.EMPTY_METADATA);
                    continue;
                }
                AnnotationMetadata annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(variableElement);
                JavaParameterElement javaParameterElement = newParameterElement(variableElement, annotationMetadata);
                if (annotationMetadata.hasDeclaredAnnotation("org.jetbrains.annotations.Nullable")) {
                    javaParameterElement.annotate("javax.annotation.Nullable").getAnnotationMetadata();
                }
                elts.add(javaParameterElement);
            }
            this.parameters = elts.toArray(new ParameterElement[0]);
        }
        return this.parameters;
    }

    @Override
    public MethodElement withNewParameters(ParameterElement... newParameters) {
        return new JavaMethodElement(declaringClass, executableElement, getAnnotationMetadata(), visitorContext) {
            @Override
            public ParameterElement[] getParameters() {
                return ArrayUtils.concat(super.getParameters(), newParameters);
            }
        };
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
     * @param variableElement The variable element
     * @param annotationMetadata The annotation metadata
     * @return The parameter element
     */
    @NonNull
    protected JavaParameterElement newParameterElement(@NonNull VariableElement variableElement, @NonNull AnnotationMetadata annotationMetadata) {
        return new JavaParameterElement(declaringClass, variableElement, annotationMetadata, visitorContext);
    }

    @Override
    public ClassElement getDeclaringType() {
        if (resolvedDeclaringClass == null) {

            Element enclosingElement = executableElement.getEnclosingElement();
            if (enclosingElement instanceof TypeElement) {
                TypeElement te = (TypeElement) enclosingElement;
                if (declaringClass.getName().equals(te.getQualifiedName().toString())) {
                    resolvedDeclaringClass = declaringClass;
                } else {
                    resolvedDeclaringClass = new JavaClassElement(
                            te,
                            visitorContext.getAnnotationUtils().getAnnotationMetadata(te),
                            visitorContext,
                            declaringClass.getGenericTypeInfo()
                    );
                }
            } else {
                return declaringClass;
            }
        }
        return resolvedDeclaringClass;
    }

    @Override
    public ClassElement getOwningType() {
        return declaringClass;
    }

    /**
     * The return type for the given info.
     * @param info The info
     * @return The return type
     */
    protected ClassElement returnType(Map<String, Map<String, TypeMirror>> info) {
        VariableElement varElement = CollectionUtils.last(executableElement.getParameters());
        if (isSuspend(varElement)) {
            DeclaredType dType = (DeclaredType) varElement.asType();
            WildcardType wType = (WildcardType) dType.getTypeArguments().iterator().next();
            TypeMirror tm = wType.getSuperBound();
            // check Void
            if ((tm instanceof DeclaredType) && sameType("kotlin.Unit", (DeclaredType) tm)) {
                return PrimitiveElement.VOID;
            } else {
                return mirrorToClassElement(tm, visitorContext, info, true);
            }
        }
        return mirrorToClassElement(executableElement.getReturnType(), visitorContext, info, true);
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
