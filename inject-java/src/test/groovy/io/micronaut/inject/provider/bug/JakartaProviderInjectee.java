package io.micronaut.inject.provider.bug;

import org.jspecify.annotations.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class JakartaProviderInjectee {

  private final Provider<Injectable> first;
  private final Provider<Injectable> second;
  private final Provider<Injectable> third;

  @Inject
  public JakartaProviderInjectee(
      @First @Nullable Provider<Injectable> first,
      @Nullable @Second Provider<Injectable> second,
      @Third @Nullable Provider<Injectable> third) {
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

  private String getValue(Provider<Injectable> injectable) {
    if (injectable == null) {
      return null;
    }
    return String.valueOf(injectable.get().getInstance());
  }
}
