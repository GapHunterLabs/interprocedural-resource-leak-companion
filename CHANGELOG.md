<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Interprocedural Resource Leak Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- Combines a real, from-scratch Tarjan's SCC fixed-point call-graph
  analysis with a branch-merging path-sensitive typestate engine:
  flags a resource delegated to a helper method that doesn't guarantee
  closing it on every path, never closed by the caller either
  (CWE-772), deliberately gated to never duplicate the platform's own
  bundled leak inspection.

[Unreleased]: https://github.com/GapHunterLabs/interprocedural-resource-leak-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/interprocedural-resource-leak-companion/commits/0.1.0
