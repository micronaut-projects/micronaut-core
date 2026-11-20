/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.reflect;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.type.WildcardArgument;
import io.micronaut.core.util.ArrayUtils;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Utility methods for dealing with generic types via reflection. Generally reflection is to be avoided in Micronaut. Hence,
 * this class is regarded as internal and used for only certain niche cases.
 *
 * <p>Every method here answers in erasure: a raw {@link Class}, with neither the nested type arguments nor the
 * type-use annotations of the declaration. A caller that wants those - an {@link io.micronaut.core.type.Argument}
 * shaped like the one the annotation processors generate - wants
 * {@code io.micronaut.reflection.ReflectionArguments#resolveGenericToArgument(Class, Class)} of the
 * {@code micronaut-reflection} module instead.</p>
 *
 * <p>The three methods that search a type hierarchy are deprecated, and not only for the erasure: they match the
 * super type by its raw type without substituting the type variables of the levels between, so an argument bound
 * at an intermediate generic type is not found at all. {@link #resolveTypeArguments(Class, Class)} is their
 * successor: it answers in erasure like they do, but substitutes the bindings of every level. The methods that
 * read one level - {@link #resolveTypeArguments(Type)}, {@link #resolveSuperGenericTypeArgument(Class)}, {@link
 * #resolveGenericTypeArgument(Field)} - do what they say and stay.</p>
 *
 * <p>The class stays as well: the compiler side of Micronaut resolves the type arguments of a
 * {@code TypeElementVisitor} through it, and a runtime module is not something the annotation processor
 * classpath can carry.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GenericTypeUtils {

    private static final Type[] EMPTY_TYPE_ARRAY = new Type[0];
    private static final Type[] OBJECT_BOUND = {Object.class};
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    private static final int MAX_ERASURE_DEPTH = 32;

    /**
     * Resolves a single generic type argument for the given field.
     *
     * @param field The field
     * @return The type argument or {@link Optional#empty()}
     */
    public static Optional<Class<?>> resolveGenericTypeArgument(Field field) {
        Type genericType = field != null ? field.getGenericType() : null;
        if (genericType instanceof ParameterizedType type) {
            Type[] typeArguments = type.getActualTypeArguments();
            if (typeArguments.length > 0) {
                Type typeArg = typeArguments[0];
                return resolveParameterizedTypeArgument(typeArg);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve all type arguments for the given interface from the given type. Also
     * searches superclasses.
     *
     * @param type          The type to resolve from
     * @param interfaceType The interface to resolve from
     * @return The type arguments to the interface
     * @deprecated The interface is matched by its raw type without substituting the type variables of the levels
     * between, so an argument bound at an intermediate generic type is not found: for a
     * {@code class Leaf extends Mid<String>} whose {@code Mid<T> implements Iface<T>}, this answers nothing
     * rather than {@code String}. Use {@code io.micronaut.reflection.ReflectionArguments#resolveGenericToArgument(Class, Class)}
     * of the {@code micronaut-reflection} module, which resolves the bindings of every level.
     */
    @Deprecated(since = "5.2", forRemoval = true)
    public static Class<?>[] resolveInterfaceTypeArguments(Class<?> type, Class<?> interfaceType) {
        Optional<Type> resolvedType = getAllGenericInterfaces(type)
                .stream()
                .filter(t -> {
                            if (t instanceof ParameterizedType pt) {
                                return pt.getRawType() == interfaceType;
                            }
                            return false;
                        }
                )
                .findFirst();
        return resolvedType.map(GenericTypeUtils::resolveTypeArguments)
                .orElse(ReflectionUtils.EMPTY_CLASS_ARRAY);
    }

    /**
     * Resolve all type arguments for the given super type from the given type.
     *
     * @param type      The type to resolve from
     * @param superTypeToResolve The suepr type to resolve from
     * @return The type arguments to the interface
     * @deprecated The super class is matched by its raw type without substituting the type variables of the
     * levels between, so an argument bound at an intermediate generic type is not found: for a
     * {@code class Baz extends Bar<String>} whose {@code Bar<T> extends Foo<T>}, this answers nothing rather
     * than {@code String}. Use {@code io.micronaut.reflection.ReflectionArguments#resolveGenericToArgument(Class, Class)}
     * of the {@code micronaut-reflection} module, which resolves the bindings of every level.
     */
    @Deprecated(since = "5.2", forRemoval = true)
    public static Class<?>[] resolveSuperTypeGenericArguments(Class<?> type, Class<?> superTypeToResolve) {
        Type supertype = type.getGenericSuperclass();
        Class<?> superclass = type.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            if (supertype instanceof ParameterizedType pt) {
                if (pt.getRawType() == superTypeToResolve) {
                    return resolveTypeArguments(supertype);
                }
            }

            supertype = superclass.getGenericSuperclass();
            superclass = superclass.getSuperclass();
        }
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    /**
     * Resolves a single generic type argument from the super class of the given type.
     *
     * @param type The type to resolve from
     * @return A single Class or null
     */
    public static Optional<Class<?>> resolveSuperGenericTypeArgument(Class<?> type) {
        try {
            Type genericSuperclass = type.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType) {
                return resolveSingleTypeArgument(genericSuperclass);
            }
            return Optional.empty();
        } catch (NoClassDefFoundError e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the type arguments for a generic type.
     *
     * @param genericType The generic type
     * @return The type arguments
     */
    public static Class<?>[] resolveTypeArguments(Type genericType) {
        Class<?>[] typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
        if (genericType instanceof ParameterizedType pt) {
            typeArguments = resolveParameterizedType(pt);
        }
        return typeArguments;
    }

    /**
     * Resolve the type arguments that {@code type} binds for {@code superType}, be that an interface or a
     * super class, substituting the type variables of every level between the two.
     *
     * <p>For a {@code class IntRepo extends NumberRepo<Integer>} whose
     * {@code abstract class NumberRepo<X extends Number> implements Repo<X>}, this answers {@code [Integer]}
     * for {@code Repo} - the same answer the annotation processors record at build time - where the deprecated
     * {@link #resolveInterfaceTypeArguments(Class, Class)} answers nothing because it matches {@code Repo} by
     * its raw type only.</p>
     *
     * <p>The answer is in erasure, and is empty when {@code superType} is not a super type of {@code type},
     * when it declares no type parameter, when {@code type} implements it raw, or when an argument stays an
     * unresolved type variable - a generic {@code class OpenRepo<T> implements Repo<T>} binds nothing. A caller
     * that needs the nested type arguments and the type-use annotations of the declaration wants
     * {@code io.micronaut.reflection.ReflectionArguments#resolveGenericToArgument(Class, Class)} of the
     * {@code micronaut-reflection} module instead.</p>
     *
     * @param type      The type to resolve from, {@code null} answering nothing
     * @param superType The super type, an interface or a class, to resolve the arguments of, {@code null}
     *                  answering nothing
     * @return The type arguments, never {@code null}
     * @since 5.2
     */
    public static Class<?>[] resolveTypeArguments(@Nullable Class<?> type, @Nullable Class<?> superType) {
        if (type == null || superType == null || !superType.isAssignableFrom(type)) {
            return ReflectionUtils.EMPTY_CLASS_ARRAY;
        }
        Type[] arguments = findTypeArguments(type, superType, Map.of());
        if (arguments == null || arguments.length == 0) {
            return ReflectionUtils.EMPTY_CLASS_ARRAY;
        }
        Class<?>[] erased = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Optional<Class<?>> resolved = resolveParameterizedTypeArgument(arguments[i]);
            if (resolved.isEmpty()) {
                return ReflectionUtils.EMPTY_CLASS_ARRAY;
            }
            erased[i] = resolved.get();
        }
        return erased;
    }

    /**
     * Walk the type hierarchy of {@code declaredType} for {@code superType}, carrying the bindings the levels
     * above have made for the type variables of this one.
     *
     * @param declaredType The type as it is declared at this level, so a {@link ParameterizedType} when the
     *                     level below parameterized it
     * @param superType    The super type being searched for
     * @param bindings     The bindings in scope for the type variables of {@code declaredType}
     * @return The arguments {@code superType} is bound to, or {@code null} when it is not reached this way
     */
    private static Type @Nullable [] findTypeArguments(Type declaredType, Class<?> superType, Map<TypeVariable<?>, Type> bindings) {
        Class<?> raw = erase(declaredType);
        if (raw == null || !superType.isAssignableFrom(raw)) {
            return null;
        }
        TypeVariable<?>[] variables = raw.getTypeParameters();
        Map<TypeVariable<?>, Type> resolved = bindings;
        Type[] arguments = EMPTY_TYPE_ARRAY;
        if (declaredType instanceof ParameterizedType pt) {
            Type[] actual = pt.getActualTypeArguments();
            if (actual.length == variables.length) {
                arguments = new Type[actual.length];
                resolved = new HashMap<>(variables.length);
                for (int i = 0; i < actual.length; i++) {
                    arguments[i] = substitute(actual[i], bindings);
                    resolved.put(variables[i], arguments[i]);
                }
            }
        }
        if (raw == superType) {
            return arguments;
        }
        Type genericSuperclass = raw.getGenericSuperclass();
        if (genericSuperclass != null) {
            Type[] found = findTypeArguments(genericSuperclass, superType, resolved);
            if (found != null) {
                return found;
            }
        }
        for (Type genericInterface : raw.getGenericInterfaces()) {
            Type[] found = findTypeArguments(genericInterface, superType, resolved);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Replace a type variable by what the level below bound it to, following a chain of variables to its end.
     * Anything else, a parameterized type included, is answered as it is: only the erasure of the argument is
     * read in the end, and that a substitution inside it cannot change.
     */
    private static Type substitute(Type type, Map<TypeVariable<?>, Type> bindings) {
        Type current = type;
        Set<TypeVariable<?>> seen = null;
        while (current instanceof TypeVariable<?> variable) {
            if (seen != null && !seen.add(variable)) {
                return current;
            }
            Type bound = bindings.get(variable);
            if (bound == null) {
                return current;
            }
            if (seen == null) {
                seen = new HashSet<>(4);
                seen.add(variable);
            }
            current = bound;
        }
        return current;
    }

    /**
     * The erasure of a type, or {@code null} when it has none - an unresolved type variable or wildcard.
     */
    @Nullable
    private static Class<?> erase(Type type) {
        if (type instanceof Class<?> cls) {
            return cls;
        }
        if (type instanceof ParameterizedType pt) {
            return erase(pt.getRawType());
        }
        return null;
    }

    /**
     * Resolves a single type argument from the given interface of the given class. Also
     * searches superclasses.
     *
     * @param type          The type to resolve from
     * @param interfaceType The interface to resolve for
     * @return The class or null
     * @deprecated Carries the same limitation as {@link #resolveInterfaceTypeArguments(Class, Class)}: an
     * argument bound at an intermediate generic type is not found. Use
     * {@code io.micronaut.reflection.ReflectionArguments#resolveGenericToArgument(Class, Class)} of the
     * {@code micronaut-reflection} module and read its first type parameter.
     */
    @Deprecated(since = "5.2", forRemoval = true)
    public static Optional<Class<?>> resolveInterfaceTypeArgument(Class<?> type, Class<?> interfaceType) {
        Type[] genericInterfaces = type.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType pt) {
                if (pt.getRawType() == interfaceType) {
                    return resolveSingleTypeArgument(genericInterface);
                }
            }
        }
        Class<?> superClass = type.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return resolveInterfaceTypeArgument(superClass, interfaceType);
        }
        return Optional.empty();
    }

    /**
     * Resolve a single type from the given generic type.
     *
     * @param genericType The generic type
     * @return An {@link Optional} of the type
     */
        private static Optional<Class<?>> resolveSingleTypeArgument(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                Type actualTypeArgument = actualTypeArguments[0];
                return resolveParameterizedTypeArgument(actualTypeArgument);
            }
        }
        return Optional.empty();
    }

    /**
     * @param actualTypeArgument The actual type argument
     * @return An optional with the resolved parameterized class
     */
    private static Optional<Class<?>> resolveParameterizedTypeArgument(Type actualTypeArgument) {
        if (actualTypeArgument instanceof Class class1) {
            return Optional.of(class1);
        }
        if (actualTypeArgument instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType instanceof Class class1) {
                return Optional.of(class1);
            }
        }
        return Optional.empty();
    }

    /**
     * @param aClass A class
     * @return All generic interfaces
     */
    private static Set<Type> getAllGenericInterfaces(Class<?> aClass) {
        Set<Type> interfaces = new LinkedHashSet<>();
        return populateInterfaces(aClass, interfaces);
    }

    /**
     * @param aClass     Some class
     * @param interfaces The interfaces
     * @return A set of interfaces
     */
    @SuppressWarnings("Duplicates")
    private static Set<Type> populateInterfaces(Class<?> aClass, Set<Type> interfaces) {
        Type[] theInterfaces = aClass.getGenericInterfaces();
        interfaces.addAll(Arrays.asList(theInterfaces));
        for (Type theInterface : theInterfaces) {
            if (theInterface instanceof Class i) {
                if (ArrayUtils.isNotEmpty(i.getGenericInterfaces())) {
                    populateInterfaces(i, interfaces);
                }
            }
        }
        if (!aClass.isInterface()) {
            Class<?> superclass = aClass.getSuperclass();
            while (superclass != null) {
                populateInterfaces(superclass, interfaces);
                superclass = superclass.getSuperclass();
            }
        }
        return interfaces;
    }

    private static Class<?>[] resolveParameterizedType(ParameterizedType pt) {
        Class<?>[] typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
        Type[] actualTypeArguments = pt.getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length > 0) {
            typeArguments = new Class<?>[actualTypeArguments.length];
            for (int i = 0; i < actualTypeArguments.length; i++) {
                Type actualTypeArgument = actualTypeArguments[i];
                Optional<Class<?>> opt = resolveParameterizedTypeArgument(actualTypeArgument);
                if (opt.isPresent()) {
                    typeArguments[i] = opt.get();
                } else {
                    typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
                    break;
                }
            }
        }
        return typeArguments;
    }

    /**
     * A {@link ParameterizedType} of the given class over the given arguments, owned by the class that declares
     * it, as the JDK makes one: equal to a type the JDK reflects with the same raw type, owner and arguments, with
     * its hash code and its name. Building it reads no class.
     *
     * @param rawType   The raw class
     * @param arguments The type arguments, not checked against the variables the class declares
     * @return The parameterized type
     * @since 5.3.0
     */
    public static ParameterizedType parameterizedType(Class<?> rawType, Type... arguments) {
        Objects.requireNonNull(rawType, "rawType");
        return new ParameterizedTypeValue(rawType.getDeclaringClass(), rawType, copyOf(arguments));
    }

    /**
     * A {@link TypeVariable} with the given name and bounds, without a declaration: its
     * {@link TypeVariable#getGenericDeclaration()} throws {@link UnsupportedOperationException}. It is equal to a
     * variable made here or rendered by {@link TypeInformation#asType()} with the same name and bounds that erase
     * to the same classes, and never to one the JDK reflects, which the JDK compares by declaration.
     *
     * @param name   The name
     * @param bounds The bounds, empty for {@code Object}
     * @return The variable
     * @since 5.3.0
     */
    @Internal
    public static TypeVariable<GenericDeclaration> typeVariable(String name, Type... bounds) {
        TypeVariableValue variable = new TypeVariableValue(Objects.requireNonNull(name, "name"));
        variable.bounds = bounds.length == 0 ? OBJECT_BOUND : copyOf(bounds);
        return variable;
    }

    /**
     * The type {@link TypeInformation#asType()} renders: see there.
     *
     * @param type The type information
     * @return The type
     * @since 5.3.0
     */
    @Internal
    public static Type toType(TypeInformation<?> type) {
        return toType(type, Map.of());
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
            // the owner is the declaring class, whose name a member class's name begins with
            if (actualTypeArguments.length == 0) {
                return rawType.getTypeName();
            }
            return rawType.getTypeName() + '<' + joined(actualTypeArguments, ", ") + '>';
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
