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
package io.micronaut.python.annotation.processing.test.classargs;

import java.util.Collection;

/**
 * The static factory overloads of a vector type: primitive varargs of several widths and a
 * collection.
 */
public interface VectorLike {

    static VectorLike of(float... values) {
        double[] doubles = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            doubles[i] = values[i];
        }
        return new Impl("float", doubles);
    }

    static VectorLike of(double... values) {
        return new Impl("double", values.clone());
    }

    static VectorLike of(Collection<? extends Number> values) {
        double[] doubles = new double[values.size()];
        int i = 0;
        for (Number value : values) {
            doubles[i++] = value.doubleValue();
        }
        return new Impl("collection", doubles);
    }

    static VectorLike of(byte... values) {
        double[] doubles = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            doubles[i] = values[i];
        }
        return new Impl("byte", doubles);
    }

    /**
     * @return Whether Java assertions are enabled in this JVM, as they are in a Gradle test JVM
     */
    static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    String kind();

    double[] toDoubleArray();

    record Impl(String kind, double[] toDoubleArray) implements VectorLike {
    }
}
