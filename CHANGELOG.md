# Changelog

All notable changes to the MDI N-body demonstration are documented in this
file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-09-15

### Added

- Two-dimensional softened Newtonian N-body simulation for 2–50 bodies, with
  Velocity-Verlet and explicit-Euler integrators.
- MDI-based interactive body selection, dragging, editing, deletion, graphical
  velocity adjustment, pan and zoom, and image capture.
- Presets for circular and eccentric two-body orbits, the figure-eight
  choreography, three chaotic suns and a low-mass planet, Sun–Mercury–Jupiter,
  a binary with a low-mass third body, and reproducible random clusters.
- Editable custom-model workflow with body creation and setup tools for
  centering the center of mass, removing net momentum, duplicating bodies,
  assigning circular velocity, reversing velocities, and scaling positions or
  velocities.
- Portable, versioned JSON files for saving and reopening user-created models.
- Run, pause, resume, reset, stop, and single-step controls built on MDI's
  `SimulationEngine`, with immutable snapshots between the worker and Swing
  event-dispatch threads.
- Energy, angular-momentum-error, and selected-body views, plus trails,
  velocity overlays, momentum and center-of-mass diagnostics.
- A non-modal Mercury-perihelion plot for the Sun–Mercury–Jupiter preset.

### Changed

- Updated the framework dependency to the released MDI 1.2.3 used by the final
  reference-book manuscript.
- Builds now resolve MDI directly from Maven Central instead of requiring a
  separately cloned and locally installed snapshot.

### Documentation & Testing

- Documented the numerical model, threading architecture, controls, editing
  semantics, diagnostics, preset limitations, and desktop smoke test.
- Added numerical, persistence, editable-setup, validation, reproducibility,
  engine-lifecycle, and long-orbit stability tests.

[Unreleased]: https://github.com/heddle/mdi-nbody/compare/v1.0.0...develop
[1.0.0]: https://github.com/heddle/mdi-nbody/releases/tag/v1.0.0
