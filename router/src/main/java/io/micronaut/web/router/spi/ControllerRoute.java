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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * The static attributes of the route of a controller method in a {@link RouteSlot}: a symbolic
 * reference to the executable method, never a live bean, and what the route builder would derive
 * from the annotations at runtime.
 *
 * @param ownerType           The name of the controller type
 * @param methodName          The name of the method
 * @param argumentTypes       The erased argument types, as {@link Class#getName()} returns them
 * @param declaringTypeTarget Whether the route targets the method through its declaring type rather than through the bean definition
 * @param consumes            The consumed media types, or {@code null} for the default
 * @param produces            The produced media types, or {@code null} for the default
 * @param implicitHead        Whether this is the implicit {@code HEAD} route of a {@code GET} method
 * @param port                The port the route is exposed on, or {@code -1}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record ControllerRoute(String ownerType,
                              String methodName,
                              String[] argumentTypes,
                              boolean declaringTypeTarget,
                              String @Nullable [] consumes,
                              String @Nullable [] produces,
                              boolean implicitHead,
                              int port) {

    /**
     * @param ownerType           The name of the controller type
     * @param methodName          The name of the method
     * @param argumentTypes       The erased argument types
     * @param declaringTypeTarget Whether the route targets the declaring type
     * @param consumes            The consumed media types, or {@code null}
     * @param produces            The produced media types, or {@code null}
     * @param implicitHead        Whether this is an implicit {@code HEAD} route
     * @param port                The port, or {@code -1}
     */
    @UsedByGeneratedCode
    public ControllerRoute(String ownerType,
                           String methodName,
                           String[] argumentTypes,
                           boolean declaringTypeTarget,
                           String @Nullable [] consumes,
                           String @Nullable [] produces,
                           boolean implicitHead,
                           int port) {
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.argumentTypes = Objects.requireNonNull(argumentTypes, "argumentTypes");
        this.declaringTypeTarget = declaringTypeTarget;
        this.consumes = consumes;
        this.produces = produces;
        this.implicitHead = implicitHead;
        this.port = port;
    }

    void appendCanonical(StringBuilder builder) {
        char s = '\u001f';
        builder.append(ownerType).append(s).append(methodName).append(s).append(String.join(",", argumentTypes)).append(s)
            .append(declaringTypeTarget).append(s).append(consumes == null ? "-" : String.join(",", consumes)).append(s)
            .append(produces == null ? "-" : String.join(",", produces)).append(s).append(implicitHead).append(s).append(port);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ControllerRoute other && ownerType.equals(other.ownerType) && methodName.equals(other.methodName)
            && Arrays.equals(argumentTypes, other.argumentTypes) && declaringTypeTarget == other.declaringTypeTarget
            && Arrays.equals(consumes, other.consumes) && Arrays.equals(produces, other.produces)
            && implicitHead == other.implicitHead && port == other.port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerType, methodName, Arrays.hashCode(argumentTypes));
    }

    @Override
    public String toString() {
        return ownerType + '#' + methodName + '(' + String.join(",", argumentTypes) + ')';
    }
}
