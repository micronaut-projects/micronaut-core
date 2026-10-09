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
package io.micronaut.context.reload;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Where a development launcher installs its {@link RequestAdmission} while it runs the process, for
 * {@link RequestAdmission#current()}. A static of the launcher's tier, as the launcher itself: one per process,
 * never bound to an application context.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Experimental
@NullMarked
public final class RequestAdmissionHolder {

    private static final AtomicReference<@Nullable RequestAdmission> CURRENT = new AtomicReference<>();

    private RequestAdmissionHolder() {
    }

    /**
     * Installs the admission of the launcher that starts running the process.
     *
     * @param admission The admission
     * @return False when another one is installed, which stays
     */
    public static boolean install(RequestAdmission admission) {
        return CURRENT.compareAndSet(null, admission);
    }

    /**
     * Removes the admission of a launcher that stopped; another one installed meanwhile stays.
     *
     * @param admission The admission it installed
     */
    public static void uninstall(RequestAdmission admission) {
        CURRENT.compareAndSet(admission, null);
    }

    /**
     * @return The installed admission, or null
     */
    static @Nullable RequestAdmission current() {
        return CURRENT.get();
    }
}
