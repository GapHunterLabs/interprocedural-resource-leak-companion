<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Interprocedural Resource Leak Companion Changelog

## [Unreleased]

## [0.1.1]

### Fixed

- The interprocedural fixed-point summary computation (whole-project
  Tarjan-SCC + per-file scan) now checks for cancellation
  (`ProgressManager.checkCanceled()`) once per file and once per
  fixed-point iteration -- a large real project could previously block
  the read action uncancellably while the user kept typing. Catalog-wide
  gap found via manual review, retrofitted here.

## [0.1.0]

### Added

- Combines a real, from-scratch Tarjan's SCC fixed-point call-graph
  analysis with a branch-merging path-sensitive typestate engine:
  flags a resource delegated to a helper method that doesn't guarantee
  closing it on every path, never closed by the caller either
  (CWE-772), deliberately gated to never duplicate the platform's own
  bundled leak inspection.

[Unreleased]: https://github.com/GapHunterLabs/interprocedural-resource-leak-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/GapHunterLabs/interprocedural-resource-leak-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/interprocedural-resource-leak-companion/commits/0.1.0
