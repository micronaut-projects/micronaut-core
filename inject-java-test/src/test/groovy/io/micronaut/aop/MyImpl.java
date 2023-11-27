package io.micronaut.aop;

class MyImpl implements MyInterface {

    @Override
    public String process(String str) {
        return "process1";
    }

    @Override
    public String process(String str, int intParam) {
        return "process2";
    }

    @Override
    public String process2(String str, int intParam) {
        return "process2_custom";
    }

    @Override
    public String process(String str, int[] intArrayParam) {
        return "process3";
    }
}
