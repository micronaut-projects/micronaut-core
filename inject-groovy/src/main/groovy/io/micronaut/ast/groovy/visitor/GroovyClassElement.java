package io.micronaut.ast.groovy.visitor;

import io.micronaut.inject.visitor.ClassElement;
import org.codehaus.groovy.ast.ClassNode;

import java.lang.reflect.Modifier;

public class GroovyClassElement implements ClassElement {

    private final ClassNode classNode;

    GroovyClassElement(ClassNode classNode) {
        this.classNode = classNode;
    }

    @Override
    public String getName() {
        return classNode.getName();
    }

    @Override
    public boolean isAbstract() {
        return classNode.isAbstract();
    }

    @Override
    public boolean isStatic() {
        return classNode.isStaticClass();
    }

    @Override
    public boolean isPublic() {
        return classNode.isSyntheticPublic() || Modifier.isPublic(classNode.getModifiers());
    }

    @Override
    public boolean isPrivate() {
        return Modifier.isPrivate(classNode.getModifiers());
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(classNode.getModifiers());
    }

    @Override
    public boolean isProtected() {
        return Modifier.isProtected(classNode.getModifiers());
    }

    @Override
    public Object getNativeType() {
        return classNode;
    }
}
