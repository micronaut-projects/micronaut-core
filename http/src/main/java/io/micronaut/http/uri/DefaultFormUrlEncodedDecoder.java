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
package io.micronaut.http.uri;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.form.FormConfiguration;
import io.micronaut.http.form.FormUrlEncodedDecoder;
import jakarta.inject.Singleton;
import reactor.util.annotation.NonNull;
import java.nio.charset.Charset;
import java.util.Map;

@Requires(missingBeans = FormUrlEncodedDecoder.class)
@Singleton
final class DefaultFormUrlEncodedDecoder implements FormUrlEncodedDecoder {
    private final FormConfiguration formConfiguration;

    DefaultFormUrlEncodedDecoder(FormConfiguration formConfiguration) {
        this.formConfiguration = formConfiguration;
    }

    @Override
    @NonNull
    public Map<String, Object> decode(@NonNull String formUrlEncodedString,
                                      @NonNull Charset charset) {
        QueryStringDecoder decoder = new QueryStringDecoder(formUrlEncodedString, charset, false,
                                                            formConfiguration.getMaxDecodedKeyValueParameters(),
                                                            formConfiguration.isSemicolonIsNormalChar());
        return flatten(decoder.parameters());
    }
}
