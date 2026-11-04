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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * What a generated bean definition keeps of the failure of its static initializer, such as a missing optional
 * dependency, to throw when the definition is loaded. The definition keeps it in a static field for as long as its
 * class lives, and a {@link Throwable}'s backtrace refers to the class of every frame on the stack that created it, and so to
 * their class loaders: the failure itself would keep the loaders of the callers that happened to initialize the
 * definition reachable. This holds the same type name, message, stack, cause and suppressed failures as plain data.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class BeanInitializationFailure extends Throwable {

    private final String type;

    private BeanInitializationFailure(String type, @Nullable String message, @Nullable Throwable cause) {
        super(message, cause, true, true);
        this.type = type;
    }

    /**
     * Copies a failure and those it refers to. This never throws: it runs in the catch block of a static initializer,
     * where anything it threw would leave the definition's class erroneous rather than reporting the failure when the
     * definition is loaded. A failure whose methods throw, or a chain of causes too deep to copy, leaves only its type.
     *
     * @param failure The failure
     * @return The copy, which refers to no class
     */
    @SuppressWarnings("java:S1181") // the initializer that calls this catches every Throwable for the same reason
    public static Throwable detach(Throwable failure) {
        try {
            return copy(failure, Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch (Throwable e) {
            return new BeanInitializationFailure(failure.getClass().getName(), null, null);
        }
    }

    private static BeanInitializationFailure copy(Throwable failure, Set<Throwable> seen) {
        seen.add(failure);
        Throwable cause = failure.getCause();
        BeanInitializationFailure copy = new BeanInitializationFailure(
            failure.getClass().getName(),
            failure.getMessage(),
            cause == null || seen.contains(cause) ? null : copy(cause, seen)
        );
        copy.setStackTrace(failure.getStackTrace());
        for (Throwable suppressed : failure.getSuppressed()) {
            if (!seen.contains(suppressed)) {
                copy.addSuppressed(copy(suppressed, seen));
            }
        }
        return copy;
    }

    /**
     * Captures nothing: this is what would create the backtrace.
     *
     * @return This failure
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String toString() {
        String message = getMessage();
        return message == null ? type : type + ": " + message;
    }
}
