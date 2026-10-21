package io.micronaut.core.type.hierarchy;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GeneratedTypeHierarchy;
import io.micronaut.core.type.TypeHierarchy;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.Supplier;

/**
 * Hand-written hierarchies, shaped as the writer shapes them, and the types they describe.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked", "serial"})
public final class Records {

    private Records() {
    }

    public static class Payload<T extends Number> extends ArrayList<T> implements Supplier<List<T>> {
        @Override
        public List<T> get() {
            return this;
        }
    }

    public interface Top<T> {
    }

    public interface Left<T> extends Top<T> {
    }

    public interface Right<T> extends Top<T> {
    }

    public static class Diamond implements Left<String>, Right<String> {
    }

    public static class Arrays<T> implements Supplier<T[]> {
        @Override
        public T[] get() {
            return null;
        }
    }

    public static class RawSub extends ArrayList {
    }

    public static class Unrelated {
    }

    public static Argument<?> variable(String name) {
        return Argument.ofTypeVariable(Object.class, name);
    }

    public static Argument<?> variable(String name, Class<?> bound) {
        return Argument.ofTypeVariable(bound, name);
    }

    public static Argument<?> of(Class<?> type, Argument<?>... typeParameters) {
        return Argument.of(type, (String) null, typeParameters);
    }

    public static Argument<?> raw(Class<?> type, Argument<?>... typeParameters) {
        return Argument.ofRawType(type, null, null, typeParameters);
    }

    public static Argument<?> extendsWildcard(Class<?> bound) {
        return Argument.ofWildcard(bound, null, null, null, new Argument[]{Argument.of(bound)}, null);
    }

    public static TypeHierarchy hierarchy(Class<?> type, List<Argument<?>> variables, Argument<?> superclass, List<Argument<?>> interfaces, List<Argument<?>> superTypes) {
        return new GeneratedTypeHierarchy(type, variables.toArray(Argument.ZERO_ARGUMENTS), superclass, interfaces.toArray(Argument.ZERO_ARGUMENTS),
            superTypes.toArray(Argument.ZERO_ARGUMENTS)) {
        };
    }

    /** The super types of {@code ArrayList<E>} as the compiler lists them, {@code E} being the given argument. */
    public static List<Argument<?>> arrayListSuperTypes(Argument<?> e) {
        return List.of(of(AbstractList.class, e), of(AbstractCollection.class, e), of(Collection.class, e), of(Iterable.class, e), of(List.class, e),
            Argument.of(RandomAccess.class), Argument.of(Cloneable.class), Argument.of(Serializable.class));
    }

    public static List<Argument<?>> concat(List<Argument<?>> first, List<Argument<?>> second) {
        List<Argument<?>> all = new java.util.ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    public static final TypeHierarchy PAYLOAD = hierarchy(Payload.class, List.of(variable("T", Number.class)),
        of(ArrayList.class, variable("T", Number.class)),
        List.of(of(Supplier.class, of(List.class, variable("T", Number.class)))),
        concat(concat(List.of(of(ArrayList.class, variable("T", Number.class))), arrayListSuperTypes(variable("T", Number.class))),
            List.of(of(Supplier.class, of(List.class, variable("T", Number.class))), Argument.OBJECT_ARGUMENT)));
    public static final TypeHierarchy DIAMOND = hierarchy(Diamond.class, List.of(), null, List.of(of(Left.class, Argument.STRING), of(Right.class, Argument.STRING)),
        List.of(of(Left.class, Argument.STRING), of(Top.class, Argument.STRING), of(Right.class, Argument.STRING), Argument.OBJECT_ARGUMENT));
    public static final TypeHierarchy ARRAYS = hierarchy(Arrays.class, List.of(variable("T")), null,
        List.of(of(Supplier.class, Argument.ofTypeVariable(Object[].class, "T"))),
        List.of(of(Supplier.class, Argument.ofTypeVariable(Object[].class, "T")), Argument.OBJECT_ARGUMENT));
    public static final TypeHierarchy RAW_SUB = hierarchy(RawSub.class, List.of(), raw(ArrayList.class, variable("E")), List.of(),
        concat(List.of(raw(ArrayList.class, variable("E"))), concat(List.of(raw(AbstractList.class, variable("E")), raw(AbstractCollection.class, variable("E")),
            raw(Collection.class, variable("E")), raw(Iterable.class, variable("E")), raw(List.class, variable("E"))),
            List.of(Argument.of(RandomAccess.class), Argument.of(Cloneable.class), Argument.of(Serializable.class), Argument.OBJECT_ARGUMENT))));
}
