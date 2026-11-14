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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;

/**
 * How {@link AsyncRequestBody#form(FormOptions)} reads a form.
 *
 * <pre>{@code
 * body.form(FormOptions.defaults().withRawValues())
 *     .thenApply(form -> form.getString("path")); // "a%2Fb" for path=a%2Fb
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface FormOptions permits DefaultFormOptions {

    /**
     * @return The options of {@link AsyncRequestBody#form()}: the values are decoded
     */
    static FormOptions defaults() {
        return DefaultFormOptions.DEFAULTS;
    }

    /**
     * Options that keep the values of the fields of an {@code application/x-www-form-urlencoded}
     * form as they appear in the body, not percent-decoded, e.g. {@code a%2Fb}, a {@code +} that
     * is not turned into a space, and the percent-encoded bytes of a non-ASCII value: what a
     * framework needs for a field it must not decode, e.g. a JAX-RS {@code @Encoded} form
     * parameter. The names of the fields are decoded. A {@code multipart/form-data} form has no
     * percent-encoding: it is read as with the defaults.
     *
     * @return The options with raw values
     */
    FormOptions withRawValues();

    /**
     * @return Whether the values of an {@code application/x-www-form-urlencoded} form are kept as
     * they appear in the body, see {@link #withRawValues()}
     */
    boolean rawValues();

    /**
     * Options that read an {@code application/x-www-form-urlencoded} form with the decoded values
     * of its fields, like the defaults, and their values as they appear in the body as its
     * {@link io.micronaut.http.form.FormData#raw() raw view}, from one read of the body: what a
     * framework needs that reads some fields decoded and others raw, e.g. JAX-RS {@code @FormParam}
     * with and without {@code @Encoded}. The form is read from the bytes like with
     * {@link #withRawValues()}, with its rules: it is not the shared form of the request. A
     * {@code multipart/form-data} form is read as with the defaults, and is its own raw view.
     *
     * <pre>{@code
     * body.form(FormOptions.defaults().withRawView())
     *     .thenApply(form -> form.getString("path") + " " + form.raw().getString("path")); // "a/b a%2Fb"
     * }</pre>
     *
     * @return The options with the decoded values and a raw view
     * @since 5.3.0
     */
    FormOptions withRawView();

    /**
     * @return Whether the form has the decoded values with the raw ones as its raw view, see
     * {@link #withRawView()}
     * @since 5.3.0
     */
    boolean rawView();
}
