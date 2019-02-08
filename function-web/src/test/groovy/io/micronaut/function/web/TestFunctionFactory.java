/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.function.web;

import io.micronaut.context.annotation.Factory;
import io.micronaut.function.FunctionBean;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@Factory
public class TestFunctionFactory {

    // This should work but is currently not implemented
    // the reason is because when @FunctionBean is defined on a factory
    // we do not go through and visit public Executable methods unless
    // it is an AOP proxy
    @FunctionBean("java/supplier/string")
    Supplier<String> get() {
        return () -> "myvalue";
    }

    @FunctionBean("java/supplier/xml")
    @Produces(MediaType.TEXT_XML)
    Supplier<String> getXml() {
        return () -> "<hello></hello>";
    }

    // This should work but is currently not implemented
    // the reason is because when @FunctionBean is defined on a factory
    // we do not go through and visit public Executable methods unless
    // it is an AOP proxy
    @FunctionBean("java/function/round")
    Function<Double, Long> round() {
        return (doub) -> Math.round(doub.doubleValue());
    }

    @FunctionBean("java/function/upper")
    Function<Name, Name> upper() {
        return (n) -> n.setName(n.getName().toUpperCase());
    }

    @FunctionBean("java/function/fullname")
    BiFunction<String, String, Name> fullname() {
        return (s, s2) -> {
            Name name = new Name();
            name.setName(s + " " + s2);
            return name;
        };
    }

    @FunctionBean("java/function/xml")
    @Consumes(MediaType.TEXT_XML)
    Function<String, String> roundXml() {
        return (str) -> str;
    }

    static class Name {
        private String name;

        public String getName() {
            return name;
        }

        public Name setName(String name) {
            this.name = name;
            return this;
        }
    }
}
