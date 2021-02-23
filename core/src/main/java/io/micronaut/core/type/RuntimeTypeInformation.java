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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;

import java.util.*;

/**
 * Abstracts how types are interpreted by the core API.
 *
 * @author graemerocher
 * @since 2.4.0
 */
@Internal
final class RuntimeTypeInformation {
    private static final Collection<TypeInformationProvider> TYPE_INFORMATION_PROVIDERS;

    static {
        final ServiceLoader<TypeInformationProvider> loader = ServiceLoader.load(TypeInformationProvider.class);
        List<TypeInformationProvider> informationProviders = new ArrayList<>(2);
        for (TypeInformationProvider informationProvider : loader) {
            informationProviders.add(informationProvider);
        }

        TYPE_INFORMATION_PROVIDERS = Collections.unmodifiableList(informationProviders);
    }

    /**
     * Returns whether the annotation metadata specifies the type as single.
     * @param annotationMetadata The annotation metadata provider
     * @return True if does
     */
    static boolean isSpecifiedSingle(AnnotationMetadataProvider annotationMetadata) {
        return TYPE_INFORMATION_PROVIDERS.stream().anyMatch(provider -> provider.isSpecifiedSingle(annotationMetadata));
    }

    /**
     * does the given type represent a type that emits a single item.
     * @param type True if it does
     * @return True if it is single
     */
    static boolean isSingle(Class<?> type) {
        return TYPE_INFORMATION_PROVIDERS.stream().anyMatch(provider -> provider.isSingle(type));
    }

    /**
     * does the type represent a reactive type.
     * @param type The type
     * @return True if it is reactive
     */
    static boolean isReactive(Class<?> type) {
        return TYPE_INFORMATION_PROVIDERS.stream().anyMatch(provider -> provider.isReactive(type));
    }

    /**
     * does the type represent a completable type.
     * @param type The type
     * @return True if it is completable
     */
    static boolean isCompletable(Class<?> type) {
        return TYPE_INFORMATION_PROVIDERS.stream().anyMatch(provider -> provider.isCompletable(type));
    }

    /**
     * Does the type represent a wrapper type.
     * @param type The type
     * @param <T> The generic type
     * @return True if it is a wrapper type
     * @see TypeInformation#isWrapperType()
     */
    static <T> boolean isWrapperType(Class<T> type) {
        return TYPE_INFORMATION_PROVIDERS.stream().anyMatch(provider -> provider.isWrapperType(type));
    }
}
