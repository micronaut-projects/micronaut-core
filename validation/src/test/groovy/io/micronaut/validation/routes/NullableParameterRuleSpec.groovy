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

class NullableParameterRuleSpec extends AbstractTypeElementSpec {

    void "test nullable parameter"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import edu.umd.cs.findbugs.annotations.Nullable;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(@Nullable String abc) {
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
import java.util.Optional;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(Optional<String> abc) {
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

    @Get("{/abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(@PathVariable(defaultValue = "x") String abc) {
        return "";
    }
}

""")

        then:
            noExceptionThrown()
    }

    void "test query optional parameter"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{?abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")
    }

    void "test ampersand optional parameter"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{&abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")
    }

    void "test required argument doesn't fail compilation"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import edu.umd.cs.findbugs.annotations.Nullable;

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
    }

    void "test nullable with multiple uris"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import edu.umd.cs.findbugs.annotations.Nullable;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{?abc}"})
    String abc(@Nullable String abc) {
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
import edu.umd.cs.findbugs.annotations.Nullable;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{abc}", "/{?def}"})
    String abc(String abc, @Nullable String def) {
        return "";
    }
}

""")

        then: "abc is optional because /{?def} may be matched and it does not have {abc}"
            def ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{abc}", "/{?abc}"})
    String abc(String abc) {
        return "";
    }
}

""")
        then: "abc is optional because it is optional in at least one template"
            ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{abc}", "/{abc}"})
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
import edu.umd.cs.findbugs.annotations.Nullable;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{?abc}", "/{?abc}"})
    String abc(@Nullable String abc) {
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

    @Get(uris = {"/{?abc}", "/{abc}"})
    String abc(String abc) {
        return "";
    }
}

""")
        then: "abc is optional because it is optional in at least one template"
            ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import edu.umd.cs.findbugs.annotations.Nullable;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{abc}", "/{def}"})
    String abc(String abc, String def) {
        return "";
    }
}

""")
        then:
            ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")
    }

    void "test nullable parameter with RequestBean"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @Nullable
        @PathVariable
        private String abc;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }
        
    }
    
}

""")
        then:
            noExceptionThrown()

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;
import java.util.Optional;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @PathVariable
        private Optional<String> abc;
        
        public Optional<String> getAbc() { return abc; }
        public void setAbc(Optional<String> abc) { this.abc = abc; }

    }
    
}

""")

        then:
            noExceptionThrown()

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @PathVariable
        private String abc;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }
    }
    
}

""")
        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @PathVariable(defaultValue = "x")
        private String abc;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }
        
    }
    
}

""")

        then:
            noExceptionThrown()

    }

    void "test query optional parameter with RequestBean"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import java.util.Optional;

@Controller("/foo")
class Foo {

    @Get("/{?abc}")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @QueryValue
        private Optional<String> abc;
        
        public Optional<String> getAbc() { return abc; }
        public void setAbc(Optional<String> abc) { this.abc = abc; }
    }
    
}

""")

        then:
            noExceptionThrown()

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{?abc}")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @QueryValue
        private String abc;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }
        
    }
    
}

""")

        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")
    }

    void "test ampersand optional RequestBean parameter"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{&abc}")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @PathVariable
        private String abc;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }

        
    }
    
}

""")

        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")
    }

    void "test nullable with multiple uris with RequestBean"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{abc}", "/{?def}"})
    String abc(@RequestBean Bean bean) {
        return "";
    }
   
    @Introspected
    private static class Bean {
        
        @PathVariable
        private String abc;
        
        @Nullable
        @PathVariable
        private String def;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }
        
        public String getDef() { return def; }
        public void setDef(String def) { this.def = def; }
        
    }
    
}

""")

        then: "abc is optional because /{?def} may be matched and it does not have {abc}"
        def ex = thrown(RuntimeException)
        ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{?abc}", "/{abc}"})
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @PathVariable
        private String abc;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }
        
    }
    
}

""")
        then: "abc is optional because it is optional in at least one template"
            ex = thrown(RuntimeException)
            ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{?abc}", "/{?abc}"})
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @Nullable
        @PathVariable
        private String abc;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }
        
    }
    
}

""")
        then:
        noExceptionThrown()

        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;

@Controller("/foo")
class Foo {

    @Get(uris = {"/{abc}", "/{abc}"})
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @PathVariable
        private String abc;
        
        public String getAbc() { return abc; }
        public void setAbc(String abc) { this.abc = abc; }
        
    }
    
}

""")
        then:
            noExceptionThrown()


    }
}
