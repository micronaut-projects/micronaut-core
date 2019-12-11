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
package io.micronaut.validation.routes

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.util.environment.RestoreSystemProperties

class MissingParameterRuleSpec extends AbstractTypeElementSpec {

    void "test missing parameter"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
        noExceptionThrown()

        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc() {
        return "";
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("The route declares a uri variable named [abc], but no corresponding method argument is present")
    }


    void "test missing parameter with expression"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/\${version}/foo")
class Foo {

    @Get("/{abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
        noExceptionThrown()

        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc() {
        return "";
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("The route declares a uri variable named [abc], but no corresponding method argument is present")
    }

    @RestoreSystemProperties
    void "test validation can be turned off with a system property"() {
        setup:
        System.setProperty("micronaut.route.validation", "false")

        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc() {
        return "";
    }
}

""")

        then:
        noExceptionThrown()
    }

    void "test property name change with bindable"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc(@QueryValue("abc") String def) {
        return "";
    }
}

""")

        then:
        noExceptionThrown()
    }

    void "test body bean properties are added to parameters"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Post("/{abc}")
    String abc(@Body Book book) {
        return "";
    }
}

class Book {
    
    private String abc;
    
    public String getAbc() {
        return this.abc;
    }
    
    public void setAbc(String abc) {
        this.abc = abc;
    }
}

""")

        then:
        noExceptionThrown()
    }

    void "test map name to different header"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{name}")
    String abc(@Header("pet-name") String name, @QueryValue("name") String pathName) {
        return "abc";
    }
}

class Book {
    
    private String abc;
    
    public String getAbc() {
        return this.abc;
    }
    
    public void setAbc(String abc) {
        this.abc = abc;
    }
}

""")

        then:
        noExceptionThrown()
    }
}
