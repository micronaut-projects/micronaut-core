package io.micronaut.http.bind;

import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.PathVariables;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchedPathVariablesTest {

    private static final UUID ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    private final PathVariables pathVariables = new MatchedPathVariables(
        Map.of("id", "5", "ratio", "1.5", "flag", "true", "uuid", ID.toString(), "tags", "a,b,c", "numbers", "1,2,3", "letter", "x"),
        ConversionService.SHARED);

    @Test
    void namesAndContains() {
        assertEquals(Set.of("id", "ratio", "flag", "uuid", "tags", "numbers", "letter"), pathVariables.names());
        assertTrue(pathVariables.contains("id"));
        assertFalse(pathVariables.contains("other"));
    }

    @Test
    void typedAccessors() {
        assertEquals("5", pathVariables.getString("id"));
        assertEquals(5, pathVariables.getInt("id"));
        assertEquals(5L, pathVariables.getLong("id"));
        assertEquals((short) 5, pathVariables.getShort("id"));
        assertEquals((byte) 5, pathVariables.getByte("id"));
        assertEquals(1.5d, pathVariables.getDouble("ratio"));
        assertEquals(1.5f, pathVariables.getFloat("ratio"));
        assertTrue(pathVariables.getBoolean("flag"));
        assertEquals('x', pathVariables.getChar("letter"));
        assertEquals(ID, pathVariables.get("uuid", UUID.class));
        assertEquals(Optional.of(ID), pathVariables.find("uuid", UUID.class));
    }

    @Test
    void defaultsAndOptionals() {
        assertEquals(7, pathVariables.getInt("other", 7));
        assertEquals(5, pathVariables.getInt("id", 7));
        assertEquals(8L, pathVariables.getLong("other", 8L));
        assertEquals("none", pathVariables.getString("other", "none"));
        assertEquals(OptionalInt.of(5), pathVariables.findInt("id"));
        assertEquals(OptionalInt.empty(), pathVariables.findInt("other"));
        assertEquals(OptionalLong.of(5L), pathVariables.findLong("id"));
        assertEquals(OptionalLong.empty(), pathVariables.findLong("other"));
        assertEquals(OptionalDouble.of(1.5d), pathVariables.findDouble("ratio"));
        assertEquals(OptionalDouble.empty(), pathVariables.findDouble("other"));
        assertEquals(Optional.of(true), pathVariables.findBoolean("flag"));
        assertEquals(Optional.empty(), pathVariables.findString("other"));
    }

    @Test
    void lists() {
        assertEquals(List.of("a", "b", "c"), pathVariables.getStrings("tags"));
        assertEquals(List.of(1, 2, 3), pathVariables.getList("numbers", Integer.class));
        assertEquals(List.of(1, 2, 3), pathVariables.get("numbers", Argument.listOf(Integer.class)));
        assertEquals(List.of("5"), pathVariables.getStrings("id"));
        assertEquals(Optional.of(List.of(1, 2, 3)), pathVariables.findList("numbers", Integer.class));
        assertEquals(Optional.empty(), pathVariables.findList("other", Integer.class));
    }

    @Test
    void aMissingVariableIsUnsatisfied() {
        UnsatisfiedArgumentException e = assertThrows(UnsatisfiedArgumentException.class, () -> pathVariables.getInt("other"));
        assertTrue(e.getMessage().contains("Required PathVariable [other] not specified"), e.getMessage());
        assertThrows(UnsatisfiedArgumentException.class, () -> pathVariables.getStrings("other"));
    }

    @Test
    void aValueThatDoesNotConvertIsAConversionError() {
        ConversionErrorException e = assertThrows(ConversionErrorException.class, () -> pathVariables.getLong("tags"));
        assertEquals("tags", e.getArgument().getName());
        assertThrows(ConversionErrorException.class, () -> pathVariables.findInt("flag"));
        assertThrows(ConversionErrorException.class, () -> pathVariables.getInt("uuid", 0));
    }

    @Test
    void theValuesAreReadOnly() {
        MatchedPathVariables matched = (MatchedPathVariables) pathVariables;
        assertThrows(UnsupportedOperationException.class, () -> matched.values().put("id", "6"));
        assertEquals("PathVariables{id=5}", new MatchedPathVariables(Map.of("id", "5"), ConversionService.SHARED).toString());
    }
}
