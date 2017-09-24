package org.particleframework.http.cors;

import org.particleframework.http.server.HttpServerConfiguration;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.particleframework.http.HttpHeaders.*;

/**
 * Responsible for handling CORS requests and responses
 *
 * @author James Kleeh
 * @since 1.0
 */
public class CorsHandler {

    protected HttpServerConfiguration.CorsConfiguration corsConfiguration;

    public CorsHandler(HttpServerConfiguration.CorsConfiguration corsConfiguration) {
        this.corsConfiguration = corsConfiguration;
    }

    private Optional<CorsOriginConfiguration> getConfiguration(String requestOrigin) {
        Map<String, CorsOriginConfiguration> corsConfigurations = corsConfiguration.getConfigurations();
        for (Map.Entry<String, CorsOriginConfiguration> config : corsConfigurations.entrySet()) {
            List<String> allowedOrigins = config.getValue().allowedOrigins.orElse(Collections.emptyList());
            if (!allowedOrigins.isEmpty()) {
                boolean matches = false;
                if (isAny(allowedOrigins)) {
                    matches = true;
                }
                if (!matches) {
                    matches = allowedOrigins.stream().anyMatch(origin -> {
                        if (origin.equals(requestOrigin)) {
                            return true;
                        }
                        Pattern p = Pattern.compile(origin);
                        Matcher m = p.matcher(requestOrigin);
                        return m.matches();
                    });
                }

                if (matches) {
                    return Optional.of(config.getValue());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isAny(List<String> values) {
        return values.size() == 1 && values.get(0).equals("*");
    }

    public void handleRequest(CorsRequest request, CorsRequestHandler requestHandler) {

        if (request.isCorsRequest()) {

            String requestOrigin = request.getHeader(ORIGIN);
            boolean preflight = request.isPreflightRequest();

            Optional<CorsOriginConfiguration> optionalConfig = getConfiguration(requestOrigin);

            if (optionalConfig.isPresent()) {
                CorsOriginConfiguration config = optionalConfig.get();

                String requestMethod = request.getMethod();

                List<String> allowedMethods = config.allowedMethods.orElse(Collections.emptyList());

                if (!isAny(allowedMethods)) {
                    if (!allowedMethods.stream().anyMatch(method -> method.equals(requestMethod))) {
                        requestHandler.rejectRequest();
                        return;
                    }
                }

                if (preflight) {
                    List<String> headers = request.getHeaders(ACCESS_CONTROL_REQUEST_HEADERS);
                    List<String> allowedHeaders = config.allowedHeaders.orElse(Collections.emptyList());

                    if (!isAny(allowedHeaders)) {
                        if (!headers.stream()
                                .allMatch(header -> allowedHeaders.stream()
                                        .anyMatch(allowedHeader -> allowedHeader.equals(header.trim())))) {
                            requestHandler.rejectRequest();
                            return;
                        }
                    }

                    requestHandler.preflightSuccess();
                    return;
                }
            }
        }

        requestHandler.continueRequest();
    }

    protected void setAllowCredentials(CorsOriginConfiguration config, CorsResponse response) {
        if (config.allowCredentials.orElse(false)) {
            response.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, Boolean.toString(true));
        }
    }

    protected void setExposeHeaders(Optional<List<String>> optionalExposedHeaders, CorsResponse response) {
        optionalExposedHeaders.ifPresent(exposedHeaders ->
                exposedHeaders.forEach(header -> response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, header)));
    }

    protected void setVary(CorsResponse response) {
        response.setHeader(VARY, ORIGIN);
    }

    protected void setOrigin(String origin, CorsResponse response) {
        response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    }

    protected void setAllowMethods(String method, CorsResponse response) {
        response.setHeader(ACCESS_CONTROL_ALLOW_METHODS, method);
    }

    protected void setAllowHeaders(Optional<List<String>> optionalAllowHeaders, CorsResponse response) {
        optionalAllowHeaders.ifPresent(allowedHeaders ->
                allowedHeaders.forEach(header -> response.addHeader(ACCESS_CONTROL_ALLOW_HEADERS, header)));
    }

    protected void setMaxAge(Optional<Long> optionalMaxAge, CorsResponse response) {
        optionalMaxAge.ifPresent(maxAge -> response.setHeader(ACCESS_CONTROL_MAX_AGE, Long.toString(maxAge)));
    }

    public void handleResponse(CorsRequest request, CorsResponse response) {
        if (request.isCorsRequest()) {
            String requestOrigin = request.getHeader(ORIGIN);

            Optional<CorsOriginConfiguration> optionalConfig = getConfiguration(requestOrigin);

            if (optionalConfig.isPresent()) {
                CorsOriginConfiguration config = optionalConfig.get();

                if (request.isPreflightRequest()) {
                    setAllowMethods(request.getMethod(), response);
                    setAllowHeaders(Optional.ofNullable(request.getHeaders(ACCESS_CONTROL_REQUEST_HEADERS)), response);
                    setMaxAge(config.getMaxAge(), response);
                }

                setOrigin(requestOrigin, response);
                setVary(response);
                setExposeHeaders(config.getExposedHeaders(), response);
                setAllowCredentials(config, response);
            }
        }
    }
}
