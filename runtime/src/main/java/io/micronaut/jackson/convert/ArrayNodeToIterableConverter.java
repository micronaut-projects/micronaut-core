package io.micronaut.jackson.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;

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