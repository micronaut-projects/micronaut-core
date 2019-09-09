package io.micronaut.jackson.convert;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import java.io.IOException;
import java.util.Optional;

@Singleton
@Internal
@Requires(classes = XmlMapper.class)
public class XmlStreamToObjectConverter implements TypeConverter<XmlStreamToObjectConverter.ByteArrayXmlStreamReader, Object> {

    private final XmlMapper xmlMapper;
    private final ConversionService<?> conversionService;

    public XmlStreamToObjectConverter(@Named("xml") ObjectMapper xmlMapper,
                                      ConversionService<?> conversionService) {
        this.xmlMapper = (XmlMapper) xmlMapper;
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Object> convert(ByteArrayXmlStreamReader stream, Class<Object> targetType, ConversionContext context) {
        try {
            if (ConvertibleValues.class.isAssignableFrom(targetType)) {
                Object pojo = xmlMapper.readTree((stream).bytes);
                ObjectNode objectNode = xmlMapper.valueToTree(pojo);
                return Optional.of(new XmlNodeConvertibleValues<>(objectNode, conversionService));
            } else {
                return Optional.of(xmlMapper.readValue(stream, targetType));
            }
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }

    public static class ByteArrayXmlStreamReader extends StreamReaderDelegate {
        private byte[] bytes;

        public ByteArrayXmlStreamReader(XMLStreamReader reader, byte[] bytes) {
            super(reader);
            this.bytes = bytes;
        }
    }
}
