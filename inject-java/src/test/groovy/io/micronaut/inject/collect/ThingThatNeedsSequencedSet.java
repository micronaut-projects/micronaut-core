package io.micronaut.inject.collect;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.SequencedSet;

@Singleton
public class ThingThatNeedsSequencedSet {
    private final SequencedSet<MyNamedBean> beans;

    @Inject
    public ThingThatNeedsSequencedSet(SequencedSet<MyNamedBean> beans) {
        this.beans = beans;
    }

    public SequencedSet<MyNamedBean> getBeans() {
        return beans;
    }

    public List<String> getBeanNames() {
        List<String> beanNames = new ArrayList<>();
        for (MyNamedBean bean : beans) {
            beanNames.add(bean.name());
        }
        return beanNames;
    }
}
