package org.particleframework.context

import spock.lang.Specification

/**
 * Created by graemerocher on 13/05/2017.
 */
class DefaultComponentResolutionContextSpec extends Specification {


    void "test path toString()"() {
        given:
        ComponentResolutionContext resolutionContext = new DefaultComponentResolutionContext(Mock(Context), Foo,[bar:Bar])
        resolutionContext.path.pushFieldResolve(Bar, "baz")
        resolutionContext.path.pushMethodArgumentResolve(Baz, "setStuff", ['stuff':Stuff])

        expect:
        resolutionContext.path.toString() == 'new Foo(Bar bar) --> Bar.baz --> Baz.setStuff(Stuff stuff)'
    }
}

class Foo {
    Foo(Bar bar) {

    }
}

class Bar {
    Baz baz
}

class Baz {
    void setStuff(Stuff stuff) {}
}

class Stuff {}