package io.micronaut.inject.visitor.beans;

import javax.validation.constraints.Min;
import java.util.List;

public class Test {
    private List<@Min(10) Long> value;

    public List<@Min(20) Long> getValue() {
        return value;
    }
}
