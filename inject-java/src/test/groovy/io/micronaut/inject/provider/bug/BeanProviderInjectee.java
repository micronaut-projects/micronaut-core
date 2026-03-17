package io.micronaut.inject.provider.bug;

import io.micronaut.context.BeanProvider;
import org.jspecify.annotations.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class BeanProviderInjectee {

  private final BeanProvider<Injectable> first;
  private final BeanProvider<Injectable> second;
  private final BeanProvider<Injectable> third;

  @Inject
  public BeanProviderInjectee(
      @First @Nullable BeanProvider<Injectable> first,
      @Nullable @Second BeanProvider<Injectable> second,
      @Third @Nullable BeanProvider<Injectable> third) {
    this.first = first;
    this.second = second;
    this.third = third;
  }

  public String showWhatIsInjected() {
    return "first "
        + getValue(first)
        + ", second "
        + getValue(second)
        + ", third "
        + getValue(third);
  }

  private String getValue(BeanProvider<Injectable> injectable) {
    if (injectable == null) {
      return null;
    }
    return String.valueOf(injectable.get().getInstance());
  }
}
