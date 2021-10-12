/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.core.async;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.TypeInformationProvider;

/**
 * Implementation of {@link TypeInformationProvider} for reactive streams.
 *
 * @author graemerocher
 * @since 2.4.0
 */
public final class ReactiveStreamsTypeInformationProvider implements TypeInformationProvider {

    @Override
    public boolean isSpecifiedSingle(@NonNull AnnotationMetadataProvider annotationMetadataProvider) {
        AnnotationMetadata annotationMetadata = annotationMetadataProvider.getAnnotationMetadata();
        return annotationMetadata.hasStereotype(SingleResult.class) &&
                annotationMetadata.booleanValue(SingleResult.NAME).orElse(true);
    }

    @Override
    public boolean isSingle(@NonNull Class<?> type) {
        return Publishers.isSingle(type);
    }

    @Override
    public boolean isReactive(@NonNull Class<?> type) {
        return Publishers.isConvertibleToPublisher(type);
    }

    @Override
    public boolean isCompletable(@NonNull Class<?> type) {
        return Publishers.isCompletable(type);
    }
}
