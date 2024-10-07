package io.micronaut.inject.foreach.qualifier;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;

@Requires(property = "spec", value = "EachBeanQualifierSpec")
@Singleton
public class MyMapEachUser {

    private final Map<String, MyEach1> allMap;

    public MyMapEachUser(Map<String, MyEach1> allMap) {
        this.allMap = allMap;
    }

    public Map<String, MyEach1> getAllMap() {
        return allMap;
    }
}
