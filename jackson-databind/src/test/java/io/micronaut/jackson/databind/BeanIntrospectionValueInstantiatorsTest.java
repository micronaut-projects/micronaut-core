package io.micronaut.jackson.databind;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.jackson.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.InjectableValues;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.std.StdValueInstantiator;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanIntrospectionValueInstantiatorsTest {

    private static volatile StackTraceElement[] lastCreation;

    private final JsonMapper mapper = new ObjectMapperFactory().jsonMapperBuilder(null, null).build();
    private final JsonMapper jacksonMapper = jacksonOnlyMapper();

    private static JsonMapper jacksonOnlyMapper() {
        JacksonConfiguration configuration = new JacksonConfiguration();
        configuration.setBeanIntrospectionCreators(false);
        return new ObjectMapperFactory().jsonMapperBuilder(configuration, null).build();
    }

    @Test
    void recordIsCreatedThroughTheIntrospection() {
        assertIntrospected(Item.class);
        String json = "{\"id\":1,\"name\":\"a\",\"enabled\":true,\"score\":2.5,\"tags\":[\"x\"]}";
        Item item = mapper.readValue(json, Item.class);
        assertTrue(lastCreatedByIntrospection(), () -> "created by " + Arrays.toString(lastCreation));
        assertEquals(new Item(1, "a", true, 2.5, List.of("x")), item);
        assertEquals(jacksonMapper.readValue(json, Item.class), item);
    }

    @Test
    void listOfRecords() {
        String json = "[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\",\"enabled\":true}]";
        List<Item> items = mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, Item.class));
        assertEquals(List.of(new Item(1, "a", false, 0, null), new Item(2, "b", true, 0, null)), items);
    }

    @Test
    void missingPropertiesGetJacksonDefaults() {
        Item item = mapper.readValue("{}", Item.class);
        assertTrue(lastCreatedByIntrospection());
        assertEquals(new Item(0, null, false, 0.0, null), item);
        assertEquals(jacksonMapper.readValue("{}", Item.class), item);
    }

    @Test
    void explicitNullForPrimitive() {
        String json = "{\"id\":null,\"name\":null}";
        // FAIL_ON_NULL_FOR_PRIMITIVES is enabled by default
        Exception expected = assertThrows(Exception.class, () -> jacksonMapper.readValue(json, Item.class));
        Exception actual = assertThrows(Exception.class, () -> mapper.readValue(json, Item.class));
        assertSame(expected.getClass(), actual.getClass());
        assertEquals(expected.getMessage(), actual.getMessage());

        JsonMapper lenient = mapper.rebuild().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
        JsonMapper lenientJackson = jacksonMapper.rebuild().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
        Item item = lenient.readValue(json, Item.class);
        assertTrue(lastCreatedByIntrospection());
        assertEquals(lenientJackson.readValue(json, Item.class), item);
    }

    @Test
    void renamedConstructorProperties() {
        assertIntrospected(Renamed.class);
        String json = "{\"first_name\":\"John\",\"years\":42}";
        Renamed renamed = mapper.readValue(json, Renamed.class);
        assertEquals("John", renamed.getName());
        assertEquals(42, renamed.getAge());
        assertTrue(renamed.createdByIntrospection);
    }

    @Test
    void classWithMissingPropertyFallsBackToJackson() {
        // a null argument of a class that is not a record stays with Jackson
        Renamed renamed = mapper.readValue("{\"years\":42}", Renamed.class);
        assertNull(renamed.getName());
        assertEquals(42, renamed.getAge());
        assertFalse(renamed.createdByIntrospection);
    }

    @Test
    void recordWithRenamedComponents() {
        assertIntrospected(RenamedRecord.class);
        RenamedRecord value = mapper.readValue("{\"n\":\"x\",\"v\":3}", RenamedRecord.class);
        assertEquals(new RenamedRecord("x", 3), value);
    }

    @Test
    void constructorExceptionIsReportedLikeJackson() {
        String json = "{\"value\":-1}";
        ValueInstantiationException expected = assertThrows(ValueInstantiationException.class, () -> jacksonMapper.readValue(json, Validated.class));
        ValueInstantiationException actual = assertThrows(ValueInstantiationException.class, () -> mapper.readValue(json, Validated.class));
        assertEquals(expected.getMessage(), actual.getMessage());
        assertInstanceOf(IllegalArgumentException.class, actual.getCause());
        assertEquals(expected.getCause().getMessage(), actual.getCause().getMessage());
        assertEquals(5, mapper.readValue("{\"value\":5}", Validated.class).value());
    }

    @Test
    void injectableValues() {
        assertIntrospected(WithInjectable.class);
        JsonMapper injecting = mapper.rebuild().injectableValues(new InjectableValues.Std().addValue("source", "injected")).build();
        WithInjectable value = injecting.readValue("{\"name\":\"n\"}", WithInjectable.class);
        assertEquals(new WithInjectable("n", "injected"), value);
    }

    @Test
    void factoryMethodStaysWithJackson() {
        assertNotIntrospected(FromFactory.class);
        assertEquals("a!", mapper.readValue("{\"name\":\"a\"}", FromFactory.class).name());
    }

    @Test
    void delegatingCreatorStaysWithJackson() {
        assertNotIntrospected(Delegating.class);
        assertEquals(new Delegating("abc"), mapper.readValue("\"abc\"", Delegating.class));
    }

    @Test
    void notIntrospectedStaysWithJackson() {
        assertNotIntrospected(NotIntrospected.class);
        assertEquals(new NotIntrospected("a"), mapper.readValue("{\"name\":\"a\"}", NotIntrospected.class));
    }

    @Test
    void jacksonCreatorDifferentFromIntrospectionConstructorStaysWithJackson() {
        assertNotIntrospected(TwoConstructors.class);
        TwoConstructors value = mapper.readValue("{\"a\":\"x\",\"b\":\"y\"}", TwoConstructors.class);
        assertEquals("x", value.a);
        assertEquals("y", value.b);
    }

    @Test
    void canBeDisabled() {
        assertSame(StdValueInstantiator.class, instantiator(jacksonMapper, Item.class).getClass());
        jacksonMapper.readValue("{\"id\":1}", Item.class);
        assertFalse(lastCreatedByIntrospection());
    }

    private void assertIntrospected(Class<?> type) {
        assertInstanceOf(BeanIntrospectionValueInstantiators.IntrospectedValueInstantiator.class, instantiator(mapper, type));
    }

    private void assertNotIntrospected(Class<?> type) {
        ValueInstantiator instantiator = instantiator(mapper, type);
        assertFalse(instantiator instanceof BeanIntrospectionValueInstantiators.IntrospectedValueInstantiator, String.valueOf(instantiator));
    }

    private static ValueInstantiator instantiator(JsonMapper mapper, Class<?> type) {
        DeserializationContext ctxt = mapper._deserializationContext();
        JavaType javaType = mapper.constructType(type);
        ValueDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(javaType);
        return ((ValueInstantiator.Gettable) deserializer).getValueInstantiator();
    }

    private static boolean lastCreatedByIntrospection() {
        return calledFromIntrospection(lastCreation);
    }

    private static boolean calledFromIntrospection(StackTraceElement[] stack) {
        return Arrays.stream(stack).anyMatch(e -> e.getClassName().endsWith("$Introspection"))
            && Arrays.stream(stack).noneMatch(e -> e.getClassName().startsWith("java.lang.invoke."));
    }

    @Introspected
    public record Item(int id, String name, boolean enabled, double score, List<String> tags) {
        public Item {
            lastCreation = new Throwable().getStackTrace();
        }
    }

    @Introspected
    public static final class Renamed {
        private final String name;
        private final int age;
        final boolean createdByIntrospection;

        @JsonCreator
        public Renamed(@JsonProperty("first_name") String name, @JsonProperty("years") int age) {
            this.name = name;
            this.age = age;
            this.createdByIntrospection = calledFromIntrospection(new Throwable().getStackTrace());
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    @Introspected
    public record RenamedRecord(@JsonProperty("n") String name, @JsonProperty("v") int value) {
    }

    @Introspected
    public record Validated(int value) {
        public Validated {
            if (value < 0) {
                throw new IllegalArgumentException("value must not be negative: " + value);
            }
        }
    }

    @Introspected
    public record WithInjectable(String name, @JacksonInject("source") String source) {
    }

    @Introspected
    public record FromFactory(String name) {
        @JsonCreator
        public static FromFactory of(@JsonProperty("name") String name) {
            return new FromFactory(name + "!");
        }
    }

    @Introspected
    public record Delegating(String value) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Delegating {
            // only carries the creator annotation
        }
    }

    public record NotIntrospected(String name) {
    }

    @Introspected
    public static final class TwoConstructors {
        final String a;
        final String b;

        @Creator
        public TwoConstructors(String a) {
            this(a, "default");
        }

        @JsonCreator
        public TwoConstructors(@JsonProperty("a") String a, @JsonProperty("b") String b) {
            this.a = a;
            this.b = b;
        }
    }
}
