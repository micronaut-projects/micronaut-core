package io.micronaut.inject.foreach.condition;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

@EachBean(XConfig.class)
@Requires(condition = XCondition.class)
public class XClient {
}
