package io.micronaut.inject.visitor.beans.hidden;

/**
 * Hides the field of its package-private super class, and inherits the other one.
 */
public class HiddenChild extends HiddenBase {

    public String name = "child";
}
