/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.util;

import io.micronaut.core.annotation.Internal;

/**
 * Utility methods for dealing with exceptions.
 *
 * @author Denis Stepanov
 */
@Internal
public final class ExceptionUtils {

    /**
     * Throws a given {@link Throwable} without requiring the caller to handle it explicitly,
     * bypassing checked exception restrictions at compile time.
     *
     * @param <T> the type of the throwable being thrown
     * @param <R> the return type of the method
     * @param t the throwable instance to be thrown
     * @return this method does not return normally, as it always throws the supplied throwable
     * @throws T the throwable passed as the argument
     */
    public static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

}
