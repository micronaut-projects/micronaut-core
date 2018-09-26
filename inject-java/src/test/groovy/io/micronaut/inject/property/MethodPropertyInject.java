package io.micronaut.inject.property;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.convert.format.MapFormat;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class MethodPropertyInject {


    private Map<String, String> values;
    private String str;
    private int integer;
    private String nullable;

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

    @Inject
    public void setValues(@Property(name = "my.map")
                          @MapFormat(transformation = MapFormat.MapTransformation.FLAT) Map<String, String> values) {
        this.values = values;
    }

    @Inject
    public void setStr(@Property(name = "my.string") String str) {
        this.str = str;
    }

    @Inject
    public void setInteger(@Property(name = "my.int") int integer) {
        this.integer = integer;
    }

    @Inject
    public void setNullable(@Property(name = "my.nullable")
                                @Nullable String nullable) {
        this.nullable = nullable;
    }
}
