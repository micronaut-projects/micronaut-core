/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json;

import io.micronaut.core.annotation.Experimental;

/**
 * Configuration for json stream parsing and generation. Immutable.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Experimental
public final class JsonStreamConfig {
    /**
     * The default stream configuration.
     */
    public static final JsonStreamConfig DEFAULT = new JsonStreamConfig(false, false);

    private final boolean useBigDecimalForFloats;
    private final boolean useBigIntegerForInts;

    private JsonStreamConfig(boolean useBigDecimalForFloats, boolean useBigIntegerForInts) {
        this.useBigDecimalForFloats = useBigDecimalForFloats;
        this.useBigIntegerForInts = useBigIntegerForInts;
    }

    /**
     * @return Whether {@link java.math.BigDecimal}s should be used for parsing floating-point json numbers.
     */
    public boolean useBigDecimalForFloats() {
        return useBigDecimalForFloats;
    }

    /**
     * @param useBigDecimalForFloats The new value for {@link #useBigDecimalForFloats}
     * @return A copy of this config instance, with {@link #useBigDecimalForFloats} set to the new value.
     */
    public JsonStreamConfig withUseBigDecimalForFloats(boolean useBigDecimalForFloats) {
        return new JsonStreamConfig(useBigDecimalForFloats, useBigIntegerForInts);
    }

    /**
     * @return Whether {@link java.math.BigInteger}s should be used for parsing integer json numbers.
     */
    public boolean useBigIntegerForInts() {
        return useBigIntegerForInts;
    }

    /**
     * @param useBigIntegerForInts The new value for {@link #useBigIntegerForInts}
     * @return A copy of this config instance, with {@link #useBigIntegerForInts} set to the new value.
     */
    public JsonStreamConfig withUseBigIntegerForInts(boolean useBigIntegerForInts) {
        return new JsonStreamConfig(useBigDecimalForFloats, useBigIntegerForInts);
    }
}
