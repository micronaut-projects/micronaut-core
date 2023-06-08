package io.micronaut.inject.foreach.multi;

import io.micronaut.context.annotation.EachBean;

@EachBean(MyBean.class)
public class Bar implements MyService {
}
