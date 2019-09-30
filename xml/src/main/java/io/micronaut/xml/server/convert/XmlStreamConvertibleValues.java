/*
 *
 *  * Copyright 2017-2019 original authors
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package io.micronaut.xml.server.convert;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An implementation of {@link ConvertibleValues} backed by xml stream.
 *
 * @param <V> The generic type for values
 * @author sergey.vishnyakov
 * @since 1.2
 */
public class XmlStreamConvertibleValues<V> implements ConvertibleValues<V> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlStreamConvertibleValues.class);

    private final ByteArrayXmlStreamReader stream;
    private final XmlMapper xmlMapper;
    private final JsonNode objectNode;

    /**
     * @param stream    xml input stream
     * @param xmlMapper object mapper specializing on xml content
     * @throws IOException xml mapper fails to read xml stream
     */
    public XmlStreamConvertibleValues(ByteArrayXmlStreamReader stream,
                                      XmlMapper xmlMapper) throws IOException {
        this.stream = stream;
        this.xmlMapper = xmlMapper;
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
        try {
            FromXmlParser parser = xmlMapper.getFactory().createParser(stream.copy());
            FieldRenamerJsonParser fieldParser = new FieldRenamerJsonParser(
                    name.toString(), ParsedValueHolder.VALUE_FIELD_NAME, parser
            );

            JavaType javaType = xmlMapper.getTypeFactory()
                    .constructParametricType(ParsedValueHolder.class, conversionContext.getArgument().getType());

            ParsedValueHolder<T> valueHolder = xmlMapper.readValue(fieldParser, javaType);
            return Optional.of(valueHolder.getValue());

        } catch (Exception e) {
            LOG.error("Failed to deserialize value holder", e);
        }

        return Optional.empty();
    }

    /**
     * Type that works as a selector for a single field from xml stream. The algorithm works as following: <pre>
     * 1. Modify xml stream and rename the field we are interested in to  "noWayYouCanNameFieldLikeThis"
     * 2. Parse xml stream as {@link ParsedValueHolder}.
     * 3. {@link ParsedValueHolder} will contain a single field we are interested in and nothing else
     * </pre>
     *
     * @param <T> type of the selected field.
     */
    static class ParsedValueHolder<T> {
        static final String VALUE_FIELD_NAME = "noWayYouCanNameFieldLikeThis";
        private final T value;

        /**
         * @param fieldValue the value of a selected field
         */
        @JsonCreator
        ParsedValueHolder(@JsonProperty(VALUE_FIELD_NAME) T fieldValue) {
            value = fieldValue;
        }

        /**
         * @return selected field value
         */
        T getValue() {
            return this.value;
        }
    }
}
