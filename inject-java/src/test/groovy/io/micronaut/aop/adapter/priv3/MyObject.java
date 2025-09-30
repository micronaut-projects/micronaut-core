package io.micronaut.aop.adapter.priv3;

import io.micronaut.aop.adapter.priv3.test1.TestAnnotation1;
import io.micronaut.aop.adapter.priv3.test2.TestAnnotation2;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "InterceptorPrivate3Spec")
@TestAnnotation1
@TestAnnotation2
@Bean
public class MyObject {
	public String testMe() {
		return "bean";
	}
}
