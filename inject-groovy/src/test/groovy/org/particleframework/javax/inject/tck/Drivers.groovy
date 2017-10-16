package org.particleframework.javax.inject.tck

import javax.inject.Qualifier
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME) @Qualifier
@interface Drivers {
}

