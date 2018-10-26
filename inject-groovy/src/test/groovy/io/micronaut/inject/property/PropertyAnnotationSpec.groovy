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
        fieldInjectedBean.defaultInject == ['one':'one', 'one.two':'two']
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
