/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.compiler;

import java.util.StringJoiner;

public final class InstanceAliasFixture {
    private String name = "Ada";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String withValue(String value) {
        return "text:" + value;
    }

    public String withValue(int value) {
        return "number:" + value;
    }

    public void getVoid() {
        // A zero-argument void method must not become a property.
    }

    public String getWrong(int value) {
        return "wrong:" + value;
    }

    public Middle middle() {
        return new Middle();
    }

    public static final class Middle {
        public Leaf leaf() {
            return new Leaf();
        }
    }

    public static final class Leaf {
        public String getValue() {
            return "deep";
        }
    }

    public String getHTTP2Status() {
        return "HTTP2";
    }

    @SuppressWarnings("java:S100")
    public String with__Value() {
        return "underscores";
    }

    public StringJoiner getHTTPStatus() {
        return new StringJoiner(",");
    }

    public boolean isReady() {
        return true;
    }

    public String getURL() {
        return "URL";
    }

    public String getUrl() {
        return "Url";
    }

    @SuppressWarnings("java:S100")
    public String literal_value(String value) {
        return value;
    }

    public int literalValue(int value) {
        return value;
    }

    public static String getStaticValue() {
        return "static";
    }

    public String mixedValue(String value) {
        return value;
    }

    public static int mixedValue(int value) {
        return value;
    }
}
