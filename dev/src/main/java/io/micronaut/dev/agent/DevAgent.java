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
package io.micronaut.dev.agent;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The agent: {@code -javaagent:micronaut-dev.jar} at launch, which the build plugins add, or
 * attached to the running JVM when {@code byte-buddy-agent} is on the classpath. It keeps the
 * {@link Instrumentation} the fast path redefines method bodies with; without it every change
 * restarts.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class DevAgent {

    private static final AtomicReference<Instrumentation> INSTRUMENTATION = new AtomicReference<>();

    private DevAgent() {
    }

    /**
     * Called by the JVM for {@code -javaagent}.
     *
     * @param arguments The agent arguments, unused
     * @param instrumentation The instrumentation
     */
    public static void premain(@Nullable String arguments, Instrumentation instrumentation) {
        INSTRUMENTATION.compareAndSet(null, instrumentation);
    }

    /**
     * Called by the JVM when the agent is attached to a running JVM.
     *
     * @param arguments The agent arguments, unused
     * @param instrumentation The instrumentation
     */
    public static void agentmain(@Nullable String arguments, Instrumentation instrumentation) {
        INSTRUMENTATION.compareAndSet(null, instrumentation);
    }

    /**
     * @return The instrumentation, or null when no agent was loaded
     */
    @Nullable
    public static Instrumentation instrumentation() {
        return INSTRUMENTATION.get();
    }

    /**
     * Records an instrumentation obtained another way, such as through a dynamic attach.
     *
     * @param instrumentation The instrumentation
     */
    public static void install(Instrumentation instrumentation) {
        INSTRUMENTATION.compareAndSet(null, instrumentation);
    }
}
