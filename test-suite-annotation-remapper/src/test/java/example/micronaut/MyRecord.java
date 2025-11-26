package example.micronaut;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;

@ReflectiveAccess
@Introspected
public record MyRecord(String name, int age) {
}
