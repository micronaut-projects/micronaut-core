package io.micronaut.ast.groovy.utils;

import io.micronaut.core.annotation.Internal;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;

import java.util.Objects;

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
