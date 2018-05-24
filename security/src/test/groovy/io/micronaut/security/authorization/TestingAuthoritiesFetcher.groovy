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
package io.micronaut.security.authorization

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.providers.AuthoritiesFetcher
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import javax.inject.Singleton

@Requires(property = 'spec.name', value = 'authorization')
@Singleton
class TestingAuthoritiesFetcher implements AuthoritiesFetcher {

    @Override
    Publisher<List<String>> findAuthoritiesByUsername(String username) {
        return Flowable.just((username == "admin") ?  ["ROLE_ADMIN"] : ["foo", "bar"])
    }
}
