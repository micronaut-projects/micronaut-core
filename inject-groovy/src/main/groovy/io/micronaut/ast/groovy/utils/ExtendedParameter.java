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
package io.micronaut.ast.groovy.utils;

import io.micronaut.core.annotation.Internal;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;

/**
 * This class was created to pass to the {@link io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder} because
 * the method node the parameter belongs to is not available from the {@link org.codehaus.groovy.ast.Parameter} class
 * itself. The method node is necessary to support argument annotation metadata inheritance.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public final class ExtendedParameter extends AnnotatedNode {

    private final MethodNode methodNode;
    private final Parameter parameter;

    /**
     * @param methodNode The method node that contains the parameter
     * @param parameter  The parameter
     */
    public ExtendedParameter(MethodNode methodNode, Parameter parameter) {
        this.methodNode = methodNode;
        this.parameter = parameter;
        this.addAnnotations(parameter.getAnnotations());
        this.setSynthetic(parameter.isSynthetic());
        this.setDeclaringClass(parameter.getDeclaringClass());
        this.setHasNoRealSourcePosition(parameter.hasNoRealSourcePosition());
    }

    /**
     * @return The method node that contains the parameter
     */
    public MethodNode getMethodNode() {
        return methodNode;
    }

    /**
     * @return The parameter
     */
    public Parameter getParameter() {
        return parameter;
    }

    @Override
    public int hashCode() {
        return parameter.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof ExtendedParameter) &&
                (this.parameter == ((ExtendedParameter) o).parameter);
    }
}
