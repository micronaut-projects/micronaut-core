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
package org.particleframework.aop.infra

import org.particleframework.aop.Around
import org.particleframework.aop.Interceptor
import org.particleframework.aop.InvocationContext
import org.particleframework.aop.annotation.Trace
import org.particleframework.context.annotation.Type

import javax.inject.Singleton
import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.RetentionPolicy.RUNTIME

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
class Foo {

    Bar bar

    Foo(Bar bar) {
        this.bar = bar
    }

    @Mutating
    String blah(String name) {
        "Name is $name"
    }

    @Singleton
    static class ArgMutating implements Interceptor {

        @Override
        Object intercept(InvocationContext context) {
            context.getParameters().get("name").setValue("changed")
            return context.proceed()
        }
    }
}

@Around
@Type(Foo.ArgMutating)
@Documented
@Retention(RUNTIME)
@Target([ElementType.METHOD])
@interface Mutating {
}

