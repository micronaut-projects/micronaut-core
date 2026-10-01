package io.micronaut.docs.inject.aliasfor

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@interface Size {
    int min() default 0
    int max() default 100
}
