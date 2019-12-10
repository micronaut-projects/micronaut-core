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

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Wrapper over {@link com.fasterxml.jackson.core.JsonParser} that allows for selecting one element under the root and ignore
 * everything else.
 *
 * The main purpose of this implementation is to allow controller argument binding from xml requests.
 *
 * <p>
 * For example if original json was like bellow: <pre>{@code
 * {
 *     "name" : "Fred",
 *     "state": "VA"
 * }
 * }</pre>
 * <p>
 * After applying the decorator with the target field to be renamed "name", the json might be transformed to <pre>{@code
 * {
 *     "modifiedName" : "Fred",
 * }
 * }</pre>
 *
 * @author sergey.vishnyakov
 * @since 1.3.0
 */
class SelectiveJsonParser extends JsonParserDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(SelectiveJsonParser.class);

    private final String fieldName;
    private final String newFieldName;
    private int depth = 0;

    /**
     * @param fieldName    field name under the root you want to override
     * @param newFieldName new field name
     * @param parser       original parser most of the logic will be delegated to
     */
    SelectiveJsonParser(String fieldName, String newFieldName, FromXmlParser parser) {
        super(parser);
        this.fieldName = fieldName;
        this.newFieldName = newFieldName;
    }

    @Override
    public JsonToken nextToken() throws IOException {
        JsonToken token = super.nextToken();

        if (depth == 1) {
            while (token == JsonToken.FIELD_NAME && !getCurrentName().equals(fieldName)) {
                LOG.trace("Field ignored: [{}]", getCurrentName());
                /*
                 Skipping content of the fields we are not interested in.
                 any field that does not match "target" and any tag under the root that does not
                 have "target" name will be ignored.
                */
                super.nextToken();
                super.skipChildren();
                token = super.nextToken();
            }

            if (token == JsonToken.FIELD_NAME) {
                LOG.trace("Overriding field [{}]", getCurrentName());
                overrideCurrentName(newFieldName);
            }
        }

        if (token == null) {
            return null;
        }
        if (token.isStructStart()) {
            depth++;
        } else if (token.isStructEnd()) {
            depth--;
        }

        return token;
    }
}
