/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.jackson

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper

class JacksonClassloaderSetupSpec extends AbstractTypeElementSpec {

    void "test Jackson classloader"() {
        given:
            def context = buildContext('test.Animal',"""
package test;

import com.fasterxml.jackson.annotation.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, defaultImpl = Dog.class)
interface Animal {
    String getName();
}

class Dog implements Animal {
    private String name;
    private double barkVolume;

    @Override
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setBarkVolume(double barkVolume) {
        this.barkVolume = barkVolume;
    }
    public double getBarkVolume() {
        return barkVolume;
    }
}

class Cat implements Animal {
    private String name;
    private int lives;
    private boolean likesCream;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getLives() {
        return lives;
    }
    public void setLives(int lives) {
        this.lives = lives;
    }
    public void setLikesCream(boolean likesCream) {
        this.likesCream = likesCream;
    }
    public boolean isLikesCream() {
        return likesCream;
    }
}
""", true)

        when:
            JsonMapper jsonMapper = context.getBean(JsonMapper)

            def dogBean = jsonMapper.readValue('{"@c":".Dog","name":"Fred","barkVolume":1.1}', argumentOf(context, 'test.Animal'))
            def catBean = jsonMapper.readValue('{"@c":".Cat","name":"Joe","lives":9,"likesCream":true}', argumentOf(context, 'test.Animal'))

        then:
            dogBean.name == "Fred"
            dogBean.barkVolume == 1.1d
            catBean.name == "Joe"
            catBean.likesCream
            catBean.lives == 9

        cleanup:
            context.close()
    }

    Argument<Object> argumentOf(ApplicationContext context, String name) {
        return Argument.of(context.classLoader.loadClass(name))
    }
}
