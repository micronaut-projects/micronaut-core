package io.micronaut.aop.adapter.priv1;

import io.micronaut.aop.adapter.priv1.test.TestAnnotation;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "InterceptorPrivate1Spec")
@TestAnnotation
@Bean
public class MyObject {
	public String testMe() {
		return "bean";
	}
}
