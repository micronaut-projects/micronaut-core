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
package io.micronaut.http.form;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * A form does not have what the application asked for: a required field or file is missing, or
 * a field is not a file where a file was expected. Answered with 400.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class FormFieldException extends HttpStatusException {

    private final String fieldName;

    /**
     * @param fieldName The name of the field
     * @param message   The message
     */
    public FormFieldException(String fieldName, String message) {
        super(HttpStatus.BAD_REQUEST, message);
        this.fieldName = fieldName;
    }

    /**
     * @param fieldName The name of the field
     * @return The exception for a required field without a value
     */
    public static FormFieldException missingField(String fieldName) {
        return new FormFieldException(fieldName, "Required form field [" + fieldName + "] not specified");
    }

    /**
     * @param fieldName The name of the field
     * @return The exception for a required file that was not uploaded
     */
    public static FormFieldException missingFile(String fieldName) {
        return new FormFieldException(fieldName, "Required file [" + fieldName + "] not uploaded");
    }

    /**
     * @param fieldName The name of the field
     * @return The exception for a field that is not a file where a file was expected
     */
    public static FormFieldException notAFile(String fieldName) {
        return new FormFieldException(fieldName, "Form field [" + fieldName + "] was expected to be a file upload");
    }

    /**
     * @return The name of the field
     */
    public String getFieldName() {
        return fieldName;
    }
}
