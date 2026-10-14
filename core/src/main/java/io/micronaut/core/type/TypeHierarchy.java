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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The generic hierarchy of a type as it was declared: its type variables with their bounds, its generic superclass
 * and its generic interfaces, with their type arguments written in terms of those variables.
 *
 * <p>A hierarchy is recorded at compile time, by the Micronaut processors, for the types a compilation asks for with
 * {@link io.micronaut.core.annotation.RecordTypeHierarchy} or through
 * {@code VisitorContext.recordTypeHierarchy(ClassElement, Element...)}, and found at run time with
 * {@link TypeHierarchies#find(Class)}. It is read from the record only: no signature of the class is read
 * reflectively. Unlike a bean definition or a bean introspection, a hierarchy can be recorded for any class a
 * compilation sees, a class of a library or of the JDK included, and describes nothing but the declaration of the
 * type.</p>
 *
 * <p>The arguments keep what the declaration says: a type variable is a {@link GenericPlaceholder} with its bounds, a
 * wildcard is a {@link WildcardArgument}, a type used without its type arguments is a raw type
 * ({@link Argument#isRawType()}), and an array of a variable is a placeholder whose type is the array of the
 * erasure of the variable. The supertypes are the direct ones; {@link #getTypeClosure(Argument)} walks the
 * hierarchies of the supertypes to give the whole closure of a parameterization of the type.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface TypeHierarchy {

    /**
     * @return The type the hierarchy is of
     */
    Class<?> getType();

    /**
     * The type variables the type declares, in order, each a {@link GenericPlaceholder} named as the variable, whose
     * type is the erasure of the variable and whose {@link GenericPlaceholder#getBounds() bounds} are those declared.
     *
     * @return The type variables, empty for a type that declares none
     */
    List<Argument<?>> getTypeVariables();

    /**
     * The generic superclass, with its type arguments as declared, which can name the
     * {@link #getTypeVariables() type variables}. A raw {@code extends} is a raw argument.
     *
     * @return The superclass, or empty for an interface and for a type that extends {@link Object}
     */
    Optional<Argument<?>> getGenericSuperclass();

    /**
     * The generic interfaces the type implements, or an interface extends, with their type arguments as declared.
     *
     * @return The interfaces, in the order declared
     */
    List<Argument<?>> getGenericInterfaces();

    /**
     * The type parameterized by its own type variables, {@code Foo<T>} for {@code class Foo<T>}, or the plain type
     * when it declares none.
     *
     * @return The declaration as an argument
     */
    default Argument<?> getDeclaration() {
        List<Argument<?>> variables = getTypeVariables();
        if (variables.isEmpty()) {
            return Argument.of(getType());
        }
        return Argument.of(getType(), (String) null, variables.toArray(Argument.ZERO_ARGUMENTS));
    }

    /**
     * The type closure of a parameterization of the type: the parameterization itself, then every supertype with the
     * actual type arguments substituted along the path, each distinct type once, and {@link Object} last. The
     * hierarchies of the supertypes are found with {@link TypeHierarchies#find(Class)}; a supertype that has no record
     * ends its branch of the walk and is still part of the closure, without its own supertypes.
     *
     * @param parameterization The type with its type arguments, or raw
     * @return The closure
     * @see #getTypeClosure(Argument, Function)
     */
    default List<Argument<?>> getTypeClosure(Argument<?> parameterization) {
        return getTypeClosure(parameterization, TypeHierarchies::find);
    }

    /**
     * The type closure of a parameterization of the type, as {@link #getTypeClosure(Argument)}, with the hierarchies
     * of the supertypes found by the given function, which lets a caller add a source of hierarchies of its own, such
     * as one that reads the signatures of a class reflectively for the types no compilation recorded.
     *
     * <p>The substitution follows the Java Language Specification. A type variable is replaced by the actual type
     * argument at its position, an array of a variable by an array of that argument, and a wildcard argument is kept
     * as a wildcard. A raw parameterization of a generic type, one with no type arguments or
     * {@link Argument#isRawType() marked raw}, erases every supertype (JLS 4.8), and so does a raw {@code extends}
     * or {@code implements} along the path. A variable the hierarchy of a supertype names but the parameterization
     * does not resolve is kept as it is.</p>
     *
     * @param parameterization The type with its type arguments, or raw
     * @param hierarchies      The hierarchies of the supertypes, by type
     * @return The closure
     * @throws IllegalArgumentException if the parameterization is not of {@link #getType() the type}
     */
    default List<Argument<?>> getTypeClosure(Argument<?> parameterization, Function<Class<?>, Optional<TypeHierarchy>> hierarchies) {
        return TypeClosure.of(this, parameterization, hierarchies);
    }
}
