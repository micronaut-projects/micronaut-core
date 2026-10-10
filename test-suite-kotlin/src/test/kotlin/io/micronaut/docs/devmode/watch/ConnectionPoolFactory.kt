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
package io.micronaut.docs.devmode.watch

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.context.WatchableBeanContext
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.watch.ReloadingConfigurationWatcher
// end::imports[]

@Requires(property = "spec.name", value = "BeanWatchSnippetsTest")
// tag::class[]
@Factory
class ConnectionPoolFactory {

    @EachBean(PoolConfiguration::class)
    fun connectionPool(configuration: PoolConfiguration, context: WatchableBeanContext): ConnectionPool {
        val pool = ConnectionPool(configuration)
        val prefix = "pools." + configuration.name
        context.configuration(prefix).watchReloading { change -> // <1>
            when {
                change.touches("$prefix.url") ->
                    ReloadingConfigurationWatcher.Outcome.RECREATE // <2>
                change.touchesAny("$prefix.username", "$prefix.password") -> {
                    pool.setCredentials(configuration.username, configuration.password)
                    pool.softEvictConnections()
                    ReloadingConfigurationWatcher.Outcome.APPLIED // <3>
                }
                else -> ReloadingConfigurationWatcher.Outcome.IGNORED // <4>
            }
        }
        return pool
    }
}
// end::class[]
