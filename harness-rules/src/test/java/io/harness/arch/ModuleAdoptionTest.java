package io.harness.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every service module in the reactor must actually adopt the rule packs.
 *
 * <p>README's headline property is that "a rule written once cannot be forgotten in module
 * nineteen". Within an adopting module that held, but adoption itself was a copy-paste convention
 * documented in CLAUDE.md and enforced by nothing: a new module inherited Checkstyle, SpotBugs,
 * JaCoCo and PIT, had no architecture test at all, and passed. {@code importService}'s empty-import
 * guard catches a <em>mistyped</em> base package but not an <em>absent</em> test.
 *
 * <p>This test is why the property is now true rather than merely intended. It reaches outside its
 * own module, which is unusual and deliberate: the thing being asserted is a fact about the
 * reactor, and there is nowhere inside a single module to assert it from.
 *
 * <p>The obvious alternative does not work. Declaring the {@code harness-rules} test dependency in
 * the parent's {@code <dependencies>} so every module inherits it makes {@code harness-rules}
 * inherit a dependency on itself, and Maven refuses to build: "'dependencies.dependency.
 * [io.harness:harness-rules]' for io.harness:harness-rules is referencing itself". Verified, not
 * assumed. An intermediate service-parent POM would work, but it only removes a copy-paste step -
 * it still forces nothing, which is the actual gap.
 */
class ModuleAdoptionTest {

  /** Modules exempt from needing an architecture test, with the reason. */
  private static final List<String> EXEMPT = List.of("harness-rules");

  private static final Pattern MODULE = Pattern.compile("<module>\\s*([^<\\s]+)\\s*</module>");

  @Test
  @DisplayName("every service module has an architecture test that uses both rule packs")
  void everyServiceModuleAdoptsTheRules() {
    Path root = reactorRoot();
    List<String> modules = modulesOf(root);

    assertThat(modules)
        .as("the reactor should declare modules; if this is empty the test is vacuous")
        .isNotEmpty();

    List<String> missing = new ArrayList<>();
    List<String> partial = new ArrayList<>();

    for (String module : modules) {
      if (EXEMPT.contains(module)) {
        continue;
      }
      Path testRoot = root.resolve(module).resolve("src/test/java");
      List<Path> architectureTests = architectureTestsUnder(testRoot);

      if (architectureTests.isEmpty()) {
        missing.add(module);
        continue;
      }
      boolean usesBothPacks =
          architectureTests.stream()
              .map(ModuleAdoptionTest::read)
              .anyMatch(
                  body -> body.contains("LayeringRules") && body.contains("WireContractRules"));
      if (!usesBothPacks) {
        partial.add(module);
      }
    }

    assertThat(missing)
        .as(
            "these modules have no ArchitectureTest, so they inherit the sensors but none of the "
                + "architecture rules and pass while checking nothing: %s",
            missing)
        .isEmpty();
    assertThat(partial)
        .as(
            "these modules have an ArchitectureTest that does not reference both rule packs, so "
                + "half the rules are silently not applied: %s",
            partial)
        .isEmpty();
  }

  /** Walk up from the module directory until the POM that declares the modules is found. */
  private static Path reactorRoot() {
    Path candidate = Paths.get("").toAbsolutePath();
    while (candidate != null) {
      Path pom = candidate.resolve("pom.xml");
      if (Files.isRegularFile(pom) && read(pom).contains("<modules>")) {
        return candidate;
      }
      candidate = candidate.getParent();
    }
    throw new IllegalStateException("no reactor POM found above " + Paths.get("").toAbsolutePath());
  }

  private static List<String> modulesOf(Path root) {
    Matcher matcher = MODULE.matcher(read(root.resolve("pom.xml")));
    List<String> modules = new ArrayList<>();
    while (matcher.find()) {
      modules.add(matcher.group(1));
    }
    return modules;
  }

  private static List<Path> architectureTestsUnder(Path testRoot) {
    if (!Files.isDirectory(testRoot)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(testRoot)) {
      return paths
          .filter(path -> path.getFileName().toString().equals("ArchitectureTest.java"))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("walking " + testRoot, e);
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("reading " + path, e);
    }
  }
}
