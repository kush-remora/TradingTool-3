# Accumulation Configuration Runtime Loading

## Current Understanding

Service startup fails while constructing `AccumulationShapeEngine` because its algorithm configuration is only searched relative to the process working directory. That works from a few repository locations but fails for IDE and packaged-JAR launches. The configuration remains an editable repository file for local calibration, while the built core artifact must also contain a reliable default.

## Implementation Outcome

1. Package the existing root configuration into the core artifact without creating a second source of truth.
2. Load an existing local configuration first, then fall back to the packaged resource.
3. Add a focused test for the packaged-resource fallback and run the core test suite.

The core Maven resource configuration now packages `config/accumulation_analysis_config.json` directly from its existing repository location, avoiding a duplicate source file. Runtime loading still prefers an editable local configuration and otherwise reads the packaged classpath resource. The focused fallback test and `mvn -pl core test` passed (11 `AccumulationShapeEngineTest` tests). A full service package attempt was blocked by unrelated untracked work in `core/src/main/kotlin/com/tradingtool/core/strategy/weeklyfloor/WeeklyFloorReboundEngine.kt`, which currently does not compile.

## Render Deployment Diagnosis (2026-07-29)

Render builds through the repository `Dockerfile`, which copied individual Maven modules into `/workspace` but did not copy the root `config/` directory. The core Maven module packages this JSON from `${project.basedir}/../config`; therefore it is available during a local Maven package but absent during the Docker build. The resulting shaded service JAR had no classpath fallback and startup failed in `AccumulationShapeConfigLoader`. The Dockerfile now includes `COPY config config` before the Maven package command.

Validation: `mvn -pl service -am package -DskipTests` passed and the shaded `service/target/service-0.1.0-SNAPSHOT.jar` contains `accumulation_analysis_config.json`. A Docker build could not run locally because the Docker daemon is unavailable.
