package io.micronaut.docs.ioc.beans;

import io.micronaut.core.annotation.Introspected;
import org.jetbrains.annotations.Nullable;

@Introspected
public class Manufacturer {
    private String id;
    private String name;

    public Manufacturer(@Nullable String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }
}
