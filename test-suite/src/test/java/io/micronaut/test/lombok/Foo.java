package io.micronaut.test.lombok;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder(toBuilder = true)
@ToString
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "UserField", asEnum = true)
@Introspected
public class Foo implements Bar {

    private String name;

}
