package io.micronaut.docs.ioc.introspection

import io.micronaut.core.annotation.Introspected

import javax.persistence.Transient

@Introspected
class ObjectWithJakartaTransient {

    @Transient
    private String tmp;

    String getTmp() {
        return tmp
    }

    void setTmp(String tmp) {
        this.tmp = tmp
    }
}
