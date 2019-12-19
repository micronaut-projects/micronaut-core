package io.micronaut.ast.groovy.annotation

@interface Parent {

    Child[] child() default []
}
