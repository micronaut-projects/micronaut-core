package io.micronaut.jackson.databind;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
class GraphQLResponseBodyTest {
    @Test
    void testRawJackson() throws JacksonException {
        ObjectMapper objectMapper = new ObjectMapper();
        String result = objectMapper.writeValueAsString(
            new GraphQLResponseBody(Map.of("data", "test")));
        assertEquals("""
            {"data":"test"}""", result);
    }

    @Test
    void testInjectedJackson() throws JacksonException {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ObjectMapper objectMapper = ctx.getBean(ObjectMapper.class);
            String result = objectMapper.writeValueAsString(
                new GraphQLResponseBody(Map.of("data", "test")));
            assertEquals("""
            {"data":"test"}""", result);
        }
    }

    @Test
    void testInjectedJsonMapper() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            JsonMapper objectMapper = ctx.getBean(JsonMapper.class);
            String result = objectMapper.writeValueAsString(
                new GraphQLResponseBody(Map.of("data", "test")));
            assertEquals("""
            {"data":"test"}""", result);
        }
    }
}
