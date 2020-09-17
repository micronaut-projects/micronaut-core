/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jackson.env;

import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * <p>Reads properties from JSON stored in the environment variables <tt>SPRING_APPLICATION_JSON</tt> or <tt>MICRONAUT_APPLICATION_JSON</tt>.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class EnvJsonPropertySourceLoader extends JsonPropertySourceLoader {

    /**
     * Position for the system property source loader in the chain.
     */
    public static final int POSITION = SystemPropertiesPropertySource.POSITION + 50;

    private static final String SPRING_APPLICATION_JSON = "SPRING_APPLICATION_JSON";
    private static final String MICRONAUT_APPLICATION_JSON = "MICRONAUT_APPLICATION_JSON";

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    protected Optional<InputStream> readInput(ResourceLoader resourceLoader, String fileName) {
        if (fileName.equals("application.json")) {
            return getEnvValueAsStream();
        }
        return Optional.empty();
    }

    /**
     * @return The JSON as input stream stored in the environment variables
     * <tt>SPRING_APPLICATION_JSON</tt> or <tt>MICRONAUT_APPLICATION_JSON</tt>.
     */
    protected Optional<InputStream> getEnvValueAsStream() {
        String v = getEnvValue();
        if (v != null) {
            String encoding = System.getProperty("file.encoding");
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return Optional.of(new ByteArrayInputStream(v.getBytes(charset)));
        }
        return Optional.empty();
    }

    /**
     * @return The JSON stored in the environment variables
     * <tt>SPRING_APPLICATION_JSON</tt> or <tt>MICRONAUT_APPLICATION_JSON</tt>.
     */
    protected String getEnvValue() {
        String v = System.getenv(SPRING_APPLICATION_JSON);
        if (v == null) {
            v = System.getenv(MICRONAUT_APPLICATION_JSON);
        }
        return v;
    }
}
