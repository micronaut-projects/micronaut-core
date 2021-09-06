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
package io.micronaut.discovery.cloud;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpMethod;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for {@link ComputeInstanceMetadataResolver}'s.
 *
 * @author Alvaro Sanchez-Mariscal
 * @since 1.1
 */
@Internal
public class ComputeInstanceMetadataResolverUtils {
    /**
     * Reads the result of a URL and parses it using the given {@link ObjectMapper}.
     *
     * @param url                 the URL to read
     * @param connectionTimeoutMs connection timeout, in milliseconds
     * @param readTimeoutMs       read timeout, in milliseconds
     * @param objectMapper        Jackson's {@link ObjectMapper}
     * @param requestProperties   any request properties to pass
     * @return a {@link JsonNode} instance
     * @throws IOException if any I/O error occurs
     */
    public static com.fasterxml.jackson.databind.JsonNode readMetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs, ObjectMapper objectMapper, Map<String, String> requestProperties) throws IOException {
        try (InputStream in = openMetadataUrl(url, connectionTimeoutMs, readTimeoutMs, requestProperties)) {
            return objectMapper.readTree(in);
        }
    }

    /**
     * Reads the result of a URL and parses it using the given {@link JsonFactory}.
     *
     * @param url                 the URL to read
     * @param connectionTimeoutMs connection timeout, in milliseconds
     * @param readTimeoutMs       read timeout, in milliseconds
     * @param treeCodec           Tree codec to use (contains deserialization config)
     * @param jsonFactory         Jackson's {@link JsonFactory}
     * @param requestProperties   any request properties to pass
     * @return a {@link JsonNode} instance
     * @throws IOException if any I/O error occurs
     */
    @Experimental
    public static JsonNode readMetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs, JsonNodeTreeCodec treeCodec, JsonFactory jsonFactory, Map<String, String> requestProperties) throws IOException {
        try (InputStream in = openMetadataUrl(url, connectionTimeoutMs, readTimeoutMs, requestProperties);
             JsonParser parser = jsonFactory.createParser(in)) {
            return treeCodec.readTree(parser);
        }
    }

    private static InputStream openMetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs, Map<String, String> requestProperties) throws IOException {
        URLConnection urlConnection = url.openConnection();

        if (url.getProtocol().equalsIgnoreCase("file")) {
            urlConnection.connect();
            return urlConnection.getInputStream();
        } else {
            HttpURLConnection uc = (HttpURLConnection) urlConnection;
            uc.setConnectTimeout(connectionTimeoutMs);
            requestProperties.forEach(uc::setRequestProperty);
            uc.setReadTimeout(readTimeoutMs);
            uc.setRequestMethod(HttpMethod.GET.name());
            uc.setDoOutput(true);
            return uc.getInputStream();
        }
    }

    /**
     * Populates the instance instance metadata's {@link AbstractComputeInstanceMetadata#setMetadata(Map)} property.
     *
     * @param instanceMetadata The instance metadata
     * @param metadata         A json object of metadata
     */
    @Experimental
    public static void populateMetadata(AbstractComputeInstanceMetadata instanceMetadata, JsonNode metadata) {
        if (metadata != null) {
            Map<String, String> finalMetadata = new HashMap<>(metadata.size());
            for (Map.Entry<String, JsonNode> entry : metadata.entries()) {
                JsonNode value = entry.getValue();
                if (value.isString()) {
                    finalMetadata.put(entry.getKey(), value.getStringValue());
                }
            }
            instanceMetadata.setMetadata(finalMetadata);
        }
    }


    /**
     * Resolve a value as a string from the metadata json.
     *
     * @param json The json
     * @param key  The key
     * @return An optional value
     */
    public static Optional<String> stringValue(com.fasterxml.jackson.databind.JsonNode json, String key) {
        return Optional.ofNullable(json.findValue(key)).map(com.fasterxml.jackson.databind.JsonNode::asText);
    }

    /**
     * Populates the instance instance metadata's {@link AbstractComputeInstanceMetadata#setMetadata(Map)} property.
     *
     * @param instanceMetadata The instance metadata
     * @param metadata         A map of metadata
     */
    public static void populateMetadata(AbstractComputeInstanceMetadata instanceMetadata, Map<?, ?> metadata) {
        if (metadata != null) {
            Map<String, String> finalMetadata = new HashMap<>(metadata.size());
            for (Map.Entry<?, ?> entry : metadata.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    finalMetadata.put(key.toString(), value.toString());
                }
            }
            instanceMetadata.setMetadata(finalMetadata);
        }
    }
}
