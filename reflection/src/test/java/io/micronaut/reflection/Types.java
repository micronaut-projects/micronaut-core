package io.micronaut.reflection;

import java.util.List;
import java.util.Map;

/**
 * The shapes the type conversions are read from.
 */
@Tag("types")
public class Types {

    public List<String> names;

    public List<String>[] matrix;

    public List<? extends Number> numbers;

    public Map<String, ?> anything;

    @Binding(value = "b", comment = "ignored")
    public String bound;

    public <T> T identity(T value) {
        return value;
    }
}
