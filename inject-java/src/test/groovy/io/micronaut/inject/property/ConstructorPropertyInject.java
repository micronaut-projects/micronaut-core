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
package io.micronaut.inject.property;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.convert.format.MapFormat;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class ConstructorPropertyInject {

    private final Map<String, String> values;
    private String str;
    private int integer;
    private String nullable;

    public ConstructorPropertyInject(
            @Property(name = "my.map")
            @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
            Map<String, String> values,
            @Property(name = "my.string")
            String str,
            @Property(name = "my.int")
            int integer,
            @Property(name = "my.nullable")
            @Nullable
            String nullable) {
        this.values = values;
        this.str = str;
        this.integer = integer;
        this.nullable = nullable;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public String getStr() {
        return str;
    }

    public int getInteger() {
        return integer;
    }

    public String getNullable() {
        return nullable;
    }
}
