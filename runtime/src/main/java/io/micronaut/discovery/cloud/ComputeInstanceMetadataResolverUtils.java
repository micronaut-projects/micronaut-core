/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.discovery.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpMethod;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

/**
 * Utility class for {@link ComputeInstanceMetadataResolver}'s.
 *
 * @author Alvaro Sanchez-Mariscal
 * @since 1.1
 */
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
    public static JsonNode readMetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs, ObjectMapper objectMapper, Map<String, String> requestProperties) throws IOException {
        URLConnection urlConnection = url.openConnection();

        if (url.getProtocol().equalsIgnoreCase("file")) {
            urlConnection.connect();
            try (InputStream in = urlConnection.getInputStream()) {
                return objectMapper.readTree(in);
            }
        } else {
            HttpURLConnection uc = (HttpURLConnection) urlConnection;
            uc.setConnectTimeout(connectionTimeoutMs);
            requestProperties.forEach(uc::setRequestProperty);
            uc.setReadTimeout(readTimeoutMs);
            uc.setRequestMethod(HttpMethod.GET.name());
            uc.setDoOutput(true);
            int responseCode = uc.getResponseCode();
            try (InputStream in = uc.getInputStream()) {
                return objectMapper.readTree(in);
            }
        }
    }
}
