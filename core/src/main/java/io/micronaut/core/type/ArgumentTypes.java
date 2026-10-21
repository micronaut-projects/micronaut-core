/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.type;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Builds the {@link Type}s the {@code java.lang.reflect} APIs are written in, without reading a class: the
 * parameterized types, wildcards, generic arrays and type variables {@link TypeInformation#asType()} renders an
 * argument as, made from other types.
 *
 * <p>The types made here are equal to the ones the JDK reflects wherever the JDK defines the equality of its
 * type with one of another implementation: a {@link ParameterizedType}, a {@link WildcardType} and a
 * {@link GenericArrayType} are equal to a JDK one with the same raw type, owner, arguments, bounds and component,
 * have its hash code and print as it does. The JDK equals a {@link TypeVariable} of its own making only, to one
 * with the same declaration, so a variable made here - which has no declaration - is never equal to a reflected
 * one, and neither is a type that has one among its arguments.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ArgumentTypes {

    private static final Type[] OBJECT_BOUND = {Object.class};
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    private static final int MAX_ERASURE_DEPTH = 32;

    private ArgumentTypes() {
    }

    /**
     * The type {@link TypeInformation#asType()} renders.
     *
     * @param type The type information
     * @return The type
     */
    static Type toType(TypeInformation<?> type) {
        return toType(type, Map.of());
    }

    /**
     * A {@link ParameterizedType} of the given class over the given arguments, owned by the class that declares
     * it, as the JDK makes one.
     *
     * @param rawType   The raw class
     * @param arguments The type arguments, not checked against the variables the class declares
     * @return The parameterized type
     */
    public static ParameterizedType parameterizedType(Class<?> rawType, Type... arguments) {
        return parameterizedTypeWithOwner(null, rawType, arguments);
    }

    /**
     * A {@link ParameterizedType} of the given class over the given arguments, owned by the given type: the
     * parameterized {@code Outer<X>} of an {@code Outer<X>.Inner<Y>}.
     *
     * @param ownerType The owner, {@code null} for the class that declares the raw class
     * @param rawType   The raw class
     * @param arguments The type arguments, not checked against the variables the class declares
     * @return The parameterized type
     */
    public static ParameterizedType parameterizedTypeWithOwner(@Nullable Type ownerType, Class<?> rawType, Type... arguments) {
        Objects.requireNonNull(rawType, "rawType");
        return new ParameterizedTypeValue(ownerType == null ? rawType.getDeclaringClass() : ownerType, rawType, copyOf(arguments));
    }

    /**
     * A {@link WildcardType} with the given bounds.
     *
     * @param upperBounds The upper bounds, empty for {@code Object}
     * @param lowerBounds The lower bounds, empty for none
     * @return The wildcard
     */
    public static WildcardType wildcardType(Type[] upperBounds, Type[] lowerBounds) {
        return new WildcardTypeValue(upperBounds.length == 0 ? OBJECT_BOUND : copyOf(upperBounds), copyOf(lowerBounds));
    }

    /**
     * A {@link GenericArrayType} of the given component.
     *
     * @param componentType The component type
     * @return The array type
     */
    public static GenericArrayType genericArrayType(Type componentType) {
        return new GenericArrayTypeValue(Objects.requireNonNull(componentType, "componentType"));
    }

    /**
     * A {@link TypeVariable} with the given name and bounds. It has no declaration: see
     * {@link TypeVariable#getGenericDeclaration()} on the returned variable.
     *
     * @param name   The name
     * @param bounds The bounds, empty for {@code Object}
     * @return The variable
     */
    public static TypeVariable<GenericDeclaration> typeVariable(String name, Type... bounds) {
        TypeVariableValue variable = new TypeVariableValue(Objects.requireNonNull(name, "name"));
        variable.bounds = bounds.length == 0 ? OBJECT_BOUND : copyOf(bounds);
        return variable;
    }

    /**
     * Renders an argument as a type inside the bounds of the given variables.
     */
    private static Type toType(TypeInformation<?> argument, Map<String, TypeVariableValue> bounding) {
        if (argument instanceof WildcardArgument<?> wildcard) {
            Type[] upperBounds = toTypes(wildcard.getUpperBounds(), bounding);
            return new WildcardTypeValue(upperBounds.length == 0 ? OBJECT_BOUND : upperBounds,
                toTypes(wildcard.getLowerBounds(), bounding));
        }
        Class<?> type = argument.getType();
        if (argument instanceof GenericPlaceholder<?> placeholder && !placeholder.isResolved()) {
            String name = placeholder.getVariableName();
            TypeVariableValue variable = bounding.get(name);
            if (variable == null) {
                // created before its bounds, which may name it
                variable = new TypeVariableValue(name);
                Map<String, TypeVariableValue> within = new HashMap<>(bounding);
                within.put(name, variable);
                Type[] bounds = toTypes(placeholder.getBounds(), within);
                variable.bounds = bounds.length == 0 ? OBJECT_BOUND : bounds;
            }
            // the placeholder of an array of a variable is an array of the variable
            return arrayOf(variable, type);
        }
        Argument<?>[] typeParameters = argument.getTypeParameters();
        // a raw argument keeps the type arguments the type declares, and renders back as the bare type it
        // was written as, not as one parameterized by the variables it kept
        if (typeParameters.length == 0 || argument instanceof Argument<?> declared && declared.isRawType()) {
            return type;
        }
        Type[] arguments = new Type[typeParameters.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = toType(typeParameters[i], bounding);
        }
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        // an array has the type arguments of its component
        return arrayOf(new ParameterizedTypeValue(component.getDeclaringClass(), component, arguments), type);
    }

    private static Type[] toTypes(List<Argument<?>> arguments, Map<String, TypeVariableValue> bounding) {
        Type[] types = new Type[arguments.size()];
        for (int i = 0; i < types.length; i++) {
            types[i] = toType(arguments.get(i), bounding);
        }
        return types;
    }

    /**
     * The component wrapped in as many generic array levels as the class has dimensions.
     */
    private static Type arrayOf(Type component, Class<?> type) {
        Type result = component;
        for (Class<?> level = type; level.isArray(); level = level.getComponentType()) {
            result = new GenericArrayTypeValue(result);
        }
        return result;
    }

    private static Type[] copyOf(Type[] types) {
        Type[] copy = types.clone();
        for (Type type : copy) {
            Objects.requireNonNull(type, "A type cannot be null");
        }
        return copy;
    }

    private static String joined(Type[] types, String delimiter) {
        StringJoiner joiner = new StringJoiner(delimiter);
        for (Type type : types) {
            joiner.add(type.getTypeName());
        }
        return joiner.toString();
    }

    /**
     * The class a type erases to, as a component and a number of array dimensions, read without making the
     * array class: the first bound of a variable or of a wildcard, all the way up.
     */
    private static Erasure erasureOf(Type type) {
        Type current = type;
        int dimensions = 0;
        for (int depth = 0; depth < MAX_ERASURE_DEPTH; depth++) {
            if (current instanceof Class<?> aClass) {
                while (aClass.isArray()) {
                    dimensions++;
                    aClass = aClass.getComponentType();
                }
                return new Erasure(aClass, dimensions);
            } else if (current instanceof ParameterizedType parameterized) {
                current = parameterized.getRawType();
            } else if (current instanceof GenericArrayType array) {
                dimensions++;
                current = array.getGenericComponentType();
            } else if (current instanceof TypeVariable<?> variable) {
                Type[] bounds = variable.getBounds();
                current = bounds.length == 0 ? Object.class : bounds[0];
            } else if (current instanceof WildcardType wildcard) {
                Type[] bounds = wildcard.getUpperBounds();
                current = bounds.length == 0 ? Object.class : bounds[0];
            } else {
                break;
            }
        }
        return new Erasure(Object.class, dimensions);
    }

    /**
     * A {@link ParameterizedType} rendered from an argument, equal to the one the JDK reflects for the same
     * owner, raw type and arguments.
     */
    private static final class ParameterizedTypeValue implements ParameterizedType {

        private final @Nullable Type ownerType;
        private final Class<?> rawType;
        private final Type[] actualTypeArguments;

        ParameterizedTypeValue(@Nullable Type ownerType, Class<?> rawType, Type[] actualTypeArguments) {
            this.ownerType = ownerType;
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public @Nullable Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof ParameterizedType other
                && Objects.equals(ownerType, other.getOwnerType())
                && rawType.equals(other.getRawType())
                && Arrays.equals(actualTypeArguments, other.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(ownerType) ^ rawType.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (ownerType == null) {
                builder.append(rawType.getName());
            } else {
                builder.append(ownerType.getTypeName()).append('$');
                Erasure owner = erasureOf(ownerType);
                String prefix = owner.component().getName() + '$';
                String name = rawType.getName();
                // the name of a member class is the name of its owner, a dollar and its simple name; the simple
                // name is not asked of the class, which would read its reflection data
                builder.append(owner.dimensions() == 0 && name.startsWith(prefix)
                    ? name.substring(prefix.length())
                    : name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1));
            }
            if (actualTypeArguments.length > 0) {
                builder.append('<').append(joined(actualTypeArguments, ", ")).append('>');
            }
            return builder.toString();
        }
    }

    /**
     * A {@link WildcardType}, equal to the one the JDK reflects for the same bounds.
     */
    private static final class WildcardTypeValue implements WildcardType {

        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        WildcardTypeValue(Type[] upperBounds, Type[] lowerBounds) {
            this.upperBounds = upperBounds;
            this.lowerBounds = lowerBounds;
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof WildcardType other
                && Arrays.equals(lowerBounds, other.getLowerBounds())
                && Arrays.equals(upperBounds, other.getUpperBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(lowerBounds) ^ Arrays.hashCode(upperBounds);
        }

        @Override
        public String toString() {
            if (lowerBounds.length > 0) {
                return "? super " + joined(lowerBounds, " & ");
            }
            if (upperBounds.length == 0 || upperBounds[0].equals(Object.class)) {
                return "?";
            }
            return "? extends " + joined(upperBounds, " & ");
        }
    }

    /**
     * A {@link GenericArrayType}, equal to the one the JDK reflects for the same component.
     */
    private static final class GenericArrayTypeValue implements GenericArrayType {

        private final Type component;

        GenericArrayTypeValue(Type component) {
            this.component = component;
        }

        @Override
        public Type getGenericComponentType() {
            return component;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GenericArrayType other && component.equals(other.getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return component.hashCode();
        }

        @Override
        public String toString() {
            return component.getTypeName() + "[]";
        }
    }

    /**
     * A {@link TypeVariable} known by its name and its bounds, without the declaration it belongs to. Its bounds
     * are set once it is made, since a bound may name it. Two such variables are equal when they have the same
     * name and bounds that erase to the same classes, so that the comparison of a variable bounded by itself
     * ends; a variable reflected by the JDK is never equal to one, since the JDK compares declarations.
     */
    private static final class TypeVariableValue implements TypeVariable<GenericDeclaration> {

        private final String name;
        private Type[] bounds = OBJECT_BOUND;

        TypeVariableValue(String name) {
            this.name = name;
        }

        @Override
        public Type[] getBounds() {
            return bounds.clone();
        }

        /**
         * The declaration of the variable is not known: the variable is made from an argument, or from a name and
         * bounds, without reading the class or the method that declares it.
         *
         * @return Never
         * @throws UnsupportedOperationException Always
         */
        @Override
        public GenericDeclaration getGenericDeclaration() {
            throw new UnsupportedOperationException("The declaration of the type variable " + name + " is not known");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            AnnotatedType[] annotated = new AnnotatedType[bounds.length];
            for (int i = 0; i < bounds.length; i++) {
                annotated[i] = new UnannotatedType(bounds[i]);
            }
            return annotated;
        }

        @Override
        public <T extends Annotation> @Nullable T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return NO_ANNOTATIONS;
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return NO_ANNOTATIONS;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TypeVariableValue other) || !name.equals(other.name) || bounds.length != other.bounds.length) {
                return false;
            }
            for (int i = 0; i < bounds.length; i++) {
                if (!erasureOf(bounds[i]).equals(erasureOf(other.bounds[i]))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A bound of a {@link TypeVariableValue}, which carries no annotation.
     */
    private static final class UnannotatedType implements AnnotatedType {

        private final Type type;

        UnannotatedType(Type type) {
            this.type = type;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public <T extends Annotation> @Nullable T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return NO_ANNOTATIONS;
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return NO_ANNOTATIONS;
        }
    }

    /**
     * The erasure of a type: its component class and its array dimensions.
     *
     * @param component  The component class
     * @param dimensions The array dimensions
     */
    private record Erasure(Class<?> component, int dimensions) {
    }
}
