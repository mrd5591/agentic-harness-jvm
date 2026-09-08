package io.harness.arch.fixture;

import jakarta.persistence.Entity;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.RestController;

/** Violating and clean classes the rule tests run against. */
public final class Fixtures {

  private Fixtures() {}

  /** A persistence entity. */
  @Entity
  public static class OrderEntity {
    private String id;

    public String getId() {
      return id;
    }
  }

  public record OrderResponse(String id) {}

  @RestController
  public static class CleanController {
    public OrderResponse get() {
      return new OrderResponse("ok");
    }
  }

  @RestController
  public static class DirectEntityController {
    public OrderEntity get() {
      return new OrderEntity();
    }
  }

  @RestController
  public static class WrappedEntityController {
    public Optional<OrderEntity> get() {
      return Optional.empty();
    }
  }

  @RestController
  public static class DeeplyWrappedEntityController {
    public Optional<List<OrderEntity>> get() {
      return Optional.empty();
    }
  }

  @RestController
  public static class GenericCleanController {
    public List<OrderResponse> get() {
      return List.of();
    }
  }

  @RestController
  public static class ControllerWithNestedType {
    public String get() {
      return "ok";
    }

    public static class InlinePayload {
      private String value;

      public String getValue() {
        return value;
      }
    }
  }
}
