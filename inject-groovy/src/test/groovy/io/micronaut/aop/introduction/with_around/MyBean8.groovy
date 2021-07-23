package io.micronaut.aop.introduction.with_around

import io.micronaut.context.annotation.Executable

@Executable
@ProxyAround
class MyBean8 {
    Long id
    String name
}
