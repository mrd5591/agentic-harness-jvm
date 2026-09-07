package io.harness.arch.fixture;

import jakarta.persistence.Entity;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately-bad and deliberately-good classes that the rule tests run against.
 *
 * <p>A rule with no fixture is a rule nobody has proven fires. Every rule in this repo has both a
 * violating fixture and a clean one, so the test suite proves the gate catches the thing it claims
 * to catch rather than merely passing on a codebase that never violates it.
 */
public final class Fixtures {

  private Fixtures() {}

  /** A persistence entity. Anything that leaks this type over the wire is a violation. */
  @Entity
  public static class OrderEntity {
    private String id;

    public String getId() {
      return id;
    }
  }

  /** The projection an endpoint is supposed to return instead. */
  public record OrderResponse(String id) {}

  /** Clean: returns a record. */
  @RestController
  public static class CleanController {
    public OrderResponse get() {
      return new OrderResponse("ok");
    }
  }

  /** Violation: returns the entity directly. */
  @RestController
  public static class DirectEntityController {
    public OrderEntity get() {
      return new OrderEntity();
    }
  }

  /** Violation: returns the entity one generic level down. */
  @RestController
  public static class WrappedEntityController {
    public Optional<OrderEntity> get() {
      return Optional.empty();
    }
  }

  /** Violation: returns the entity two generic levels down. */
  @RestController
  public static class DeeplyWrappedEntityController {
    public Optional<List<OrderEntity>> get() {
      return Optional.empty();
    }
  }

  /**
   * Clean, but generic: the type walk must descend into the arguments, find nothing, and come back
   * empty. Without this case the recursion's exhausted-loop path is never executed, and a rule that
   * false-positives on every {@code List<Record>} endpoint would ship looking fully covered.
   */
  @RestController
  public static class GenericCleanController {
    public List<OrderResponse> get() {
      return List.of();
    }
  }

  /** Violation: a public nested class inside a controller. */
  @RestController
  public static class ControllerWithNestedType {
    public String get() {
      return "ok";
    }

    /** The shadowing type. */
    public static class InlinePayload {
      private String value;

      public String getValue() {
        return value;
      }
    }
  }
}
