package io.micronaut.test.lombok;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(builderClassName = "CustomBuilder")
@Introspected(builder =
    @Introspected.IntrospectionBuilder(
        builderClass = RobotEntity.CustomBuilder.class
    )
)
public class RobotEntity {
    String name;
}
