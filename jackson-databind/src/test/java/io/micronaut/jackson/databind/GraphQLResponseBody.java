package io.micronaut.jackson.databind;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;

import java.util.Map;

@Introspected
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
