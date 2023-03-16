package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.Introspected;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class MapOfListsWithAutomaticUnwrapping {

    private Map<String, List<@MyMin OptionalInt>> map;

}
