package io.micronaut.test.lombok;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(builderClassName = "Builder")
@Introspected(builder =
    @Introspected.IntrospectionBuilder(
        builderClass = RobotEntity.Builder.class
    )
)
public class RobotEntity {
    String name;
}
