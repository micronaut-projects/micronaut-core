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
package io.micronaut.http.client.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import java.util.Collections;
import java.util.List;

/**
 * The client filter resolution context.
 *
 * @author graemerocher
 * @since 2.0
 */
public class ClientFilterResolutionContext implements AnnotationMetadataProvider {
    private final List<String> clientIds;
    private final AnnotationMetadata annotationMetadata;

    /**
     * Default constructor.
     *
     * @param clientIds           The client ids
     * @param annotationMetadata The annotation metadata
     */
    public ClientFilterResolutionContext(List<String> clientIds, AnnotationMetadata annotationMetadata) {
        this.clientIds = clientIds;
        this.annotationMetadata = annotationMetadata;
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    /**
     * @return The client ID.
     */
    public @Nullable List<String> getClientIds() {
        if (clientIds != null) {
            return clientIds;
        }
        return Collections.emptyList();
    }
}
