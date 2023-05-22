package io.micronaut.inject.beans;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Requires(property = "repeatabletest", value = "true")
@Singleton
class MapOfListsBean4 {

    private final Map<String, List<List<List<List<Map<String, @MyMin4 OptionalInt>>>>>> map;

    MapOfListsBean4(Map<String, List<List<List<List<Map<String, @MyMin4 OptionalInt>>>>>> map) {
        this.map = map;
    }
}
