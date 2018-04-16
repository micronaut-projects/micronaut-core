package io.micronaut.annotation.processing.visitor;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

public abstract class AbstractJavaElement implements io.micronaut.inject.visitor.Element {

    private final Element element;

    AbstractJavaElement(Element element) {
        this.element = element;
    }

    private boolean hasModifier(Modifier modifier) {
        return element.getModifiers().contains(modifier);
    }

    @Override
    public String getName() {
        return element.getSimpleName().toString();
    }

    @Override
    public boolean isAbstract() {
        return hasModifier(Modifier.ABSTRACT);
    }

    @Override
    public boolean isStatic() {
        return hasModifier(Modifier.STATIC);
    }

    @Override
    public boolean isPublic() {
        return hasModifier(Modifier.PUBLIC);
    }

    @Override
    public boolean isPrivate() {
        return hasModifier(Modifier.PRIVATE);
    }

    @Override
    public boolean isFinal() {
        return hasModifier(Modifier.FINAL);
    }

    @Override
    public boolean isProtected() {
        return hasModifier(Modifier.PROTECTED);
    }

    @Override
    public Object getNativeType() {
        return element;
    }
}
