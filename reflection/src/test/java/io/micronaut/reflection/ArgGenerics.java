package io.micronaut.reflection;

import java.util.List;
import java.util.function.Consumer;

/**
 * The generic shapes an argument is read from: a bound naming the variable it bounds, a wildcard with a lower
 * bound, and a hierarchy giving values to the variables a super type leaves open.
 */
public class ArgGenerics {

    public <T extends Comparable<T>> T max(List<T> values) {
        return null;
    }

    public <E extends Enum<E>> void constant(E value) {
    }

    public void receive(Consumer<? super String> consumer) {
    }

    public void supply(List<? extends Number> numbers) {
    }

    public void anything(List<?> values) {
    }

    /**
     * A type whose variable is bounded by the type itself, as a builder declares it.
     */
    public static class Node<T extends Node<T>> {

        public T next;
    }

    /**
     * A type declaring members of a variable it leaves open.
     */
    public static class Base<T> {

        public T dependency;

        public List<T> all;

        public void accept(T value) {
        }

        public List<T> produce() {
            return List.of();
        }
    }

    /**
     * A type passing the variable of its super type on under another name.
     */
    public static class Middle<X> extends Base<X> {
    }

    /**
     * The type giving the variable of the base a value, two levels up.
     */
    public static class Impl extends Middle<String> {
    }

    /**
     * An interface leaving a variable open.
     *
     * @param <T> The value type
     */
    public interface Box<T> {

        T value();
    }

    /**
     * The implementation giving the variable of the interface a value.
     */
    public static class StringBox implements Box<String> {

        @Override
        public String value() {
            return "";
        }
    }
}
