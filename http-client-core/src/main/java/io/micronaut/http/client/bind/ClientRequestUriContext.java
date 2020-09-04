package io.micronaut.http.client.bind;

import io.micronaut.core.type.Argument;
import io.micronaut.http.uri.UriMatchTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientRequestUriContext {

    private final Map<String, Object> pathParameters;
    private final Map<String, String> queryParameters;
    private final List<Argument> bodyArguments;
    private final UriMatchTemplate uriTemplate;

    public ClientRequestUriContext(UriMatchTemplate uriTemplate, Map<String, Object> pathParameters, Map<String, String> queryParameters) {
        this.uriTemplate = uriTemplate;
        this.pathParameters = pathParameters;
        this.queryParameters = queryParameters;
        this.bodyArguments = new ArrayList<>();
    }

    public UriMatchTemplate getUriTemplate() {
        return uriTemplate;
    }

    public Map<String, Object> getPathParameters() {
        return pathParameters;
    }

    public Map<String, String> getQueryParameters() {
        return queryParameters;
    }

    public List<Argument> getBodyArguments() {
        return bodyArguments;
    }
}
