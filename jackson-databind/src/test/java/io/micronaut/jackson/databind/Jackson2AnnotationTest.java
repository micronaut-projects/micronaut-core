package io.micronaut.jackson.databind;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Jackson2AnnotationTest {

    @Test
    public void test() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            JsonMapper mapper = ctx.getBean(JsonMapper.class);
            assertEquals("fizz", Objects.requireNonNull(mapper.readValue("{\"bar\":\"fizz\"}", MyClass.class)).foo);
        }
    }

    @JsonDeserialize(builder = MyClass.Builder.class)
    static final class MyClass {
        final String foo;

        private MyClass(String foo) {
            this.foo = foo;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            String foo;

            public Builder bar(String bar) {
                this.foo = bar;
                return this;
            }

            public MyClass build() {
                return new MyClass(foo);
            }
        }
    }
}
