package io.micronaut.reflection;

/**
 * A generic super class whose property is of the type the sub class gives to the variable.
 *
 * @param <T> The type of the value
 */
public class IntroGenericBox<T> {

    private T value;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
