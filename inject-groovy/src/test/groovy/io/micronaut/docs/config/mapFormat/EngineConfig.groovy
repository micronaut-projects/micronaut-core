/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.config.mapFormat

import io.micronaut.context.annotation.ConfigurationProperties
import javax.validation.constraints.Min

// tag::imports[]
import io.micronaut.core.convert.format.MapFormat
// end::imports[]

/**
 * @author Zachary Klein
 * @since 1.0
 */
// tag::class[]
@ConfigurationProperties('my.engine')
class EngineConfig {

    @Min(1L)
    int cylinders

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT) //<1>
    Map<Integer, String> sensors

}
// end::class[]
