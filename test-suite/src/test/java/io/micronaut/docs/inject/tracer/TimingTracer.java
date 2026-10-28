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
package io.micronaut.docs.inject.tracer;

// tag::imports[]
import io.micronaut.context.BeanResolutionTracer;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// end::imports[]

// tag::class[]
public class TimingTracer implements BeanResolutionTracer {

    private final ThreadLocal<Deque<Long>> starts = ThreadLocal.withInitial(ArrayDeque::new); // <1>
    private final Map<String, Long> timings = new ConcurrentHashMap<>();

    @Override
    public void traceBeanCreation(Resolution resolution, BeanDefinition<?> definition, Argument<?> beanType) {
        starts.get().push(System.nanoTime()); // <2>
    }

    @Override
    public void traceBeanCreated(Resolution resolution, BeanDefinition<?> definition) {
        long took = System.nanoTime() - starts.get().pop(); // <3>
        timings.put(definition.getBeanType().getName(), took);
    }

    @Override
    public void traceBeanCreationFailed(Resolution resolution, BeanDefinition<?> definition, Throwable failure) {
        starts.get().pop(); // <4>
    }

    @Override
    public void traceInjectBean(Resolution resolution, Injection injection) {
        // injection points are not timed
    }

    public Map<String, Long> getTimings() {
        return timings;
    }
}
// end::class[]
