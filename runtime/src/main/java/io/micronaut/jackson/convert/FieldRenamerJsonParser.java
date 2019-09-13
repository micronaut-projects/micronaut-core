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
package io.micronaut.jackson.convert;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;

import java.io.IOException;

/**
 * Wrapper over {@link com.fasterxml.jackson.core.JsonParser} that overrides root field name with the given one.
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
 *     "state": "VA"
 * }
 * }</pre>
 *
 * @author sergey.vishnyakov
 */
class FieldRenamerJsonParser extends JsonParserDelegate {

    private final String fieldName;
    private final String newFieldName;
    private int depth = 0;

    /**
     * @param fieldName    root field name you want to override
     * @param newFieldName new field name
     * @param parser       original parser most of the logic will be delegated to
     */
    FieldRenamerJsonParser(String fieldName, String newFieldName, FromXmlParser parser) {
        super(parser);
        this.fieldName = fieldName;
        this.newFieldName = newFieldName;
    }

    @Override
    public JsonToken nextToken() throws IOException {
        JsonToken token = super.nextToken();

        if (token == JsonToken.START_OBJECT) {
            depth++;
        } else if (token == JsonToken.END_OBJECT) {
            depth--;
        }

        if (token == JsonToken.FIELD_NAME && depth == 1) {
            String currentFieldName = getCurrentName();
            if (currentFieldName.equals(fieldName)) {
                overrideCurrentName(newFieldName);
            }
        }

        return token;
    }
}
