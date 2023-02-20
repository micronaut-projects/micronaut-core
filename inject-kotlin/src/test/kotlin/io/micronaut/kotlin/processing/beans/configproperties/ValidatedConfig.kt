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
package io.micronaut.kotlin.processing.beans.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import java.net.URL

@Requires(property = "spec.name", value = "ValidatedConfigurationSpec")
@ConfigurationProperties("foo.bar")
class ValidatedConfig {

    @NotNull
    var url: URL? = null

    @NotBlank
    internal var name: String? = null
}
