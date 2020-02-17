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
package io.micronaut.inject.property

import io.micronaut.context.ApplicationContext

class PropertyAnnotationSpec extends Specification {
    void "test inject properties"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'my.string':'foo',
                'my.int':10,
                'my.map.one':'one',
                'my.map.one.two':'two'
        )

        ConstructorPropertyInject constructorInjectedBean = ctx.getBean(ConstructorPropertyInject)
        MethodPropertyInject methodInjectedBean = ctx.getBean(MethodPropertyInject)
        FieldPropertyInject fieldInjectedBean = ctx.getBean(FieldPropertyInject)

        expect:
        constructorInjectedBean.nullable == null
        constructorInjectedBean.integer == 10
        constructorInjectedBean.str == 'foo'
        constructorInjectedBean.values == ['one':'one', 'one.two':'two']
        methodInjectedBean.nullable == null
        methodInjectedBean.integer == 10
        methodInjectedBean.str == 'foo'
        methodInjectedBean.values == ['one':'one', 'one.two':'two']
        fieldInjectedBean.nullable == null
        fieldInjectedBean.integer == 10
        fieldInjectedBean.str == 'foo'
        fieldInjectedBean.values == ['one':'one', 'one.two':'two']
        fieldInjectedBean.defaultInject == ['one':'one']
    }

    void "test a class with only a property annotation is a bean and injected"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'my.int':10,
        )

        expect:
        ctx.getBean(PropertyOnly).integer == 10
    }
}

import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.MapFormat
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Map

@Singleton
class ConstructorPropertyInject {

    private final Map<String, String> values
    private String str
    private int integer
    private String nullable

    ConstructorPropertyInject(
            @Property(name = "my.map")
            @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
                    Map<String, String> values,
            @Property(name = "my.string")
                    String str,
            @Property(name = "my.int")
                    int integer,
            @Property(name = "my.nullable")
            @Nullable
                    String nullable) {
        this.values = values
        this.str = str
        this.integer = integer
        this.nullable = nullable
    }

    Map<String, String> getValues() {
        return values
    }

    String getStr() {
        return str
    }

    int getInteger() {
        return integer
    }

    String getNullable() {
        return nullable
    }
}

@Singleton
class FieldPropertyInject {


    @Property(name = "my.map")
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    Map<String, String> values

    @Property(name = "my.map")
    Map<String, String> defaultInject

    @Property(name = "my.string")
    String str

    @Property(name = "my.int")
    int integer

    @Property(name = "my.nullable")
    @Nullable
    String nullable

    Map<String, String> getValues() {
        return values
    }

    String getStr() {
        return str
    }

    int getInteger() {
        return integer
    }

    String getNullable() {
        return nullable
    }


}

@Singleton
class MethodPropertyInject {


    private Map<String, String> values
    private String str
    private int integer
    private String nullable

    Map<String, String> getValues() {
        return values
    }

    String getStr() {
        return str
    }

    int getInteger() {
        return integer
    }

    String getNullable() {
        return nullable
    }

    @Inject
    void setValues(@Property(name = "my.map")
                          @MapFormat(transformation = MapFormat.MapTransformation.FLAT) Map<String, String> values) {
        this.values = values
    }

    @Inject
    void setStr(@Property(name = "my.string") String str) {
        this.str = str
    }

    @Inject
    void setInteger(@Property(name = "my.int") int integer) {
        this.integer = integer
    }

    @Inject
    void setNullable(@Property(name = "my.nullable")
                            @Nullable String nullable) {
        this.nullable = nullable
    }
}


class PropertyOnly {
    @Property(name = "my.int") int integer
}
