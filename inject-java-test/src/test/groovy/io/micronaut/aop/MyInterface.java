package io.micronaut.aop;

interface MyInterface {

    String process(String str);

    String process(String str, int intParam);

    String process(String str, int intArrayParam[]);
}
