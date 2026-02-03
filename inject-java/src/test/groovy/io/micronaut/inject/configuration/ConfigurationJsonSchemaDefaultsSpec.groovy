package io.micronaut.inject.configuration

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import org.intellij.lang.annotations.Language
import groovy.json.JsonSlurper

class ConfigurationJsonSchemaDefaultsSpec extends AbstractTypeElementSpec {

    @Override
    protected JavaParser newJavaParser() {
        new JavaParser() {}
    }

    private static String readSchemaFile(AbstractTypeElementSpec self, String fqcn, @Language("java") String cls) {
        String path = "META-INF/micronaut-configuration-schemas/${fqcn}.json"
        return self.buildAndReadResourceAsString(path, cls)
    }

    private static Map parseJson(String json) { new JsonSlurper().parseText(json) as Map }

    void "test field constants as defaults (int/string/boolean)"() {
        when:
        String schema = readSchemaFile(this, 'test.Consts', '''
package test;
import io.micronaut.context.annotation.*;

@ConfigurationProperties("consts")
class Consts {
  public static final int DEFAULT_I = 42;
  public static final String DEFAULT_STRING_VALUE = "hello";
  public static final boolean DEFAULT_B = true;

  private int i = DEFAULT_I;
  private String stringValue = DEFAULT_STRING_VALUE;
  private boolean b = DEFAULT_B;

  public int getI() { return i; }
  public void setI(int v) { this.i = v; }
  public String getStringValue() { return stringValue; }
  public void setStringValue(String v) { this.stringValue = v; }
  public boolean isB() { return b; }
  public void setB(boolean v) { this.b = v; }
}
''')
        then:
        def m = parseJson(schema)
        Map props = (Map) m.get('properties')
        ((Map) props.get('i')).get('default') == 42
        ((Map) props.get('string-value')).get('default') == 'hello'
        ((Map) props.get('b')).get('default') == true
    }

    void "test @Bindable default on setter parameter is used"() {
        when:
        String schema = readSchemaFile(this, 'test.BindableSetter', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("bindable.setter")
class BindableSetter {
  @Bindable(defaultValue = "64")
  private int size;
  public int getSize() { return size; }
  public void setSize(int size) { this.size = size; }
}
''')
        then:
        def m = parseJson(schema)
        Map props = (Map) m.get('properties')
        ((Map) props.get('size')).get('default') == 64
    }

    void "test @Bindable default on property method (interface) is used"() {
        when:
        String schema = readSchemaFile(this, 'test.ItfProps', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("itf")
interface ItfProps {
  @Bindable(defaultValue = "http://default")
  java.net.URI getEndpoint();
}
''')
        then:
        def m = parseJson(schema)
        Map props = (Map) m.get('properties')
        ((Map) props.get('endpoint')).get('default') == 'http://default'
        ((Map) props.get('endpoint')).get('format') == 'uri'
    }

    void "test @Bindable default on property method record is used"() {
        when:
        String schema = readSchemaFile(this, 'test.ItfProps', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("itf")
record ItfProps(
  @Bindable(defaultValue = "http://default")
  java.net.URI endpoint) {
}
''')
        then:
        def m = parseJson(schema)
        Map props = (Map) m.get('properties')
        ((Map) props.get('endpoint')).get('default') == 'http://default'
        ((Map) props.get('endpoint')).get('format') == 'uri'
    }
}
