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
package io.micronaut.http.problem;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

@Introspected
public class InvalidParam {

    @NonNull
    private String name;

    @Nullable
    private String reason;

    public InvalidParam(@NonNull String name) {
        this.name = name;
    }

    public InvalidParam(@NonNull String name, @Nullable String reason) {
        this.name = name;
        this.reason = reason;
    }

    /**
     *
     * @return Parameter name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     *
     * @param name Parameter name
     */
    public void setName(@NonNull String name) {
        this.name = name;
    }

    /**
     *
     * @return Reason why the parameter is invalid
     */
    @Nullable
    public String getReason() {
        return reason;
    }

    /**
     *
     * @param reason Reason why the parameter is invalid
     */
    public void setReason(@Nullable String reason) {
        this.reason = reason;
    }
}
