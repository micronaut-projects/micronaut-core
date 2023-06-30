/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.binary;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Allows checking for MediaTypes that should be considered binary.
 * This is used for example to determine whether to mime encode an AWS lambda response message (where the response body is a String).
 *
 * @author Tim Yates
 * @since 4.0.0
 */
@ConfigurationProperties(BinaryTypeConfiguration.PREFIX)
public class BinaryTypeConfiguration {

    static final String PREFIX = "micronaut.http.binary-types";

    private static final Set<String> DEFAULT_BINARY_TYPES = Set.of(
        MediaType.APPLICATION_OCTET_STREAM,
        MediaType.IMAGE_JPEG,
        MediaType.IMAGE_PNG,
        MediaType.IMAGE_GIF,
        "application/zip"
    );

    private boolean useDefaultBinaryTypes = true;

    @NonNull
    private List<String> additionalTypes = new ArrayList<>();

    /**
     * If this is false then calls to {@link #isMediaTypeBinary(String)} will only check the additional types, and ignore the defaults.
     *
     * @return Whether to use the default binary types
     */
    public boolean isUseDefaults() {
        return useDefaultBinaryTypes;
    }

    /**
     * Sets whether to use the default binary types.
     *
     * @param useDefaults True if they should be used
     */
    public void setUseDefaults(boolean useDefaults) {
        this.useDefaultBinaryTypes = useDefaults;
    }

    /**
     * The additional media types to consider binary.
     *
     * @return A lists of {@link MediaType} objects
     */
    public List<String> getAdditionalTypes() {
        return additionalTypes;
    }

    /**
     * Sets the additional media types to consider binary.
     *
     * @param additionalTypes The media types
     */
    public void setAdditionalTypes(@NonNull List<String> additionalTypes) {
        ArgumentUtils.requireNonNull("additionalTypes", additionalTypes);
        this.additionalTypes = additionalTypes;
    }

    /**
     * Checks whether the given media type is considered binary.
     *
     * @param mediaType The media type
     * @return Whether the media type is considered binary
     */
    public boolean isMediaTypeBinary(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        if (useDefaultBinaryTypes && DEFAULT_BINARY_TYPES.contains(mediaType)) {
            return true;
        }
        return additionalTypes.contains(mediaType);
    }
}
