/*
 * Copyright 2017 original authors
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
package org.particleframework.docs.config.builder

// tag::imports[]
import org.particleframework.context.annotation.Bean
import org.particleframework.context.annotation.Factory

import javax.inject.Singleton
// end::imports[]

/**
 * @author James Kleeh
 * @since 1.0
 */
// tag::class[]
@Factory
class EngineFactory {

    @Bean
    @Singleton
    EngineImpl buildEngine(EngineConfig engineConfig) {
        engineConfig.builder.build(engineConfig.crankShaft)
    }
}
// end::class[]