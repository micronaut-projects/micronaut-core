package org.particleframework.jackson.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.type.Argument;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ArrayNodeToIterableConverter implements TypeConverter<ArrayNode, Iterable> {

    private final ObjectMapper objectMapper;
    private final ConversionService conversionService;

    public ArrayNodeToIterableConverter(ObjectMapper objectMapper, ConversionService conversionService) {
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Iterable> convert(ArrayNode node, Class<Iterable> targetType, ConversionContext context) {
            Map<String, Argument<?>> typeVariables = context.getTypeVariables();
            Class elementType = typeVariables.isEmpty() ? Map.class : typeVariables.values().iterator().next().getType();
            List results = new ArrayList();
            node.elements().forEachRemaining(jsonNode -> {
                Optional converted = conversionService.convert(jsonNode, elementType, context);
                if(converted.isPresent()) {
                    results.add(converted.get());
                }
            });
            return Optional.of(results);
    }

}