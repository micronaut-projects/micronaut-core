package io.micronaut.jackson.databind;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Introspected;

import java.util.Map;

@Introspected
public class GraphQLResponseBodyNoInclude {
    private final Map<String, Object> specification;

    @JsonCreator
    public GraphQLResponseBodyNoInclude(Map<String, Object> specification) {
        this.specification = specification;
    }

    @JsonAnyGetter
    public Map<String, Object> getSpecification() {
        return specification;
    }
}
