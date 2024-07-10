/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import org.slf4j.Logger;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A {@link Qualifier} that filters beans according to the type arguments.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 4.6
 */
@Internal
public final class MatchArgumentQualifier<T> implements Qualifier<T> {

    private static final Logger LOG = ClassUtils.getLogger(MatchArgumentQualifier.class);
    private final Argument<?> argument;
    private final Argument<?> superArgument;

    private MatchArgumentQualifier(Argument<?> argument, Argument<?> superArgument) {
        this.argument = argument;
        this.superArgument = superArgument;
    }

    public static <T> MatchArgumentQualifier<T> ofArgument(Argument<?> argument) {
        return new MatchArgumentQualifier<>(
            argument,
            null
        );
    }

    /**
     * Finds matches of a type with a generic of a higher kind types (types that extend the type or are equal to it).
     * The generic argument is assignable from the candidate generic type.
     * Use-cases are generic deserializers, readers.
     * Java example:
     * MyReader[ArrayList[String]] candidate = ...;
     * MyReader[? extends List[String]] aMatch = candidate;
     *
     * @param beanType        The type of the beans
     * @param genericArgument The generic argument of the bean type
     * @param <T>             The bean type
     * @return The qualifier
     */
    public static <T> MatchArgumentQualifier<T> ofHigherTypes(Class<T> beanType, Argument<?> genericArgument) {
        Argument<?> superArgument = Argument.ofTypeVariable(genericArgument.getType(), null, genericArgument.getAnnotationMetadata(), genericArgument.getTypeParameters());
        return new MatchArgumentQualifier<>(
            Argument.of(beanType, superArgument),
            superArgument
        );
    }

    /**
     * Finds matches of a type with a generic of a lower kind types (types that is a super type or are equal to it).
     * The candidate generic type is assignable from the generic argument.
     * Use-cases are generic serializers, writers.
     * Java example:
     * MyWriter[String]  candidate = ...;
     * MyWriter[? super CharSequence] aMatch = candidate;
     *
     * @param beanType        The type of the beans
     * @param genericArgument The generic argument of the bean type
     * @param <T>             The bean type
     * @return The qualifier
     */
    public static <T> MatchArgumentQualifier<T> ofLowerTypes(Class<T> beanType, Argument<?> genericArgument) {
        return new MatchArgumentQualifier<>(
            Argument.of(beanType, Argument.ofTypeVariable(genericArgument.getType(), null, genericArgument.getAnnotationMetadata(), genericArgument.getTypeParameters())),
            null
        );
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return filter(beanType, candidates.toList()).stream();
    }

