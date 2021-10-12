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
package io.micronaut.core.convert.exceptions;

import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.type.Argument;

/**
 * An exception thrown in the case of a {@link io.micronaut.core.convert.ConversionError}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConversionErrorException extends RuntimeException {

    private final Argument argument;
    private final ConversionError conversionError;

    /**
     * @param argument        The argument
     * @param conversionError The conversion error
     */
    public ConversionErrorException(Argument argument, ConversionError conversionError) {
        super(buildMessage(argument, conversionError), conversionError.getCause());
        this.argument = argument;
        this.conversionError = conversionError;
    }

    /**
     * @param argument The argument
     * @param cause    The cause
     */
    public ConversionErrorException(Argument argument, Exception cause) {
        super(cause.getMessage(), cause);
        this.argument = argument;
        this.conversionError = () -> cause;
    }

    /**
     * @return The argument that failed conversion.
     */
    public Argument getArgument() {
        return argument;
    }

    /**
     * @return The conversion error
     */
    public ConversionError getConversionError() {
        return conversionError;
    }

    private static String buildMessage(Argument argument, ConversionError conversionError) {
        return String.format("Failed to convert argument [%s] for value [%s] due to: %s", argument.getName(), conversionError.getOriginalValue().orElse(null), conversionError.getCause().getMessage());
    }
}
