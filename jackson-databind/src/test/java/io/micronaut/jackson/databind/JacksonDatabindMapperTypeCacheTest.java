package io.micronaut.jackson.databind;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The reader and writer for a type are cached across calls. The cache is keyed by type, not by
 * {@link Argument} identity, and holds more than one type at a time.
 */
class JacksonDatabindMapperTypeCacheTest {
    private final JacksonDatabindMapper mapper = new JacksonDatabindMapper();

    @Test
    void sameArgumentInstanceHits() {
        Argument<Item> type = Argument.of(Item.class);
        assertSame(mapper.createWriter(type), mapper.createWriter(type));
        assertSame(mapper.createReader(type), mapper.createReader(type));
    }

    @Test
    void typeEqualArgumentsHit() {
        // Argument.ofInstance creates a new Argument for every call
        ObjectWriter writer = mapper.createWriter(Argument.ofInstance(new Item(1, "a")));
        assertSame(writer, mapper.createWriter(Argument.ofInstance(new Item(2, "b"))));
        assertSame(writer, mapper.createWriter(Argument.of(Item.class)));

        ObjectReader reader = mapper.createReader(Argument.of(Item.class));
        assertSame(reader, mapper.createReader(Argument.of(Item.class)));
    }

    @Test
    void typeEqualGenericArgumentsHit() {
        Argument<List<Item>> a = Argument.listOf(Item.class);
        Argument<List<Item>> b = Argument.listOf(Item.class);
        assertNotSame(a, b);
        assertSame(mapper.createWriter(a), mapper.createWriter(b));
        assertSame(mapper.createReader(a), mapper.createReader(b));
    }

    @Test
    void differentTypeArgumentsMiss() {
        assertNotSame(mapper.createWriter(Argument.listOf(Item.class)), mapper.createWriter(Argument.listOf(String.class)));
        assertNotSame(mapper.createReader(Argument.listOf(Item.class)), mapper.createReader(Argument.listOf(String.class)));
        assertNotSame(mapper.createWriter(Argument.of(Item.class)), mapper.createWriter(Argument.of(Other.class)));
    }

    @Test
    void severalTypesAreCachedAtOnce() {
        Argument<Item> item = Argument.of(Item.class);
        Argument<Other> other = Argument.of(Other.class);
        Argument<List<Item>> items = Argument.listOf(Item.class);
        Argument<Map<String, Other>> others = Argument.mapOf(String.class, Other.class);
        ObjectWriter itemWriter = mapper.createWriter(item);
        ObjectWriter otherWriter = mapper.createWriter(other);
        ObjectWriter itemsWriter = mapper.createWriter(items);
        ObjectWriter othersWriter = mapper.createWriter(others);
        ObjectReader itemReader = mapper.createReader(item);
        ObjectReader otherReader = mapper.createReader(other);
        for (int i = 0; i < 3; i++) {
            // alternate between the types: each keeps its own entry
            assertSame(itemWriter, mapper.createWriter(item));
            assertSame(otherWriter, mapper.createWriter(other));
            assertSame(itemsWriter, mapper.createWriter(Argument.listOf(Item.class)));
            assertSame(othersWriter, mapper.createWriter(Argument.mapOf(String.class, Other.class)));
            assertSame(itemReader, mapper.createReader(item));
            assertSame(otherReader, mapper.createReader(Argument.of(Other.class)));
        }
    }

    @Test
    void jsonViewIsPartOfTheKey() throws IOException {
        BeanIntrospection<Holder> introspection = BeanIntrospection.getIntrospection(Holder.class);
        Argument<Object> viewed = introspection.getRequiredProperty("viewed", Object.class).asArgument();
        Argument<Object> plain = introspection.getRequiredProperty("plain", Object.class).asArgument();
        assertEquals(Item.class, viewed.getType());
        assertEquals(Item.class, plain.getType());

        ObjectWriter viewedWriter = mapper.createWriter(viewed);
        ObjectWriter plainWriter = mapper.createWriter(plain);
        assertNotSame(viewedWriter, plainWriter);
        assertSame(viewedWriter, mapper.createWriter(viewed));
        assertSame(plainWriter, mapper.createWriter(plain));
        // an argument with the same type and no view shares the plain writer, not the viewed one
        assertSame(plainWriter, mapper.createWriter(Argument.of(Item.class)));
        assertNotSame(mapper.createReader(viewed), mapper.createReader(plain));

        // and the view is actually applied
        Item item = new Item(7, "seven");
        assertEquals("{\"id\":7}", new String(mapper.writeValueAsBytes(viewed, item), StandardCharsets.UTF_8));
        assertEquals("{\"id\":7,\"name\":\"seven\"}", new String(mapper.writeValueAsBytes(plain, item), StandardCharsets.UTF_8));
    }

    @Test
    void specializedMapperIgnoresTheCache() {
        JsonMapperSpecific specific = new JsonMapperSpecific(mapper.createSpecific(Argument.of(Item.class)));
        assertSame(specific.writer(Argument.of(Item.class)), specific.writer(Argument.of(Other.class)));
    }

    private record JsonMapperSpecific(io.micronaut.json.JsonMapper mapper) {
        ObjectWriter writer(Argument<?> type) {
            return ((JacksonDatabindMapper) mapper).createWriter(type);
        }
    }

    public static class Summary {
    }

    @Introspected
    public record Item(@JsonView(Summary.class) int id, String name) {
    }

    @Introspected
    public record Other(String value) {
    }

    @Introspected
    public record Holder(@JsonView(Summary.class) Item viewed, Item plain) {
    }
}
