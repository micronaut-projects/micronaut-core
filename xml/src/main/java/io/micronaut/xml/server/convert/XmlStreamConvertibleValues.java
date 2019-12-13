/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.xml.server.convert;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.jackson.JacksonConfiguration;

import java.io.IOException;
import java.util.*;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * An implementation of {@link ConvertibleValues} backed by xml stream.
 *
 * @param <V> The generic type for values
 * @author sergey.vishnyakov
 * @since 1.2
 */
public class XmlStreamConvertibleValues<V> implements ConvertibleValues<V> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(XmlStreamConvertibleValues.class);

    private final ByteArrayXmlStreamReader stream;
    private final XmlMapper xmlMapper;
    private final ConversionService<?> conversionService;
    private final JsonNode objectNode;

    /**
     * @param stream    xml input stream
     * @param xmlMapper object mapper specializing on xml content
     * @throws IOException xml mapper fails to read xml stream
     */
    public XmlStreamConvertibleValues(ByteArrayXmlStreamReader stream,
                                      XmlMapper xmlMapper,
                                      ConversionService<?> conversionService) throws IOException {
        this.stream = stream;
        this.xmlMapper = xmlMapper;
        this.conversionService = conversionService;
        this.objectNode = xmlMapper.readTree(stream.getBytes());
    }

    @Override
    public Set<String> names() {
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, JsonNode> child : CollectionUtils.iteratorToSet(objectNode.fields())) {
            names.add(child.getKey());
            for (Map.Entry<String, JsonNode> grandChild : CollectionUtils.iteratorToSet(child.getValue().fields())) {
                names.add(grandChild.getKey());
            }
        }

        return Collections.unmodifiableSet(names);
    }

    @Override
    public Collection<V> values() {
        return (Collection<V>) Collections.unmodifiableSet(CollectionUtils.iteratorToSet(objectNode.iterator()));
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        int depth = 0;
        JavaType javaType = JacksonConfiguration.constructType(conversionContext.getArgument(), xmlMapper.getTypeFactory());
        String nameString = name.toString();
        try (ByteArrayXmlStreamReader streamReader = stream.reset()) {
            while (streamReader.hasNext()) {
                int token = streamReader.next();
                if (token == START_ELEMENT) {
                    depth++;
                    if (depth == 1 && nameString.equals(streamReader.getName().toString())) {
                        if (ClassUtils.isJavaBasicType(conversionContext.getArgument().getType())) {
                            String value = streamReader.getElementText();
                            return conversionService.convert(value, conversionContext);
                        } else {
                            return Optional.ofNullable(xmlMapper.readValue(streamReader, javaType));
                        }
                    }
                    if (depth == 0) {
                        for (int i = 0; i < streamReader.getAttributeCount(); ++i) {
                            String attr = streamReader.getAttributeLocalName(i);
                            if (attr.equals(nameString)) {
                                return conversionService.convert(streamReader.getAttributeValue(i), conversionContext);
                            }
                        }
                    }
                }
                if (token == END_ELEMENT) {
                    depth--;
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to retrieve {} field from xml stream", name, e);
        }

        return Optional.empty();
    }
}
