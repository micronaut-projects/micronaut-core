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

import io.micronaut.core.util.ArrayUtils;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
}
