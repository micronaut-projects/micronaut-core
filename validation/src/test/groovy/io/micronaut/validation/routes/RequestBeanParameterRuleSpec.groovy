package io.micronaut.validation.routes

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class RequestBeanParameterRuleSpec extends AbstractTypeElementSpec {

    void "test RequestBean compiles with primary constructor"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/abc")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @Nullable
        @QueryValue
        private final String abc;
        
        public Bean(String abc) {
            this.abc = abc;
        }
        
        public String getAbc() { return abc; }
        
    }
    
}

""")
        then:
            noExceptionThrown()
    }

    void "test RequestBean compiles with @Creator constructor"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/abc")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @Nullable
        @QueryValue
        private final String abc;
        
        @Nullable
        @QueryValue
        private final String def;
        
        public Bean(String abc) {
            this.abc = abc;
            this.def = null;
        }
        
        @Creator    
        public Bean(String abc, String def) {
            this.abc = abc;
            this.def = def;
        }
        
        public String getAbc() { return abc; }
        
        public String getDef() { return def; }
        
    }
    
}

""")
        then:
            noExceptionThrown()
    }

    void "test RequestBean compiles with @Creator method"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/abc")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @Nullable
        @QueryValue
        private String abc;
        
        @Creator
        public static Bean of(String abc) {
            Bean bean = new Bean();
            bean.abc = abc;
            return bean;
        }
        
        public String getAbc() { return abc; }
        
    }
    
}

""")
        then:
            noExceptionThrown()
    }

    void "test RequestBean compiles with setter"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/abc")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    private static class Bean {
        
        @Nullable
        @QueryValue
        private String abc;
        
        public String getAbc() { return abc; }
        
        public void setAbc(String abc) { this.abc = abc; }
        
    }
    
}

""")
        then:
            noExceptionThrown()
    }

    void "test RequestBean fails when read only property not settable"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/abc")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    public static class Bean {
        
        @Nullable
        @QueryValue
        private String abc;
        
        public String getAbc() { return abc; }
        
    }
    
}

""")
        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("Bindable property [abc] for type [test.Foo\$Bean] is Read only and cannot be set during initialization.")
    }

    void "test RequestBean fails when property not settable when primary constructor is present"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/abc")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    public static class Bean {
        
        @Nullable
        @QueryValue
        private String abc;
        
        @Nullable
        @QueryValue
        private String def;
        
        public Bean(String def) {
            this.def = def;
        }
        
        public String getAbc() { return abc; }
        
        public String getDef() { return def; }
        
    }
    
}

""")
        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("Primary Constructor or @Creator Method for Bindable property [abc] for type [test.Foo\$Bean] found, but there is no constructor/method parameter with name equal to [abc].")
    }

    void "test RequestBean fails when constructor parameter has Bindable annotation"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/abc")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    public static class Bean {
    
        @Nullable
        @QueryValue
        private String abc;
        
        @Creator
        public Bean(@Nullable @QueryValue String abc) {
            this.abc = abc;
        }
        
        public String getAbc() { return abc; }
                
    }
    
}

""")
        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("Parameter of Primary Constructor (or @Creator Method) [abc] for type [test.Foo\$Bean] has one of @Bindable annotations. This is not supported.")
    }

    void "test RequestBean fails when having setter but not value in constructor"() {
        when:
            buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import io.micronaut.core.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/abc")
    String abc(@RequestBean Bean bean) {
        return "";
    }
    
    @Introspected
    public static class Bean {
    
        @Nullable
        @QueryValue
        private String abc;
        
        @Nullable
        @QueryValue
        private String def;
        
        @Creator
        public Bean(String def) {
            this.def = def;
        }
        
        public String getAbc() { return abc; }
        
        public void setAbc() { this.abc = abc; }
        
        public String getDef() { return def; }
                
    }
    
}

""")
        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("Primary Constructor or @Creator Method for Bindable property [abc] for type [test.Foo\$Bean] found, but there is no constructor/method parameter with name equal to [abc].")
    }


}
