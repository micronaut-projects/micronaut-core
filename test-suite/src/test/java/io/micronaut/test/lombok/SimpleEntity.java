package io.micronaut.test.lombok;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

@lombok.Builder(builderClassName = "Builder")
@Data
@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = SimpleEntity.Builder.class))
@AllArgsConstructor
public class SimpleEntity {

    @NonNull
    private String id;

    private String displayName;

    @NonNull
    private String compartmentId;

    @NonNull
    private String state;

    @NonNull
    private Long timeCreated;

    @NonNull
    private Long timeUpdated;

    @Value
    @Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = CompartmentCreationTimeIndexPrefix.Builder.class))
    @lombok.Builder(builderClassName = "Builder")
    public static class CompartmentCreationTimeIndexPrefix {

        String compartmentId;

        Long timeCreated;
    }
}

