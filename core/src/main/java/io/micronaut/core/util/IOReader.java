/*
 * Copyright 2017-2022 original authors
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

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility functional interface to consume {@link InputStream} and produce a result.
 *
 * @param <R> the type of the result of the function
 * @author Denis Stepanov
 * @since 4.6.0
 */
@FunctionalInterface
public interface IOReader<R> {

    /**
     * Read an input stream and produces a result.
     *
     * @param inputStream the input stream
     * @return the function result
     */
    R read(InputStream inputStream) throws IOException;
}
