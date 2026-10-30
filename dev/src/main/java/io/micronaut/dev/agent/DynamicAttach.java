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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;

/**
 * Obtains an {@link Instrumentation} without a {@code -javaagent} flag, by attaching to the running
 * JVM through {@code byte-buddy-agent} when it is on the classpath. The JVM must allow it:
 * {@code -XX:+EnableDynamicAgentLoading}, or the warning the JDK prints otherwise.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DynamicAttach {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicAttach.class);
    private static final String BYTE_BUDDY_AGENT = "net.bytebuddy.agent.ByteBuddyAgent";

    private DynamicAttach() {
    }

    /**
     * The instrumentation: the agent's if one was loaded, else a dynamic attach if possible.
     *
     * @return The instrumentation, or null when neither is available
     */
    @Nullable
    public static Instrumentation instrumentation() {
        Instrumentation existing = DevAgent.instrumentation();
        if (existing != null) {
            return existing;
        }
        if (!ClassUtils.isPresent(BYTE_BUDDY_AGENT, DynamicAttach.class.getClassLoader())) {
            return null;
        }
        try {
            Instrumentation attached = ByteBuddyAttach.attach();
            DevAgent.install(attached);
            return attached;
        } catch (RuntimeException | LinkageError e) {
            LOG.info("Cannot attach an agent to this JVM ({}): method-body changes restart the application; launch with -javaagent or -XX:+EnableDynamicAgentLoading for the fast path", e.getMessage());
            return null;
        }
    }

    /**
     * Loaded only when byte-buddy-agent is present.
     */
    private static final class ByteBuddyAttach {
        static Instrumentation attach() {
            return net.bytebuddy.agent.ByteBuddyAgent.install();
        }
    }
}
