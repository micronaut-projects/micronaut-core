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
package io.micronaut.python.processing.model;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;

import java.util.Objects;

/**
 * One argument of the {@code super().__init__(...)} call in a Python constructor.
 * <p>
 * The processor records what it can tell about the argument without running the code:
 * a reference to a constructor parameter, a literal (or f-string) with its Python type, a call
 * of a class, or nothing at all. The stub generator uses the recorded type to pick the Java
 * super constructor that the argument reaches when the Python class extends a Java class.
 * </p>
 *
 * @param source The argument source text, for diagnostics
 * @param keyword The keyword when the argument was passed by keyword, otherwise {@code null}
 * @param parameterName The name of the constructor parameter the argument refers to, otherwise {@code null}
 * @param type The static Python type of the argument when it is known ({@code str}, {@code int},
 * {@code float}, {@code bool}, {@code None} or the called class), otherwise {@code null}
 * @param conflictingCall The source of another {@code super().__init__(...)} call of the same constructor
 * that passes different arguments, otherwise {@code null}
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public record SuperArgumentDef(
    String source,
    @Nullable String keyword,
    @Nullable String parameterName,
    @Nullable TypeRef type,
    @Nullable String conflictingCall
) {

    public SuperArgumentDef {
        Objects.requireNonNull(source, "Argument source cannot be null");
    }

    public SuperArgumentDef(String source, @Nullable String keyword, @Nullable String parameterName, @Nullable TypeRef type) {
        this(source, keyword, parameterName, type, null);
    }

    /**
     * An argument that refers to a constructor parameter.
     *
     * @param source The source text
     * @param parameterName The parameter name
     * @return The argument
     */
    public static SuperArgumentDef parameter(String source, String parameterName) {
        return new SuperArgumentDef(source, null, parameterName, null);
    }

    /**
     * An argument of a known static type.
     *
     * @param source The source text
     * @param type The Python type
     * @return The argument
     */
    public static SuperArgumentDef typed(String source, TypeRef type) {
        return new SuperArgumentDef(source, null, null, type);
    }

    /**
     * An argument whose type the processor cannot tell.
     *
     * @param source The source text
     * @return The argument
     */
    public static SuperArgumentDef unknown(String source) {
        return new SuperArgumentDef(source, null, null, null);
    }

    /**
     * An argument passed by keyword.
     *
     * @param source The source text
     * @param keyword The keyword
     * @return The argument
     */
    public static SuperArgumentDef keyword(String source, String keyword) {
        return new SuperArgumentDef(source, keyword, null, null);
    }

    /**
     * The record of a constructor calling {@code super().__init__(...)} in more than one way: the
     * arguments of the first call, with the source of a differing call. A Python class extending a
     * Java class has one Java super constructor, so the calls have to agree.
     *
     * @param source The source of the first call
     * @param conflictingCall The source of a call that differs
     * @return The argument
     */
    public static SuperArgumentDef conflicting(String source, String conflictingCall) {
        return new SuperArgumentDef(source, null, null, null, conflictingCall);
    }

    /**
     * @return Whether another {@code super().__init__(...)} call of the constructor passes different arguments
     */
    public boolean isConflicting() {
        return conflictingCall != null;
    }

    /**
     * @return Whether the argument spreads the constructor's own {@code *args} or {@code **kwargs} into the call
     */
    public boolean isSpread() {
        return "*".equals(keyword) || "**".equals(keyword);
    }

    /**
     * @return Whether the argument refers to a constructor parameter
     */
    public boolean isParameter() {
        return parameterName != null;
    }

    /**
     * @return Whether the argument was passed by keyword
     */
    public boolean isKeyword() {
        return keyword != null;
    }
}
