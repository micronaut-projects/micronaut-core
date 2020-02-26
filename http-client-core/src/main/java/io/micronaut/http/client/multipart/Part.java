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
package io.micronaut.http.client.multipart;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The base class representing multiple parts in the {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 * @param <D> the data type
 */
abstract class Part<D> {

    /**
     * Name of the parameter in Multipart request body.
     */
    protected final String name;

    /**
     * @param name Name of the parameter
     */
    Part(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Adding parts with a null name is not allowed");
        }
        this.name = name;
    }

    /**
     * @return The content of this part.
     */
    abstract D getContent();

    /**
     * @param factory The factory used to create the multipart data
     * @return The multi part data object
     * @param <T> The data
     */
    abstract @NonNull <T> T getData(@NonNull MultipartDataFactory<T> factory);
}
