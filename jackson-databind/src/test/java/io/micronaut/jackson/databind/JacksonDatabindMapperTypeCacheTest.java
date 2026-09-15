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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        // the slot is derived from Class.hashCode(), an identity hash, so which types share a
        // slot varies between JVM runs: pick four candidates that land in distinct slots
        List<Argument<?>> candidates = List.of(
            Argument.of(Item.class),
            Argument.of(Other.class),
            Argument.listOf(Item.class),
            Argument.mapOf(String.class, Other.class),
            Argument.listOf(Other.class),
            Argument.mapOf(String.class, Item.class),
            Argument.setOf(Item.class),
            Argument.of(String.class),
            Argument.listOf(String.class),
            Argument.mapOf(String.class, String.class)
        );
        List<Argument<?>> types = new ArrayList<>();
        Set<Integer> slots = new HashSet<>();
        for (Argument<?> candidate : candidates) {
            if (slots.add(JacksonDatabindMapper.slotOf(candidate))) {
                types.add(candidate);
            }
            if (types.size() == 4) {
                break;
            }
        }
        assumeTrue(types.size() == 4, "fewer than four of the candidate types land in distinct slots");
        List<ObjectWriter> writers = new ArrayList<>();
        List<ObjectReader> readers = new ArrayList<>();
        for (Argument<?> type : types) {
            writers.add(mapper.createWriter(type));
            readers.add(mapper.createReader(type));
        }
        for (int i = 0; i < 3; i++) {
            // alternate between the types: each keeps its own entry
            for (int t = 0; t < types.size(); t++) {
                Argument<?> type = types.get(t);
                // an equal, distinct Argument instance hits the same entry
                Argument<?> equal = Argument.of(type.getType(), type.getTypeParameters());
                assertSame(writers.get(t), mapper.createWriter(type));
                assertSame(writers.get(t), mapper.createWriter(equal));
                assertSame(readers.get(t), mapper.createReader(type));
                assertSame(readers.get(t), mapper.createReader(equal));
            }
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
