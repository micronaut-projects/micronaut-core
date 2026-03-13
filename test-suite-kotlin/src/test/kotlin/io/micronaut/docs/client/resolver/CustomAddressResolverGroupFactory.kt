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
package io.micronaut.docs.client.resolver

// tag::class[]
import io.micronaut.context.annotation.Factory
import io.netty.resolver.AddressResolverGroup
import io.netty.resolver.DefaultAddressResolverGroup
import jakarta.inject.Named
import jakarta.inject.Singleton

@Factory
class CustomAddressResolverGroupFactory {

    @Singleton
    @Named("custom") // <1>
    fun customAddressResolverGroup(): AddressResolverGroup<*> {
        return DefaultAddressResolverGroup.INSTANCE // <2>
    }
}
// end::class[]
