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

import io.micronaut.core.annotation.Internal;

/**
 * The {@link FormOptions}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFormOptions implements FormOptions {

    static final DefaultFormOptions DEFAULTS = new DefaultFormOptions(false, false);
    private static final DefaultFormOptions RAW = new DefaultFormOptions(true, false);
    private static final DefaultFormOptions RAW_VIEW = new DefaultFormOptions(false, true);

    private final boolean rawValues;
    private final boolean rawView;

    private DefaultFormOptions(boolean rawValues, boolean rawView) {
        this.rawValues = rawValues;
        this.rawView = rawView;
    }

    @Override
    public FormOptions withRawValues() {
        return RAW;
    }

    @Override
    public boolean rawValues() {
        return rawValues;
    }

    @Override
    public FormOptions withRawView() {
        return RAW_VIEW;
    }

    @Override
    public boolean rawView() {
        return rawView;
    }

    @Override
    public String toString() {
        return "FormOptions[rawValues=" + rawValues + ", rawView=" + rawView + "]";
    }
}
