package io.micronaut.reflection;

/**
 * An interface giving a type to the variable of the generic interface it extends: the property it inherits is
 * of that type, not of the bound of the variable.
 */
public interface IntroBoxView extends IntroBox<String> {
}
