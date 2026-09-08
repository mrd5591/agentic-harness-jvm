package io.harness.arch.fixture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.harness.arch.fixture.contract.OrderResponse;
import jakarta.persistence.Entity;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/** Violating and clean classes the rule tests run against. */
public interface Fixtures {

  /** Import exactly these classes and nothing else, so each test states what it checks. */
  static JavaClasses only(Class<?>... classes) {
    return new ClassFileImporter().importClasses(classes);
  }

  /** A persistence entity. */
  @Entity
  class OrderEntity {
    private String id;

    public String getId() {
      return id;
    }
  }

  @RestController
  class CleanController {
    public OrderResponse get() {
      return new OrderResponse("ok");
    }
  }

  @RestController
  class DirectEntityController {
    public OrderEntity get() {
      return new OrderEntity();
    }
  }

  @RestController
  class WrappedEntityController {
    public Optional<OrderEntity> get() {
      return Optional.empty();
    }
  }

  @RestController
  class DeeplyWrappedEntityController {
    public Optional<List<OrderEntity>> get() {
      return Optional.empty();
    }
  }

  @RestController
  class ArrayEntityController {
    public OrderEntity[] get() {
      return new OrderEntity[0];
    }
  }

  @RestController
  class GenericArrayEntityController {
    @SuppressWarnings("unchecked")
    public List<OrderEntity>[] get() {
      return new List[0];
    }
  }

  @RestController
  class WildcardEntityController {
    public List<? extends Optional<OrderEntity>> get() {
      return List.of();
    }
  }

  @RestController
  class TypeVariableEntityController {
    public <T extends List<OrderEntity>> T get() {
      return null;
    }
  }

  @RestController
  class RecursiveBoundController {
    public <T extends Comparable<T>> T get() {
      return null;
    }
  }

  @RestController
  class GenericCleanController {
    public List<OrderResponse> get() {
      return List.of();
    }
  }

  /** The plain MVC stereotype, which the rules must treat exactly like {@code @RestController}. */
  @Controller
  class PlainController {
    public OrderEntity get() {
      return new OrderEntity();
    }

    public static class InlinePayload {}
  }

  /** A project stereotype built on {@code @RestController}: two meta-annotation hops away. */
  @RestController
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface ApiEndpoint {}

  @ApiEndpoint
  class StereotypedController {
    public OrderEntity get() {
      return new OrderEntity();
    }
  }

  @RestController
  class ControllerWithNestedType {
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

  /** The offending class is two levels down, behind a non-public holder. */
  @RestController
  class DeepController {
    static class Holder {
      public static class Inner {}
    }
  }

  /** The deep-nesting tree, since the holder is not visible outside this package. */
  static Class<?>[] deepControllerTree() {
    return new Class<?>[] {
      DeepController.class, DeepController.Holder.class, DeepController.Holder.Inner.class
    };
  }
}
