package io.micronaut.http.server.netty.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;

import static io.micronaut.jackson.codec.JsonMediaTypeCodec.CONFIGURATION_QUALIFIER;

/**
 * A factory to produce {@link MediaTypeCodec} for JSON and Jackson using a specified JsonView class.
 */
@Requires(beans = JacksonConfiguration.class)
@Requires(property = "jackson.json-view-enabled")
@Singleton
public class JsonViewMediaTypeCodecFactory {

    private final ObjectMapper objectMapper;
    private final ApplicationConfiguration applicationConfiguration;
    private final CodecConfiguration codecConfiguration;

    /**
     * @param objectMapper             To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     */
    public JsonViewMediaTypeCodecFactory(ObjectMapper objectMapper,
                              ApplicationConfiguration applicationConfiguration,
                              @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        this.objectMapper = objectMapper;
        this.applicationConfiguration = applicationConfiguration;
        this.codecConfiguration = codecConfiguration;
    }

    /**
     * Creates a {@link JsonMediaTypeCodec} for the view class (specified as the JsonView annotation value).
     * @param viewClass The view class
     * @return
     */
    public JsonMediaTypeCodec createJsonViewCodec(Class<?> viewClass) {
        ObjectMapper viewMapper = objectMapper.copy();
        viewMapper.setConfig(viewMapper.getSerializationConfig().withView(viewClass));
        return new JsonMediaTypeCodec(viewMapper, applicationConfiguration, codecConfiguration);
    }
}
