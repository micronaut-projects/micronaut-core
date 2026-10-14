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

    public static TypeHierarchy hierarchy(Class<?> type, List<Argument<?>> variables, Argument<?> superclass, List<Argument<?>> interfaces) {
        return new GeneratedTypeHierarchy(type, variables.toArray(Argument.ZERO_ARGUMENTS), superclass, interfaces.toArray(Argument.ZERO_ARGUMENTS)) {
        };
    }

    public static final TypeHierarchy PAYLOAD = hierarchy(Payload.class, List.of(variable("T", Number.class)),
        of(ArrayList.class, variable("T", Number.class)),
        List.of(of(Supplier.class, of(List.class, variable("T", Number.class)))));
    public static final TypeHierarchy ARRAY_LIST = hierarchy(ArrayList.class, List.of(variable("E")),
        of(AbstractList.class, variable("E")),
        List.of(of(List.class, variable("E")), Argument.of(RandomAccess.class), Argument.of(Cloneable.class), Argument.of(Serializable.class)));
    public static final TypeHierarchy ABSTRACT_LIST = hierarchy(AbstractList.class, List.of(variable("E")),
        of(AbstractCollection.class, variable("E")), List.of(of(List.class, variable("E"))));
    public static final TypeHierarchy LIST = hierarchy(List.class, List.of(variable("E")), null, List.of(of(Collection.class, variable("E"))));
    public static final TypeHierarchy COLLECTION = hierarchy(Collection.class, List.of(variable("E")), null, List.of(of(Iterable.class, variable("E"))));
    public static final TypeHierarchy TOP = hierarchy(Top.class, List.of(variable("T")), null, List.of());
    public static final TypeHierarchy LEFT = hierarchy(Left.class, List.of(variable("T")), null, List.of(of(Top.class, variable("T"))));
    public static final TypeHierarchy RIGHT = hierarchy(Right.class, List.of(variable("T")), null, List.of(of(Top.class, variable("T"))));
    public static final TypeHierarchy DIAMOND = hierarchy(Diamond.class, List.of(), null, List.of(of(Left.class, Argument.STRING), of(Right.class, Argument.STRING)));
    public static final TypeHierarchy ARRAYS = hierarchy(Arrays.class, List.of(variable("T")), null,
        List.of(of(Supplier.class, Argument.ofTypeVariable(Object[].class, "T"))));
    public static final TypeHierarchy RAW_SUB = hierarchy(RawSub.class, List.of(), raw(ArrayList.class, variable("E")), List.of());
}
