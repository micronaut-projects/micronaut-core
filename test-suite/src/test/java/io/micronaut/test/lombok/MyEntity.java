package io.micronaut.test.lombok;


import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;

@Introspected
@Value
@Builder
public class MyEntity {
    public static final String NAME_INDEX = "name";

    @lombok.NonNull
    String id;

    @lombok.NonNull
    String name;
}
