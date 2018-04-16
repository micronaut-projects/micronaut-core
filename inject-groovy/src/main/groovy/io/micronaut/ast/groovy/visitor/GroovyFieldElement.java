package io.micronaut.ast.groovy.visitor;

import io.micronaut.inject.visitor.FieldElement;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;

import java.lang.reflect.Modifier;

public class GroovyFieldElement implements FieldElement {

    private final Variable variable;

    GroovyFieldElement(Variable variable) {
        this.variable = variable;
    }

    @Override
    public String getName() {
        return variable.getName();
    }

    @Override
    public boolean isAbstract() {
        return Modifier.isAbstract(variable.getModifiers());
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(variable.getModifiers());
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(variable.getModifiers());
    }

    @Override
    public boolean isPrivate() {
        return Modifier.isPrivate(variable.getModifiers());
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(variable.getModifiers());
    }

    @Override
    public boolean isProtected() {
        return Modifier.isProtected(variable.getModifiers());
    }

    @Override
    public Object getNativeType() {
        return variable;
    }
}
