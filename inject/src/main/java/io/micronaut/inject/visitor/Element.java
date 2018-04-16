package io.micronaut.inject.visitor;

public interface Element {

    String getName();

    boolean isAbstract();

    boolean isStatic();

    boolean isPublic();

    boolean isPrivate();

    boolean isFinal();

    boolean isProtected();

    Object getNativeType();
}
