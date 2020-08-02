/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.qualifiers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StreamUtils;
import io.micronaut.inject.BeanType;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link io.micronaut.context.Qualifier} that qualifies beans by generic type arguments and only
 * returns the candidates that most closely match.
 *
 * @param <T> The type
 * @author James Kleeh
 * @since 1.1.1
 */
@Internal
public class ClosestTypeArgumentQualifier<T> extends TypeArgumentQualifier<T> {

    private static final Logger LOG = ClassUtils.getLogger(ClosestTypeArgumentQualifier.class);
    private final List<Class>[] hierarchies;

    /**
     * @param typeArguments The type arguments
     */
    ClosestTypeArgumentQualifier(Class... typeArguments) {
        super(typeArguments);
        this.hierarchies = new List[typeArguments.length];
        for (int i = 0 ; i < typeArguments.length; i++) {
            hierarchies[i] = ClassUtils.resolveHierarchy(typeArguments[i]);
        }
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates
                .filter(candidate -> beanType.isAssignableFrom(candidate.getBeanType()))
                .map(candidate -> {
                    List<Class> hierarchy = ClassUtils.resolveHierarchy(beanType);

                    int candidateResult = -1;
                    for (Class beanSuperType : hierarchy) {
                        List<Class> typeArguments = getTypeArguments(beanSuperType, candidate);

                        int result = compare(typeArguments);
                        if (result >= 0) {
                            if (candidateResult < 0 || result < candidateResult) {
                                candidateResult = result;
                            }
                        } else if (LOG.isTraceEnabled()) {
                            LOG.trace("Bean type {} seen as {} is not compatible with candidate generic types [{}] of candidate {}", beanType, beanSuperType, CollectionUtils.toString(typeArguments), candidate);
                        }
                    }
                    return new AbstractMap.SimpleEntry<>(candidate, candidateResult);
                })
                .filter(entry -> entry.getValue() > -1)
                .collect(StreamUtils.minAll(
                        Comparator.comparingInt((ToIntFunction<Map.Entry<BT, Integer>>) Map.Entry::getValue),
                        Collectors.toList())
                )
                .stream()
                .map(Map.Entry::getKey);
    }

    /**
     * @param classesToCompare An array of classes
     * @return Whether the types are compatible
     */
    protected int compare(List<Class> classesToCompare) {
        final Class[] typeArguments = getTypeArguments();
        if (classesToCompare.size() == 0 && typeArguments.length == 0) {
            return 0;
        } else if (classesToCompare.size() != typeArguments.length) {
            return -1;
        } else {
            int comparison = 0;
            for (int i = 0; i < classesToCompare.size(); i++) {
                if (typeArguments[i] == Object.class) {
                    continue;
                }
                Class left = classesToCompare.get(i);
                List<Class> hierarchy =  hierarchies[i];
                int index = hierarchy.indexOf(left);
                if (index == -1) {
                    comparison = -1;
                    break;
                }
                comparison = comparison + index;
            }
            return comparison;
        }

    }
}
