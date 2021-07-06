package io.micronaut.aop.introduction.with_around

import io.micronaut.context.annotation.Executable

@Executable
@ProxyIntroduction
class MyBean7 {

    Long id
    String name

}
