package io.micronaut.ast.groovy.visitor;

import io.micronaut.inject.visitor.MethodElement;
import org.codehaus.groovy.ast.MethodNode;

public class GroovyMethodElement implements MethodElement {

    private final MethodNode methodNode;

    GroovyMethodElement(MethodNode methodNode) {
        this.methodNode = methodNode;
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
}
