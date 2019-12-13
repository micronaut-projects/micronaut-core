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
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.jackson.JacksonConfiguration;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * An implementation of {@link ConvertibleValues} backed by xml stream.
 *
 * @param <V> The generic type for values
 * @author Sergey Vishnyakov
 * @author James Kleeh
 * @since 1.3.0
 */
public class XmlStreamConvertibleValues<V> implements ConvertibleValues<V> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(XmlStreamConvertibleValues.class);

    private final ByteArrayXmlStreamReader stream;
    private final XmlMapper xmlMapper;
    private final ConversionService<?> conversionService;
    private final Supplier<JsonNode> objectNode;

    /**
     * @param stream            The XML input stream
     * @param xmlMapper         The Jackson XML Mapper
     * @param conversionService The conversion service
     */
    public XmlStreamConvertibleValues(ByteArrayXmlStreamReader stream,
                                      XmlMapper xmlMapper,
                                      ConversionService<?> conversionService) {
        this.stream = stream;
        this.xmlMapper = xmlMapper;
        this.conversionService = conversionService;
        this.objectNode = SupplierUtil.memoized(() -> {
            try {
                return xmlMapper.readTree(stream.getBytes());
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Failed to read the xml stream as a tree", e);
                }
                return null;
            }
        });
    }

    @Override
    public Set<String> names() {
        JsonNode jsonNode = objectNode.get();
        if (jsonNode != null) {
            Iterator<String> fieldNames = objectNode.get().fieldNames();
            return CollectionUtils.iteratorToSet(fieldNames);
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Collection<V> values() {
        JsonNode jsonNode = objectNode.get();
        if (jsonNode != null) {
            List<V> values = new ArrayList<>();
            for (JsonNode node : jsonNode) {
                values.add((V) node);
            }
            return Collections.unmodifiableCollection(values);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        Class<T> type = conversionContext.getArgument().getType();
        //Necessary to process the XML this way for collections because the JsonNode
        //will only keep the last item due to the key being duplicated
        if (Collection.class.isAssignableFrom(type)) {
            int depth = -1;
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
                if (LOG.isErrorEnabled()) {
                    LOG.error("Failed to retrieve {} field from xml stream", name, e);
                }
            }

            return Optional.empty();
        } else {
            JsonNode node = objectNode.get();
            if (node != null) {
                return conversionService.convert(node.get(name.toString()), conversionContext);
            } else  {
                return Optional.empty();
            }
        }
    }
}
