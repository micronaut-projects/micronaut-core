package org.particleframework.tck

import junit.framework.Test
import org.particleframework.context.Context
import org.particleframework.context.DefaultContext

/**
 * Created by graemerocher on 12/05/2017.
 */
class ConvertibleSuite {

    public static Test suite() {
        Context context = new DefaultContext()
        context.start()
        Tck.testsFor(context.getBean(Car), false, true)
    }
}

