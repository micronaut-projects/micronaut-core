package io.micronaut.inject.provider.bug;

public class Injectable {
  private final int instance;

  public Injectable(int instance) {
    this.instance = instance;
  }

  public int getInstance() {
    return instance;
  }
}
