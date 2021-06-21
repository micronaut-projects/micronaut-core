package io.micronaut.aop.introduction.with_around

@ProxyIntroduction
@ProxyAround
class MyBean1 {

    private Long id
    String name

    Long getId() {
        return id
    }

    void setId(Long id) {
        this.id = id
    }
}
