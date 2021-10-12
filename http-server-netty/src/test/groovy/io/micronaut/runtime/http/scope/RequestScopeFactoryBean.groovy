package io.micronaut.runtime.http.scope

import io.micronaut.http.HttpRequest

class RequestScopeFactoryBean implements RequestAware {

    int num = 0
    boolean dead = false
    HttpRequest<?> request

    static final Set<RequestScopeFactoryBean> BEANS_CREATED = new HashSet<>()

    RequestScopeFactoryBean() {
        // don't add the proxy
        if (getClass() == RequestScopeFactoryBean) {
            BEANS_CREATED.add(this)
        }
    }

    @Override
    void setRequest(HttpRequest<?> request) {
        this.request = request
    }

    int count() {
        num++
        return num
    }

    void killMe() {
        this.dead = true
    }
}
