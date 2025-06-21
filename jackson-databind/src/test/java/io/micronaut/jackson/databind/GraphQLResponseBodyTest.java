package io.micronaut.jackson.databind;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
class GraphQLResponseBodyTest {
    @Test
    void testRawJackson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String result = objectMapper.writeValueAsString(
            new GraphQLResponseBody(Map.of("data", "test")));
        assertEquals("""
            {"data":"test"}""", result);
    }

    @Test
    void testInjectedJackson() throws JsonProcessingException {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ObjectMapper objectMapper = ctx.getBean(ObjectMapper.class);
            String result = objectMapper.writeValueAsString(
                new GraphQLResponseBody(Map.of("data", "test")));
            assertEquals("""
            {"data":"test"}""", result);
        }
    }
}
