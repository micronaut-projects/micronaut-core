package io.micronaut.test.lombok;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;

@Introspected
@Builder
public record RobotRecord(String name) {
}
