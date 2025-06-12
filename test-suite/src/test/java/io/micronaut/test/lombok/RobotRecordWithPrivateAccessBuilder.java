package io.micronaut.test.lombok;

import io.micronaut.core.annotation.Introspected;
import lombok.AccessLevel;
import lombok.Builder;

@Introspected
@Builder(access = AccessLevel.PRIVATE)
public record RobotRecordWithPrivateAccessBuilder(String name) {
}
