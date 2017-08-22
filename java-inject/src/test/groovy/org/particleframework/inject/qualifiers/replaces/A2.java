package org.particleframework.inject.qualifiers.replaces;

import org.particleframework.context.annotation.Replaces;
import org.particleframework.inject.qualifiers.primary.A;

import javax.inject.Singleton;

@Replaces(A1.class)
@Singleton
public class A2 implements A {}
