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
package io.micronaut.context.watch;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Annotation;

/**
 * Receives the changes to the executable methods carrying an annotation, registered with
 * {@link io.micronaut.context.BeanContext#watchMethods(Class, ExecutableMethodWatcher)}. The reload-aware
 * successor of {@link io.micronaut.context.processor.ExecutableMethodProcessor}: it sees what went as well
 * as what came, paired where a method came back in a new generation.
 *
 * @param <A> The annotation type watched
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ExecutableMethodWatcher<A extends Annotation> {

    /**
     * Called once per batch: at startup with every method carrying the annotation, and afterwards whenever
     * one is added or removed.
     *
     * @param change The change
     */
    void onChange(ExecutableMethodChange<A> change);
}
