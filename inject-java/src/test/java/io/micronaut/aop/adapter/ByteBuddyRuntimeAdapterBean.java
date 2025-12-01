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
package io.micronaut.aop.adapter;

import io.micronaut.aop.Adapter;
import io.micronaut.aop.ByteBuddyRuntimeProxy;
import io.micronaut.aop.bytebuddy.ByteBuddyStacktraceVerified;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "RuntimeProxyAdapterTest")
@Singleton
@RuntimeProxy(ByteBuddyRuntimeProxy.class)
public class ByteBuddyRuntimeAdapterBean {

    private String message;

    @ByteBuddyStacktraceVerified
    @Adapter(MyAdapter.class)
    void onMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
