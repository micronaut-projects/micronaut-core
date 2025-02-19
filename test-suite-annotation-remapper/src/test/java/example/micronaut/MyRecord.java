package example.micronaut;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record MyRecord(String name, int age) {
}
