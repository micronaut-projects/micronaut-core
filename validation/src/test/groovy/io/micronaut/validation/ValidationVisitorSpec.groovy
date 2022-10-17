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
package io.micronaut.validation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class ValidationVisitorSpec extends AbstractTypeElementSpec {

    void "test requires validation beans"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.FooService','''\
package test;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Singleton
class FooService {

    public Pojo bar(@Nonnull @NotNull @Valid Pojo pojo) {
        return foo(pojo);
    }

    @NonNull
    private Pojo foo(@NonNull Pojo pojo) {
        return pojo;
    }

}

@Valid
class Pojo {
    @NotBlank
    private final String name;

    public Pojo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

''')
        expect:
        definition != null
        definition.findPossibleMethods("bar").findFirst().get().hasAnnotation(Validated)
    }

    void "test fails when @Valid is defined for the parameter on a private method"() {
        when:
        buildBeanDefinition('test.FooService','''\
package test;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Singleton
class FooService {

    public Pojo bar(@Nonnull @NotNull @Valid Pojo pojo) {
        return foo(pojo);
    }

    @NonNull
    private Pojo foo(@NonNull @Valid Pojo pojo) {
        return pojo;
    }

}

@Valid
class Pojo {
    @NotBlank
    private final String name;

    public Pojo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

''')
        then:
        Throwable t = thrown()
        t.message.contains 'Method annotated for validation but is declared private'
    }

    void "test fails when @Valid is defined on a private method"() {
        when:
        buildBeanDefinition('test.FooService','''\
package test;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Singleton
class FooService {

    public Pojo bar(@Nonnull @NotNull @Valid Pojo pojo) {
        return foo(pojo);
    }

    @Valid
    @NonNull
    private Pojo foo(@NonNull Pojo pojo) {
        return pojo;
    }

}

@Valid
class Pojo {
    @NotBlank
    private final String name;

    public Pojo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

''')
        then:
        Throwable t = thrown()
        t.message.contains 'Method annotated for validation but is declared private'
    }

}

