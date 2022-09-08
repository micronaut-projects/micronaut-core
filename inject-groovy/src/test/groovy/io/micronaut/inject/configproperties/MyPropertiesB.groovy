package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.*

@ConfigurationProperties("test")
class MyPropertiesAA {

    TestAA test

    @ConfigurationBuilder(factoryMethod="build", includes="foo")
    void setTest(TestAA test) {
        this.test = test;
    }

    TestAA getTest() {
        return this.test
    }
}

class TestAA {
    private String foo
    private String bar

    private TestAA() {}

    public void setFoo(String s) {
        this.foo = s;
    }
    public String getFoo() {
        return foo;
    }
    public void setBar(String s) {
        this.bar = s;
    }
    public String getBar() {
        return bar;
    }

    static TestAA build() {
        new TestAA()
    }
}
