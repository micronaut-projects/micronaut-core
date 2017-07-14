package org.particleframework.inject.method;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class JavaB {
    private List<JavaA> all;

    @Inject
    void setJavaA(JavaA[] a) {
        this.all = Arrays.asList(a);
    }

    List<JavaA> getAll() {
        return this.all;
    }
}