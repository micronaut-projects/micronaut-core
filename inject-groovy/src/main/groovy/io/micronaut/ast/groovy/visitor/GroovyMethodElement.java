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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.ast.groovy.utils.ExtendedParameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A method element returning data from a {@link MethodNode}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class GroovyMethodElement extends AbstractGroovyElement implements MethodElement {

    private final SourceUnit sourceUnit;
    private final MethodNode methodNode;
    private final GroovyClassElement declaringClass;
    private Map<String, ClassNode> genericsSpec = null;

    /**
     * @param declaringClass     The declaring class
     * @param sourceUnit         The source unit
     * @param compilationUnit    The compilation unit
     * @param methodNode         The {@link MethodNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyMethodElement(GroovyClassElement declaringClass, SourceUnit sourceUnit, CompilationUnit compilationUnit, MethodNode methodNode, AnnotationMetadata annotationMetadata) {
        super(sourceUnit, compilationUnit, methodNode, annotationMetadata);
        this.methodNode = methodNode;
        this.sourceUnit = sourceUnit;
        this.declaringClass = declaringClass;
    }

    @Override
    public String getName() {
        return methodNode.getName();
    }

    @Override
    public boolean isAbstract() {
        return methodNode.isAbstract();
    }

    @Override
    public boolean isStatic() {
        return methodNode.isStatic();
    }

    @Override
    public boolean isPublic() {
        return methodNode.isPublic() || methodNode.isSyntheticPublic();
    }

    @Override
    public boolean isPrivate() {
        return methodNode.isPrivate();
    }

    @Override
    public boolean isFinal() {
        return methodNode.isFinal();
    }

    @Override
    public boolean isProtected() {
        return methodNode.isProtected();
    }

    @Override
    public Object getNativeType() {
        return methodNode;
    }

    @NonNull
    @Override
    public ClassElement getGenericReturnType() {
        ClassNode returnType = methodNode.getReturnType();
        ClassElement rawElement = getReturnType();
        return getGenericElement(returnType, rawElement);
    }

    /**
     * Obtains the generic element if present otherwise returns the raw element.
     *
     * @param type       The type
     * @param rawElement The raw element
     * @return The class element
     */
    @NonNull
    ClassElement getGenericElement(@NonNull ClassNode type, @NonNull ClassElement rawElement) {
        Map<String, ClassNode> genericsSpec = getGenericsSpec();

        return getGenericElement(sourceUnit, type, rawElement, genericsSpec);
    }

    /**
     * Resolves the generics spec for this method.
     *
     * @return The generic spec
     */
    @NonNull
    Map<String, ClassNode> getGenericsSpec() {
        if (genericsSpec == null) {
            Map<String, Map<String, ClassNode>> info = declaringClass.getGenericTypeInfo();
            if (CollectionUtils.isNotEmpty(info)) {
                Map<String, ClassNode> typeGenericInfo = info.get(methodNode.getDeclaringClass().getName());
                if (CollectionUtils.isNotEmpty(typeGenericInfo)) {

                    genericsSpec = AstGenericUtils.createGenericsSpec(methodNode, new HashMap<>(typeGenericInfo));
                }
            }

            if (genericsSpec == null) {
                genericsSpec = Collections.emptyMap();
            }
        }
        return genericsSpec;
    }

    @Override
    @NonNull
    public ClassElement getReturnType() {
        ClassNode returnType = methodNode.getReturnType();
        if (returnType.isEnum()) {
            return new GroovyEnumElement(sourceUnit, compilationUnit, returnType, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, returnType));
        } else {
            return new GroovyClassElement(sourceUnit, compilationUnit, returnType, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, returnType));
        }
    }

    @Override
    public ParameterElement[] getParameters() {
        Parameter[] parameters = methodNode.getParameters();
        return Arrays.stream(parameters).map((Function<Parameter, ParameterElement>) parameter ->
                new GroovyParameterElement(
                        this,
                        sourceUnit,
                        compilationUnit,
                        parameter,
                        AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, new ExtendedParameter(methodNode, parameter))
                )
        ).toArray(ParameterElement[]::new);
    }

    @Override
    public ClassElement getDeclaringType() {
        return new GroovyClassElement(
                sourceUnit,
                compilationUnit,
                methodNode.getDeclaringClass(),
                AstAnnotationUtils.getAnnotationMetadata(
                        sourceUnit,
                        compilationUnit,
                        methodNode.getDeclaringClass()
                )
        );
    }

    @Override
    public ClassElement getOwningType() {
        return declaringClass;
    }
}
