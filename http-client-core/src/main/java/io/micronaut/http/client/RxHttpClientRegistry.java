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
package io.micronaut.http.client;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpVersion;

/**
 * Internal interface for managing the construction and lifecycle of instances of {@link RxHttpClient}.
 *
 * @author graemerocher
 * @since 2.0
 */
@Internal
public interface RxHttpClientRegistry {

    /**
     * Return the client for the client ID and path.
     *
     * @param httpVersion The HTTP version
     * @param clientId    The client ID
     * @param path        The path (Optional)
     * @return The client
     */
    @NonNull
    RxHttpClient getClient(HttpVersion httpVersion, @NonNull String clientId, @Nullable String path);

    /**
     * Return the client for the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata.
     * @return The client
     */
    @NonNull
    RxHttpClient getClient(@NonNull AnnotationMetadata annotationMetadata);

    /**
     * Dispose of the client defined by the given metadata.
     *
     * @param annotationMetadata The annotation metadata
     */
    void disposeClient(AnnotationMetadata annotationMetadata);

    /**
     * @return Return the default HTTP client.
     */
    default RxHttpClient getDefaultClient() {
        return getClient(AnnotationMetadata.EMPTY_METADATA);
    }

}
