package org.particleframework.configuration.jackson.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts {@link ArrayNode} instances to arrays
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ArrayNodeToArrayConverter implements TypeConverter<ArrayNode, Object[]> {

    private final ObjectMapper objectMapper;

    public ArrayNodeToArrayConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public Optional<Object[]> convert(ArrayNode node, Class<Object[]> targetType, ConversionContext context) {
        try {
                Object[] result = objectMapper.treeToValue(node, targetType);
                return Optional.of(result);
        } catch (JsonProcessingException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
