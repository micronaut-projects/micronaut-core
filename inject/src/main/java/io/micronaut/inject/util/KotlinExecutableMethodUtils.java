/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.inject.util;

import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import kotlin.Unit;

import javax.annotation.Nonnull;

import static io.micronaut.core.util.KotlinUtils.KOTLIN_COROUTINES_SUPPORTED;

/**
 * <p>Utility methods for working with Kotlin <code>suspend</code> functions</p>.
 *
 * @author Konrad Kamiński
 * @since 1.3.0
 */
public class KotlinExecutableMethodUtils {
    /**
     * Kotlin <code>suspend</code> function return type check.
     *
     * @param method method to be checked
     * @return True if given <code>suspend</code> function's return type is <code>Unit</code>.
     */
    public static boolean isKotlinFunctionReturnTypeUnit(@Nonnull ExecutableMethod method) {
        if (KOTLIN_COROUTINES_SUPPORTED) {
            final Argument[] arguments = method.getArguments();
            final int argumentsLength = arguments.length;
            if (argumentsLength > 0) {
                final Argument[] params = arguments[argumentsLength - 1].getTypeParameters();
                return params.length == 1 && params[0].getType() == Unit.class;
            }
        }

        return false;
    }
}
