/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.form;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} implementation of {@link FormConfiguration}.
 * @author Sergio del Amo
 * @since 4.7.1
 */
@ConfigurationProperties(FormConfigurationProperties.PREFIX)
final class FormConfigurationProperties implements FormConfiguration {
    /**
     * Prefix for Micronaut form settings.
     */
    public static final String PREFIX = "micronaut.http.forms";
    /**
     * The default maximum of decoded key value parameters used in {@link io.micronaut.http.form.FormUrlEncodedDecoder}.
     */
    @SuppressWarnings("WeakerAccess")
    private static final int DEFAULT_MAX_DECODED_KEY_VALUE_PARAMETERS = 1024;

    /**
     * Default value indicating whether the semicolon is treated as a normal character
     * used in {@link io.micronaut.http.form.FormUrlEncodedDecoder}.
     */
    @SuppressWarnings("WeakerAccess")
    private static final boolean DEFAULT_SEMICOLON_IS_NORMAL_CHAR = false;

    private int maxDecodedKeyValueParameters = DEFAULT_MAX_DECODED_KEY_VALUE_PARAMETERS;
    private boolean semicolonIsNormalChar = DEFAULT_SEMICOLON_IS_NORMAL_CHAR;

    /**
     *
     * @return default maximum of decoded key value parameters
     */
    @Override
    public int getMaxDecodedKeyValueParameters() {
        return maxDecodedKeyValueParameters;
    }


    /**
     * @return true if the semicolon is treated as a normal character, false otherwise
     */
    @Override
    public boolean isSemicolonIsNormalChar() {
        return semicolonIsNormalChar;
    }

    /**
     * default maximum of decoded key value parameters. Default value {@link #DEFAULT_MAX_DECODED_KEY_VALUE_PARAMETERS}.
     * @param maxDecodedKeyValueParameters default maximum of decoded key value parameters
     */
    public void setMaxDecodedKeyValueParameters(int maxDecodedKeyValueParameters) {
        this.maxDecodedKeyValueParameters = maxDecodedKeyValueParameters;
    }

    /**
     * @param semicolonIsNormalChar true if the semicolon should be treated as a normal character, false otherwise
     */
    public void setSemicolonIsNormalChar(boolean semicolonIsNormalChar) {
        this.semicolonIsNormalChar = semicolonIsNormalChar;
    }
}
