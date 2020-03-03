/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.aop.introduction;

// tag::imports[]
import io.micronaut.aop.*;
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class StubIntroduction implements MethodInterceptor<Object,Object> { // <1>

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.getValue( // <2>
                Stub.class,
                context.getReturnType().getType()
        ).orElse(null); // <3>
    }
}
// end::class[]
