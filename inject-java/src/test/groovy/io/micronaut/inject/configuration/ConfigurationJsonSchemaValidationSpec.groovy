package io.micronaut.inject.configuration

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import org.intellij.lang.annotations.Language
import groovy.json.JsonSlurper

class ConfigurationJsonSchemaValidationSpec extends AbstractTypeElementSpec {

    @Override
    protected JavaParser newJavaParser() {
        new JavaParser() {}
    }

    private static String readSchemaFile(AbstractTypeElementSpec self, String fqcn, @Language("java") String cls) {
        String path = "META-INF/micronaut-configuration-schemas/${fqcn}.json"
        return self.buildAndReadResourceAsString(path, cls)
    }

    private static Map parseJson(String json) { new JsonSlurper().parseText(json) as Map }

    void "string constraints: NotBlank, Size, Pattern, Email"() {
        when:
        String schema = readSchemaFile(this, 'test.Props', '''
package test;
import io.micronaut.context.annotation.*;
import jakarta.validation.constraints.*;

@ConfigurationProperties("props")
class Props {
  @NotBlank
  @Size(min=2, max=5)
  @Pattern(regexp = "[a-z]+")
  @Email
  private String s;

  public String getS() { return s; }
  public void setS(String v) { this.s = v; }
}
''')
        then:
        def m = parseJson(schema)
        def props = m.get("properties")
        Map s = props['s']
        assert m.required.contains('s')
        s.minLength == 2
        s.maxLength == 5
        s.pattern == '[a-z]+'
        s.format == 'email'
    }

    void "number constraints: Min/Max, DecimalMin/DecimalMax exclusive, Positive, NegativeOrZero"() {
        when:
        String schema = readSchemaFile(this, 'test.NumProps', '''
package test;
import io.micronaut.context.annotation.*;
import jakarta.validation.constraints.*;

@ConfigurationProperties("num")
class NumProps {
  @Min(10)
  @Max(100)
  private int a;

  @DecimalMin(value="1.5", inclusive=false)
  @DecimalMax(value="9.5", inclusive=false)
  private double b;

  @Positive
  private long c;

  @NegativeOrZero
  private long d;

  public int getA(){return a;} public void setA(int v){this.a=v;}
  public double getB(){return b;} public void setB(double v){this.b=v;}
  public long getC(){return c;} public void setC(long v){this.c=v;}
  public long getD(){return d;} public void setD(long v){this.d=v;}
}
''')
        then:
        def m = parseJson(schema)
        Map props = m.get("properties")
        Map a = props['a']
        Map b = props['b']
        Map c = props['c']
        Map d = props['d']
        a.minimum == 10
        a.maximum == 100
        b.exclusiveMinimum == 1.5
        b.exclusiveMaximum == 9.5
        c.exclusiveMinimum == 0
        d.maximum == 0
    }

    void "collection constraints: NotEmpty and Size on List and Map"() {
        when:
        String schema = readSchemaFile(this, 'test.CollProps', '''
package test;
import io.micronaut.context.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;

@ConfigurationProperties("coll")
class CollProps {
  @NotEmpty
  @Size(min=2, max=4)
  private java.util.List<String> l;

  @NotEmpty
  @Size(min=1, max=3)
  private java.util.Map<String,String> m;

  public java.util.List<String> getL(){return l;} public void setL(java.util.List<String> v){this.l=v;}
  public java.util.Map<String,String> getM(){return m;} public void setM(java.util.Map<String,String> v){this.m=v;}
}
''')
        then:
        def m = parseJson(schema)
        Map props = m.get("properties")
        Map l = props['l']
        Map pmap = props['m']
        l.minItems == 2
        l.maxItems == 4
        pmap.minProperties == 1
        pmap.maxProperties == 3
    }

    void "required with NotNull and EachProperty entry constraints"() {
        when:
        String schema = readSchemaFile(this, 'test.Each', '''
package test;
import io.micronaut.context.annotation.*;
import jakarta.validation.constraints.*;

@EachProperty("things")
class Each {
  @NotNull
  private String name;

  public String getName(){return name;} public void setName(String v){this.name=v;}
}
''')
        then:
        def m = parseJson(schema)
        m.type == 'object' || m.type == 'array'
        def entry = m.'$defs'.Entry
        assert entry.required != null && entry.required.contains('name')
        (entry.get("properties")['name']).type == 'string'
    }

    void "assertTrue/assertFalse map to const"() {
        when:
        String schema = readSchemaFile(this, 'test.BoolProps', '''
package test;
import io.micronaut.context.annotation.*;
import jakarta.validation.constraints.*;

@ConfigurationProperties("bools")
class BoolProps {
  @AssertTrue
  private boolean on;
  @AssertFalse
  private boolean off;
  public boolean isOn(){return on;} public void setOn(boolean v){this.on=v;}
  public boolean isOff(){return off;} public void setOff(boolean v){this.off=v;}
}
''')
        then:
        def m = parseJson(schema)
        Map props = m.get("properties")
        props.on.const == true
        props.off.const == false
    }

    void "NotNull implies required for configuration properties"() {
        when:
        String schema = readSchemaFile(this, 'test.RequiredProps', '''
package test;
import io.micronaut.context.annotation.*;
import jakarta.validation.constraints.*;

@ConfigurationProperties("required")
class RequiredProps {
  @NotNull
  private String must;
  public String getMust(){return must;} public void setMust(String v){this.must=v;}
}
''')
        then:
        def m = parseJson(schema)
        m.required.contains('must')
    }

    void "Digits maps to conservative pattern"() {
        when:
        String schema = readSchemaFile(this, 'test.DigitsProps', '''
package test;
import io.micronaut.context.annotation.*;
import jakarta.validation.constraints.*;

@ConfigurationProperties("digits")
class DigitsProps {
  @Digits(integer=3, fraction=2)
  private java.math.BigDecimal amount;
  public java.math.BigDecimal getAmount(){return amount;} public void setAmount(java.math.BigDecimal v){this.amount=v;}
}
''')
        then:
        def m = parseJson(schema)
        Map props = m.get("properties")
        String pat = props.amount.pattern
        assert pat != null
        assert pat.contains("\\d")
    }

    void "EachProperty entry NotEmpty list implies minItems"() {
        when:
        String schema = readSchemaFile(this, 'test.EachList', '''
package test;
import io.micronaut.context.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;

@EachProperty("groups")
class EachList {
  @NotEmpty
  private java.util.List<String> names;
  public java.util.List<String> getNames(){return names;} public void setNames(java.util.List<String> v){this.names=v;}
}
''')
        then:
        def m = parseJson(schema)
        def entry = m.'$defs'.Entry
        (entry.get("properties").names).minItems == 1
    }

    void "Nullable properties are not required"() {
        when:
        String schema = readSchemaFile(this, 'test.NullableProps', '''
package test;
import io.micronaut.context.annotation.*;
import org.jspecify.annotations.Nullable;

@ConfigurationProperties("nullable")
class NullableProps {
  @Nullable
  private String maybe;
  public String getMaybe(){return maybe;} public void setMaybe(String v){this.maybe=v;}
}
''')
        then:
        def m = parseJson(schema)
        assert m.required == null || !m.required.contains('maybe')
    }
}
