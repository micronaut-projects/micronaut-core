package io.micronaut.inject.beans;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Singleton
@Requires(property = "repeatabletest", value = "true")
class MapOfListsBean5 {

    @Inject
    public Map<String, List<List<List<List<Map<String, @MyMin5 OptionalInt>>>>>> map;
}
