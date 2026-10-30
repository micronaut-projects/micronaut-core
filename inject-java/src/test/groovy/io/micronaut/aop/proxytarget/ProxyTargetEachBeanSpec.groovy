package io.micronaut.aop.proxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptedProxy
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Unroll

/**
 * The proxy of an {@code @EachBean} and its target share the type and the qualifier. A lookup by them
 * must find the proxy whether the target is created before it or, by a lazy proxy, after it, and the
 * lookup of the target must not find the proxy.
 */
class ProxyTargetEachBeanSpec extends AbstractTypeElementSpec {

    @Unroll
    void 'test a proxy of an each bean proceeds to the target of its qualifier #description'() {
        given:
        def context = buildContext('proxyeachbean.Channel', """
package proxyeachbean;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around(proxyTarget = true, lazy = $lazy)
@Retention(RUNTIME)
@interface Labelled {
}

@Singleton
@InterceptorBean(Labelled.class)
class LabelInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "[x] " + context.proceed();
    }
}

@EachProperty($eachProperty)
class ChannelConfiguration {
    final String name;
    String sender;

    ChannelConfiguration(@Parameter String name) {
        this.name = name;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}

@EachBean(ChannelConfiguration.class)
@Labelled
class Channel {
    private final ChannelConfiguration configuration;

    Channel(ChannelConfiguration configuration) {
        this.configuration = configuration;
    }

    public String send(String message) {
        return message + " from " + configuration.sender;
    }
}
""", false, [
            "labelled-channels.sms.sender"  : "Fred",
            "labelled-channels.email.sender": "Bob"
        ])
        def channelClass = context.classLoader.loadClass('proxyeachbean.Channel')

        when:
        def sms = context.getBean(channelClass, Qualifiers.byName("sms"))
        def email = context.getBean(channelClass, Qualifiers.byName("email"))

        then:
        sms instanceof InterceptedProxy
        sms.send("Hello") == "[x] Hello from Fred"
        email.send("Hello") == "[x] Hello from Bob"

        and: 'the created targets do not replace the proxies'
        context.getBean(channelClass, Qualifiers.byName("sms")).is(sms)
        context.getBean(channelClass, Qualifiers.byName("email")).is(email)
        sms.send("Hi") == "[x] Hi from Fred"

        and: 'each proxy target is the bean of its own qualifier'
        !(context.getProxyTargetBean(channelClass, Qualifiers.byName("sms")) instanceof InterceptedProxy)
        context.getProxyTargetBean(channelClass, Qualifiers.byName("sms")).is(sms.interceptedTarget())

        cleanup:
        context?.close()

        where:
        description                      | lazy  | eachProperty
        'without a primary'              | false | '"labelled-channels"'
        'with a primary'                 | false | 'value = "labelled-channels", primary = "sms"'
        'lazily, without a primary'      | true  | '"labelled-channels"'
        'lazily, with a primary'         | true  | 'value = "labelled-channels", primary = "sms"'
    }
}
