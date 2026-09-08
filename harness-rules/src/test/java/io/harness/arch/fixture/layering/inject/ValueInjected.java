package io.harness.arch.fixture.layering.inject;

import org.springframework.beans.factory.annotation.Value;

/** Violation fixture: injection through a different annotation than {@code @Autowired}. */
public class ValueInjected {

  @Value("${collaborator.name}")
  private String collaborator;

  /**
   * Read the field so it is not merely declared.
   *
   * @return the collaborator
   */
  public String collaborator() {
    return collaborator;
  }
}