    @Override
    public boolean doesQualify(Class<T> beanType, Collection<? extends BeanType<T>> candidates) {
        return !filter(beanType, candidates).isEmpty();
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public <BT extends BeanType<T>> Collection<BT> filter(Class<T> beanType, Collection<BT> candidates) {
        return filterArgumentTypeParameters(argument, candidates, null);
    }

    private boolean matchesArgumentTypeParameters(Argument<?> argument, Argument<?> candidateArgument) {
        Argument<?>[] argumentTypeParameters = argument.getTypeParameters();
        Argument<?>[] candidateTypeParameters = candidateArgument.getTypeParameters();
        if (argumentTypeParameters.length != candidateTypeParameters.length) {
            if (argumentTypeParameters.length == 0) {
                for (Argument<?> candidateTypeParameter : candidateTypeParameters) {
                    if (!candidateTypeParameter.getType().equals(Object.class)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
        for (int i = 0; i < candidateTypeParameters.length; i++) {
            Argument<?> typeParameter = argumentTypeParameters[i];
            Argument<?> candidateTypeParameter = candidateTypeParameters[i];
            if (!doesMatch(candidateTypeParameter, candidateTypeParameter.getType(), typeParameter, typeParameter.getType())) {
                return false;
            }
        }
        return true;
    }

    private <BT extends BeanType<T>> Collection<BT> filterArgumentTypeParameters(Argument<?> argument,
                                                                                 Collection<BT> result,
                                                                                 Function<BT, Argument<?>> typeArgumentExtractor) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        for (int i = 0; i < typeParameters.length; i++) {
            int finalI = i;
            Argument<?> typeParameter = typeParameters[i];
            Function<BT, Argument<?>> getCandidateArgumentFunction = bd -> {
                if (typeArgumentExtractor == null) {
                    if (bd instanceof BeanDefinition<?> beanDefinition) {
                        List<Argument<?>> typeArguments = beanDefinition.getTypeArguments(argument.getType());
                        if (finalI < typeArguments.size()) {
                            return typeArguments.get(finalI);
                        }
                    }
                    return null;
                }
                Argument<?> apply = typeArgumentExtractor.apply(bd);
                Argument<?>[] candidateTypeParameters = apply.getTypeParameters();
                if (finalI < candidateTypeParameters.length) {
                    return candidateTypeParameters[finalI];
                }
                return Argument.OBJECT_ARGUMENT;
            };
            result = filterMatching(typeParameter, result, getCandidateArgumentFunction);
            result = filterArgumentTypeParameters(typeParameter, result, getCandidateArgumentFunction);
        }
        return result;
    }

    private <BT extends BeanType<T>> List<BT> filterMatching(Argument<?> argument,
                                                             Collection<BT> candidates,
                                                             Function<BT, Argument<?>> typeArgumentExtractor) {
        List<BT> selectedDirect = null;
        boolean directMatch = false;
        List<Map.Entry<Class<?>, List<BT>>> closestMatches = null;
        candidatesForLoop:
        for (BT candidate : candidates) {

            Class<?> argumentTypeClass = argument.getType();
            if (argumentTypeClass.isPrimitive()) {
                argumentTypeClass = ReflectionUtils.getWrapperType(argumentTypeClass);
            }
            Argument<?> candidateArgument = typeArgumentExtractor.apply(candidate);
            if (candidateArgument == null) {
                continue;
            }
            Class<?> candidateType = candidateArgument.getType();
            if (argumentTypeClass.equals(candidateType)) {
                if (!matchesArgumentTypeParameters(argument, candidateArgument)) {
                    // Eliminate a candidate that doesn't fully match
                    reject(argument, candidate);
                    continue;
                }
                if (!directMatch) {
                    selectedDirect = new ArrayList<>(3);
                    closestMatches = null;
                    directMatch = true;
                }
                selectedDirect.add(candidate);
                continue;
            }
            if (directMatch) {
                // After direct match found ignore all non-direct
                reject(argument, candidate);
                continue;
            }
            if (!doesMatch(candidateArgument, candidateType, argument, argumentTypeClass)) {
                reject(argument, candidate);
                continue;
            }

            if (closestMatches != null) {
                // Compare the candidate with previous matches, possibly eliminating some of them
                for (Iterator<Map.Entry<Class<?>, List<BT>>> iterator = closestMatches.iterator(); iterator.hasNext(); ) {
                    Map.Entry<Class<?>, List<BT>> e = iterator.next();
                    Class<?> closestMatch = e.getKey();
                    List<BT> selected = e.getValue();
                    if (closestMatch.equals(candidateType)) {
                        // Same type as found before - also select this type
                        selected.add(candidate);
                        continue candidatesForLoop;
                    } else if (closestMatch.isAssignableFrom(candidateType)) {
                        // We found more close type - disregard previous selection
                        iterator.remove();
                        if (LOG.isTraceEnabled()) {
                            for (BT bt : selected) {
                                reject(argument, bt);
                            }
                        }
                        ArrayList<BT> newSelected = new ArrayList<>();
                        newSelected.add(candidate);
                        closestMatches.add(new AbstractMap.SimpleEntry<>(candidateType, newSelected));
                        continue candidatesForLoop;
                    } else if (candidateType.isAssignableFrom(closestMatch)) {
                        // Previous match is much closer
                        reject(argument, candidate);
                        continue candidatesForLoop;
                    }
                }
            } else {
                closestMatches = new ArrayList<>();
            }
            ArrayList<BT> newSelected = new ArrayList<>();
            newSelected.add(candidate);
            closestMatches.add(new AbstractMap.SimpleEntry<>(candidateType, newSelected));
        }
        if (directMatch) {
            return selectedDirect;
        }
        if (closestMatches != null) {
            if (closestMatches.size() == 1) {
                return closestMatches.iterator().next().getValue();
            }
            List<BT> result = new ArrayList<>();
            for (Map.Entry<Class<?>, List<BT>> match : closestMatches) {
                result.addAll(match.getValue());
            }
            return result;
        }
        return List.of();
    }

    private boolean doesMatch(Argument<?> candidateArgument,
                              Class<?> candidateType,
                              Argument<?> argument,
                              Class<?> argumentType) {
        if (candidateType.equals(argumentType)) {
            return true;
        }
        if (candidateType.equals(Enum.class)) {
            // Avoid checking generic types for enums
            return candidateType.isAssignableFrom(argumentType);
        }
        if (argument.isTypeVariable()) {
            // Is compatible?
            if (argument == superArgument) {
                if (candidateArgument.isTypeVariable() && candidateType.isAssignableFrom(argumentType)) {
                    // Defined a type variable <T extends LowerType>
                    return true;
                }
                return argumentType.isAssignableFrom(candidateType);
            } else {
                return candidateType.isAssignableFrom(argumentType);
            }
        }
        if (candidateType.equals(Object.class)) {
            return true;
        }
        if (!candidateArgument.isTypeVariable()) {
            // Defined as a direct type
            return false;
        }
        // Is compatible?
        return candidateType.isAssignableFrom(argumentType);
    }

    private static <BT extends BeanType<?>> void reject(Argument<?> argument, BT candidate) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Bean candidate {} is not compatible with an argument {}", candidate, argument);
        }
    }

    @Override
    public String toString() {
        return "Matches [" + argument.toString() + "]";
    }
}
