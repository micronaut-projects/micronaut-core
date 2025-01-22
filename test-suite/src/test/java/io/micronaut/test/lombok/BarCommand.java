package io.micronaut.test.lombok;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Introspected
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BarCommand {

    private String foo;

    private String bar;

    private String baz;
}
