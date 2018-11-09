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

public class ComputeInstanceMetadataResolverUtils {

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
