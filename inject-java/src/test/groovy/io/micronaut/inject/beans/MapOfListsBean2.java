package io.micronaut.inject.beans;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Requires(property = "repeatabletest", value = "true")
@Singleton
class MapOfListsBean2 {

    @Executable
    void myMethod(Map<String, List<List<List<List<Map<String, @MyMin2 OptionalInt>>>>>> map) {
    }

}
