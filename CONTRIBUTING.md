# Contributing to Stream Sentinel

Thank you for considering contributing to Stream Sentinel! This document provides guidelines and information for contributors.

## Code of Conduct

By participating in this project, you agree to maintain a respectful, inclusive environment for everyone.

## How to Contribute

### Reporting Bugs

1. **Search existing issues** to avoid duplicates.
2. Open a new issue with:
   - A clear, descriptive title
   - Steps to reproduce the problem
   - Expected vs. actual behaviour
   - Environment details (Java version, Flink version, OS)

### Suggesting Features

Open an issue tagged `enhancement` with:
- A description of the feature and the problem it solves
- Any proposed approach or alternatives considered

### Submitting Pull Requests

1. **Fork** the repository and create a feature branch:
   ```bash
   git checkout -b feature/my-detector
   ```
2. **Write code** following the style guidelines below.
3. **Add tests** — every new detection rule or public API must have corresponding unit tests.
4. **Run the full build**:
   ```bash
   mvn clean package
   ```
5. **Commit** with clear, descriptive messages (imperative mood):
   ```
   Add PatternDetector for regex-based anomaly detection
   ```
6. **Open a Pull Request** against `main` with a clear description.

## Code Style

| Guideline | Detail |
|-----------|--------|
| Java version | 17+ features (records, switch expressions, pattern matching) |
| Architecture | `core-engine` has **zero** Flink dependency |
| Configuration | All values via environment variables — no hardcoding |
| Null safety | Use `Objects.requireNonNull` at public API boundaries |
| Javadoc | Required on all public classes and methods |
| Logging | SLF4J; use `LOG.debug` for rule-fires, `LOG.info` for lifecycle events |
| Tests | JUnit 5 + AssertJ; minimum one test per public method |

## Adding a New Detector

1. Create a class implementing `AnomalyDetector` in `core-engine/`.
2. Register the new type string in `DetectorFactory`.
3. Add corresponding fields to `DetectionRule` if needed (update `validate()`).
4. Write unit tests exercising fire, no-fire, and edge cases.
5. Add a sample rule to `examples/sample-rules.yml`.
6. Update the README's rule-type table.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
