package io.harness.arch.fixture.layering.inject;

import org.springframework.beans.factory.annotation.Autowired;

/** Violation fixture: a field-injected collaborator. */
public class FieldInjected {

  @Autowired private String collaborator;

  /**
   * Read the field so it is not merely declared.
   *
   * @return the collaborator
   */
  public String collaborator() {
    return collaborator;
  }
}
