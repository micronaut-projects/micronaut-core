package org.particleframework.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotates a class or method as being experimental and subject to change or removal
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Retention(RetentionPolicy.SOURCE)
public @interface Experimental {

}