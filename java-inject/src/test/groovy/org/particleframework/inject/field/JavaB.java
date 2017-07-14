package org.particleframework.inject.field;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class JavaB {
    @Inject
    private JavaA[] all;

    List<JavaA> getAll() {
        return Arrays.asList(this.all);
    }
}
