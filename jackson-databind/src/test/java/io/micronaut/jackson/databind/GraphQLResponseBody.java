package io.micronaut.jackson.databind;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public class GraphQLResponseBody {
    private final Map<String, Object> specification;

    @JsonCreator
    public GraphQLResponseBody(Map<String, Object> specification) {
        this.specification = specification;
    }

    @JsonAnyGetter
    @JsonInclude
    public Map<String, Object> getSpecification() {
        return specification;
    }
}
