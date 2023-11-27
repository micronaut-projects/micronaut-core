package io.micronaut.aop;

interface MyInterface {

    String process(String str);

    // This method will not be added to the generated intercepted class for some reason.
    String process(String str, int intParam);

    // This one will
    String process2(String str, int intParam);

    // Seems like this one is clashing with #proces(String, int)
    // and will replace it in the intercepted?
    String process(String str, int intArrayParam[]);
}
