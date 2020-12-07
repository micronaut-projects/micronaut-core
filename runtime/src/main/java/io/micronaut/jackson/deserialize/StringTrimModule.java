package io.micronaut.jackson.deserialize;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;
import java.io.IOException;

@Singleton
@Requires(property = "jackson.trim-strings", value = StringUtils.TRUE)
public class StringTrimModule extends SimpleModule {

    public StringTrimModule() {
        addDeserializer(String.class, new StringDeserializer() {

            @Override
            public String deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
                String value = super.deserialize(jsonParser, context);

                return StringUtils.trimToNull(value);
            }
        });
    }
}
