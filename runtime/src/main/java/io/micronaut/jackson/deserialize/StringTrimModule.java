/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jackson.deserialize;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jackson.JacksonConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class StringTrimModule extends StdScalarDeserializer<String> {

    private final boolean trimStrings;

    public StringTrimModule() {
        super(String.class);

        this.trimStrings = false;
    }

    @Inject
    public StringTrimModule(JacksonConfiguration jacksonConfiguration) {
        super(String.class);

        this.trimStrings = jacksonConfiguration.isTrimStrings();
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (trimStrings) {
            return StringUtils.trimToNull(p.getValueAsString());
        }

        return p.getValueAsString();
    }
}
