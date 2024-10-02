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
package io.micronaut.http.tck;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Embedded http2Server under test provider.
 */
@Experimental
public class EmbeddedHttp2ServerUnderTestProvider implements ServerUnderTestProvider {
    @Override
    public @NonNull ServerUnderTest getServer(Map<String, Object> properties) {
        Map<String, Object> mod = new HashMap<>(properties);
        mod.put("micronaut.server.ssl.enabled", true);
        mod.put("micronaut.server.ssl.build-self-signed", true);
        mod.put("micronaut.server.http-version", "2.0");
        mod.put("micronaut.http.client.http-version", "2.0");
        mod.put("micronaut.http.client.ssl.insecure-trust-all-certificates", true);
        return new EmbeddedServerUnderTest(mod);
    }
}
