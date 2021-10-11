package io.micronaut.aop.introduction.with_around;

@ProxyIntroductionAndAroundAndIntrospectedAndExecutable
public class MyBean9 {

    private String[][] multidim;
    private int[][] primitiveMultidim;

    public MyBean9() {
    }

    public String[][] getMultidim() {
        return multidim;
    }

    public void setMultidim(String[][] multidim) {
        this.multidim = multidim;
    }

    public int[][] getPrimitiveMultidim() {
        return primitiveMultidim;
    }

    public void setPrimitiveMultidim(int[][] primitiveMultidim) {
        this.primitiveMultidim = primitiveMultidim;
    }
}
