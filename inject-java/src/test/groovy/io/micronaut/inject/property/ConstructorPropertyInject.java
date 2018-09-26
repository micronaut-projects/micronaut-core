package io.micronaut.inject.property;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.convert.format.MapFormat;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class ConstructorPropertyInject {

    private final Map<String, String> values;
    private String str;
    private int integer;
    private String nullable;

    public ConstructorPropertyInject(
            @Property(name = "my.map")
            @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
            Map<String, String> values,
            @Property(name = "my.string")
            String str,
            @Property(name = "my.int")
            int integer,
            @Property(name = "my.nullable")
            @Nullable
            String nullable) {
        this.values = values;
        this.str = str;
        this.integer = integer;
        this.nullable = nullable;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public String getStr() {
        return str;
    }

    public int getInteger() {
        return integer;
    }

    public String getNullable() {
        return nullable;
    }
}
