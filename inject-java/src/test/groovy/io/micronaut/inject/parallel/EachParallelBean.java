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
package io.micronaut.inject.parallel;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Parameter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An iterable parallel bean, so that the parallel startup takes the configuration branch that
 * initializes every candidate of a single definition.
 */
@EachProperty("parallel.each")
@Parallel
public class EachParallelBean {

    public static final Set<String> CONSTRUCTED = ConcurrentHashMap.newKeySet();

    private final String name;

    public EachParallelBean(@Parameter String name) {
        this.name = name;
        CONSTRUCTED.add(name);
    }

    public String getName() {
        return name;
    }

    /**
     * Restores the recorded names, so that the spec is isolated even if it runs more than once
     * in the same JVM.
     */
    public static void reset() {
        CONSTRUCTED.clear();
    }
}
