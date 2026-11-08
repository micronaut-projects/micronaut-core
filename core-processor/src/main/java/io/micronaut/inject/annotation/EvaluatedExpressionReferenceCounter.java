/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.Internal;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A reference class writer counter.
 * @author Denis Stepanov
 * @since 4.3.0
 */
@Internal
public class EvaluatedExpressionReferenceCounter {

    public static final String EXPR_SUFFIX = "$Expr";

    private static final Map<String, Integer> CLASS_NAME_INDEXES = new ConcurrentHashMap<>();
    private static final Set<Object> COMPILATIONS = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Provides next expression index for passed class name. In general indexes are needed only
     * to make names of generated expression classes unique and avoid conflicts in cases when
     * multiple expressions are defined in the same class. On each invocation with the same
     * argument this method will return value incremented by 1. On first invocation it will return 0
     *
     * @param className name of class owning evaluated expression
     * @return next index
     */
    public static Integer nextIndex(String className) {
        if (CLASS_NAME_INDEXES.containsKey(className)) {
            return CLASS_NAME_INDEXES.merge(className, 1, Integer::sum);
        }

        CLASS_NAME_INDEXES.put(className, 0);
        return 0;
    }

    /**
     * Notes that a compilation of the Java processors started: see {@link #compilationFinished(Object)}. Every processor
     * of the compilation notes it as it is initialized, before any of them gives an index.
     *
     * @param compilation What identifies the compilation, the same object for each of its processors
     */
    @Internal
    public static void compilationStarted(Object compilation) {
        synchronized (COMPILATIONS) {
            COMPILATIONS.add(compilation);
        }
    }

    /**
     * Notes that a compilation of the Java processors ended, and forgets the indexes given once no other is running: the
     * next compilation, in a compiler that keeps the processors' classes loaded as a build tool's daemon or development
     * mode does, names the expressions of a class from 0 again, as a compilation in a loader of its own would, so a class
     * compiled again keeps the names of its expression classes. Compilations that overlap in one loader keep numbering
     * on, as before, so that none reuses a name another gave in it. A compilation that never finishes leaves the indexes
     * as they are until it is collected, as before.
     *
     * @param compilation What identifies the compilation
     */
    @Internal
    public static void compilationFinished(Object compilation) {
        synchronized (COMPILATIONS) {
            COMPILATIONS.remove(compilation);
            if (COMPILATIONS.isEmpty()) {
                CLASS_NAME_INDEXES.clear();
            }
        }
    }

}
