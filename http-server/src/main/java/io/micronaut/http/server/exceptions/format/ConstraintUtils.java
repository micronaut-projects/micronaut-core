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
package io.micronaut.http.server.exceptions.format;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.problem.InvalidParam;

import javax.validation.ConstraintViolation;
import javax.validation.ElementKind;
import javax.validation.Path;
import java.util.Iterator;

/**
 * Utility class to parse errors given a {@link ConstraintViolation}.
 *
 * @author Sergio del Amo
 * @since 2.4.0
 */
public final class ConstraintUtils {

    /**
     * Constructor.
     */
    private ConstraintUtils() {

    }

    /**
     *
     * @param violation Constraint Violation
     * @return Returns the violation key and reason separated by :
     */
    @NonNull
    public static String message(@NonNull ConstraintViolation<?> violation) {
        Path propertyPath = violation.getPropertyPath();
        StringBuilder message = new StringBuilder();
        Iterator<Path.Node> i = propertyPath.iterator();
        while (i.hasNext()) {
            Path.Node node = i.next();
            if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.CONSTRUCTOR) {
                continue;
            }
            message.append(node.getName());
            if (i.hasNext()) {
                message.append('.');
            }
        }
        message.append(": ").append(violation.getMessage());
        return message.toString();
    }

    /**
     *
     * @param violation Constraint Violation
     * @return Invalid Param with violation key as the name and the violation messages as the reason.
     */
    @NonNull
    public static InvalidParam invalidParam(@NonNull ConstraintViolation<?> violation) {
        Path propertyPath = violation.getPropertyPath();
        StringBuilder message = new StringBuilder();
        Iterator<Path.Node> i = propertyPath.iterator();
        while (i.hasNext()) {
            Path.Node node = i.next();
            if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.CONSTRUCTOR) {
                continue;
            }
            message.append(node.getName());
            if (i.hasNext()) {
                message.append('.');
            }
        }
        return new InvalidParam(message.toString(), violation.getMessage());
    }
}
