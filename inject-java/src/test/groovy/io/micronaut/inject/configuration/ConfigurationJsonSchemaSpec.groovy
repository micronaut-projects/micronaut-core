package io.micronaut.inject.configuration

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import org.intellij.lang.annotations.Language
import groovy.json.JsonSlurper

class ConfigurationJsonSchemaSpec extends AbstractTypeElementSpec {

    @Override
    protected JavaParser newJavaParser() {
        new JavaParser() {}
    }

    private static Map readSchema(String fqcn, String json) {
        new JsonSlurper().parseText(json) as Map
    }

    private static String readSchemaFile(AbstractTypeElementSpec self, String fqcn, @Language("java") String cls) {
        String path = "META-INF/micronaut-configuration-schemas/${fqcn}.json"
        return self.buildAndReadResourceAsString(path, cls)
    }

    void "test simple configuration properties schema"() {
        when:
        String schema = readSchemaFile(this, 'test.MyProps', '''
package test;

import io.micronaut.context.annotation.*;

/** My props */
@ConfigurationProperties("foo.bar")
class MyProps {
    private String host;
    private int port;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
''')

        then:
        def m = readSchema('test.MyProps', schema)
        m.'$schema' == 'https://json-schema.org/draft/2020-12/schema'
        m.'$id' == 'urn:micronaut:config:test.MyProps'
        m.title == 'test.MyProps'
        m.'x-micronaut'.prefix == 'foo.bar'
        m.type == 'object'
        Map props = (Map) m.get('properties')
        ((Map) props.get('host')).get('type') == 'string'
        ((Map) props.get('port')).get('type') == 'integer'
    }

    void "test each property map schema with requires"() {
        when:
        String schema = readSchemaFile(this, 'test.Ds', '''
package test;
import io.micronaut.context.annotation.*;

@EachProperty("dataSources")
class Ds {
    private java.net.URI url;
    public java.net.URI getUrl() { return url; }
    public void setUrl(java.net.URI url) { this.url = url; }
}
''')

        then:
        def m = readSchema('test.Ds', schema)
        m.'x-micronaut'.kind == 'each-property'
        m.'x-micronaut'.container == 'map'
        m.'x-micronaut'.prefix == 'data-sources'
        m.type == 'object'
        m.minProperties == 1
        m.additionalProperties.'$ref' == '#/$defs/Entry'
        m.'$defs'.Entry.type == 'object'
        Map entryProps = (Map) ((Map) m.get('$defs')).get('Entry').get('properties')
        ((Map) entryProps.get('url')).get('format') == 'uri'
    }

    void "test each property list schema with requires"() {
        when:
        String schema = readSchemaFile(this, 'test.Buckets', '''
package test;
import io.micronaut.context.annotation.*;

@EachProperty(value = "buckets", list = true)
class Buckets {
    private String region;
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
''')

        then:
        def m = readSchema('test.Buckets', schema)
        m.'x-micronaut'.kind == 'each-property'
        m.'x-micronaut'.container == 'list'
        m.type == 'array'
        m.minItems == 1
        m.items.'$ref' == '#/$defs/Entry'
        Map entryProps2 = (Map) ((Map) m.get('$defs')).get('Entry').get('properties')
        ((Map) entryProps2.get('region')).get('type') == 'string'
    }

    void "test nested configuration properties produce separate schema files"() {
        when:
        String schemaOuter = readSchemaFile(this, 'test.Outer', '''
package test;
import io.micronaut.context.annotation.*;

@ConfigurationProperties("outer")
class Outer {
  Inner inner = new Inner();
  @ConfigurationProperties("inner")
  public static class Inner {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
  }
}
''')
        String schemaInner = readSchemaFile(this, 'test.Outer$Inner', '''
package test;
import io.micronaut.context.annotation.*;

@ConfigurationProperties("outer")
class Outer {
  Inner inner = new Inner();
  @ConfigurationProperties("inner")
  public static class Inner {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
  }
}
''')
        then:
        def outer = readSchema('test.Outer', schemaOuter)
        outer.'x-micronaut'.prefix == 'outer'
        outer.type == 'object'
        def inner = readSchema('test.Outer$Inner', schemaInner)
        inner.'x-micronaut'.prefix == 'outer.inner'
        inner.type == 'object'
        Map iprops = (Map) inner.get('properties')
        ((Map) iprops.get('enabled')).get('type') == 'boolean'
    }

    void "test common java types mapping"() {
        when:
        String schema = readSchemaFile(this, 'test.Types', '''
package test;
import io.micronaut.context.annotation.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;

@ConfigurationProperties("types")
class Types {
  private String s; private int i; private long l; private double d; private boolean b;
  private URI uri; private Duration dur; private Optional<Integer> opt;
  private java.util.List<String> names; private java.util.Map<String,String> labels;
  private Color color;
  public String getS(){return s;} public void setS(String s){this.s=s;}
  public int getI(){return i;} public void setI(int v){this.i=v;}
  public long getL(){return l;} public void setL(long v){this.l=v;}
  public double getD(){return d;} public void setD(double v){this.d=v;}
  public boolean isB(){return b;} public void setB(boolean v){this.b=v;}
  public URI getUri(){return uri;} public void setUri(URI u){this.uri=u;}
  public Duration getDur(){return dur;} public void setDur(Duration u){this.dur=u;}
  public Optional<Integer> getOpt(){return opt;} public void setOpt(Optional<Integer> o){this.opt=o;}
  public java.util.List<String> getNames(){return names;} public void setNames(java.util.List<String> n){this.names=n;}
  public java.util.Map<String,String> getLabels(){return labels;} public void setLabels(java.util.Map<String,String> m){this.labels=m;}
  public Color getColor(){return color;} public void setColor(Color c){this.color=c;}
  public static enum Color { RED, GREEN, BLUE }
}
''')
        then:
        def m = readSchema('test.Types', schema)
        m.'x-micronaut'.prefix == 'types'

        def properties = m.get('properties')
        properties['s']['type'] == 'string'
        properties['i']['type'] == 'integer'
        properties['l']['type'] == 'integer'
        properties['d']['type'] == 'number'
        properties['b']['type'] == 'boolean'
        properties['uri']['format'] == 'uri'
        properties['dur']['format'] == 'duration'
        properties['names']['type'] == 'array'
        properties['names']['items']['type'] == 'string'
        properties['labels']['type'] == 'object'
        properties['labels']['additionalProperties']['type'] == 'string'
        properties['color']['type'] == 'string'
        properties['color']['enum'] == ['RED', 'GREEN', 'BLUE']
    }
}
