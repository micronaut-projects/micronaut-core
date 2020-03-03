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
package io.micronaut.core.util;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

import javax.annotation.Nullable;

/**
 * <p>Internal Utility methods for working with Kotlin <code>suspend</code> functions</p>.
 *
 * @author Konrad Kamiński
 * @since 1.3.0
 */
@Internal
@Experimental
public class KotlinUtils {
    /**
     * Constant indicating whether coroutines are supported.
     */
    public static final boolean KOTLIN_COROUTINES_SUPPORTED;

    private static final Object COROUTINE_SUSPENDED;

    static {
        boolean areKotlinCoroutinesSupportedCandidate;
        Object coroutineSuspendedCandidate;

        try {
            coroutineSuspendedCandidate = IntrinsicsKt.getCOROUTINE_SUSPENDED();
            areKotlinCoroutinesSupportedCandidate = true;
        } catch (NoClassDefFoundError e) {
            coroutineSuspendedCandidate = null;
            areKotlinCoroutinesSupportedCandidate = false;
        }

        KOTLIN_COROUTINES_SUPPORTED = areKotlinCoroutinesSupportedCandidate;
        COROUTINE_SUSPENDED = coroutineSuspendedCandidate;
    }

    /**
     * Kotlin <code>suspend</code> function result check.
     *
     * @param obj object to be checked
     * @return True if given object is an indicating that a  <code>suspend</code> function suspended.
     */
    public static boolean isKotlinCoroutineSuspended(@Nullable Object obj) {
        return KOTLIN_COROUTINES_SUPPORTED && obj == COROUTINE_SUSPENDED;
    }
}
