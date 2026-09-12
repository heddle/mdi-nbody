# MDI N-body

A compact Java 17 demonstration of the MDI framework: softened Newtonian gravity in two dimensions, immutable snapshots, linked views, and simulation controls. Uses dimensionless units and 2–50 point masses.

This application has its own repository; the [MDI framework](https://github.com/heddle/mdi) is a separate dependency.

## Build and run

Requires JDK 17+, Maven, and a graphical desktop to run the application. Tests run headlessly. The dependency is `io.github.heddle:mdi:1.2.3-SNAPSHOT`; install it from the compatible framework revision before building. From the root of this repository, a fresh setup is:

```sh
git clone https://github.com/heddle/mdi.git .build-deps/mdi
git -C .build-deps/mdi checkout ea9823f8aceec7b827731f5fdbd55f4528200a42
mvn -f .build-deps/mdi/pom.xml -DskipTests -Dmaven.javadoc.skip=true install
mvn verify
mvn compile exec:java
```

The temporary `.build-deps/` checkout is ignored by Git and is not included in this repository. If this exact MDI revision is already installed in your Maven cache, skip the clone and dependency installation steps. CI follows the same pinned-source setup.

No external data or image assets are required. `mvn package` produces the application JAR; use Maven's launcher to supply its dependencies. The dependency version is configurable with `-Dmdi.version=...`, but must provide the current simulation and sPlot APIs.

## Using the demonstration

- **Start** begins the initial run; **Pause** and **Resume** preserve state. **Stop** terminates the worker; press **Reset** to prepare another run. **Single Step** advances exactly one fixed timestep while paused.
- Choose a preset, integrator, G, epsilon, and timestep, then press **Reset**. Cluster count and seed apply to the random cluster. Unchanged settings reproduce the exact initial state.
- Display rate (1–60 updates/s), trail length (0–5,000 display samples), and overlays change immediately. Rendering rate does not change the numerical timestep. The worker is paced approximately in real time for small timesteps; elapsed simulation time is always step count × dt.
- Use MDI's pointer to select a body, the hand to pan, and the zoom/box-zoom/reset tools to navigate. Body radii are bounded in screen pixels and do not represent physical radii. Arrows show velocity multiplied by 0.4 time units.
- **Energy** plots kinetic, potential, and total energy. Its checkbox switches to `(E−E₀)/|E₀|`; this is undefined when `|E₀| ≤ 10⁻¹⁴`. **Angular momentum** plots total Lz. **Selected body** shows mass, coordinates, velocity, speed, kinetic energy, and distance from the center of mass. The simulation footer also shows momentum, COM, and relative energy error.
- Views use the standard MDI menus and saved layout. Each plot retains up to 10,000 display samples, then begins a new block; Reset clears plots and trails. These are sampled visual diagnostics, not a record of every integration step.

Presets: softened two-body circular orbit; eccentric two-body orbit; equal-mass figure-eight; a binary with a third low-mass body; and a reproducible random cluster. Figure-eight initial conditions describe an unsoftened Newtonian choreography, so positive epsilon perturbs its exact periodicity. The random cluster is deliberately not an equilibrium model.

## Numerical model and architecture

For each unordered pair, the acceleration is

```text
aᵢ += G mⱼ (rⱼ − rᵢ) / (|rⱼ − rᵢ|² + ε²)^(3/2)
aⱼ -= G mᵢ (rⱼ − rᵢ) / (|rⱼ − rᵢ|² + ε²)^(3/2)
Uᵢⱼ = −G mᵢ mⱼ / sqrt(|rⱼ − rᵢ|² + ε²)
```

Velocity-Verlet performs a half velocity kick, a position drift, and a second half kick using recomputed accelerations. Explicit Euler uses old velocities for the position update and is included for comparison. Epsilon must be positive. Softening removes the force singularity; a large fixed timestep can still produce inaccurate trajectories, especially during close encounters. Reduce dt and compare energy error to study convergence.

- `model`: pure Java records, presets, pair forces, integration, and diagnostics; no Swing or MDI imports. `NBodyModel` owns private mutable arrays. Snapshots contain immutable body records and a defensive copy of their list.
- `sim`: `NBodySimulation` adapts the model to MDI's `SimulationEngine`. Only the engine worker advances state; a volatile snapshot publishes a complete frame. Refresh requests use MDI's coalescing. An initial acknowledged pause avoids the framework's READY/start timing race.
- `view`: `SimulationView` supplies engine replacement/reset and EDT callbacks; controls implement MDI's host/listener contract. MDI containers supply navigation and world transforms. Plot views use sPlot and its edit/export menus. Histories and selection belong to the EDT. Reset waits for the old engine to terminate before swapping; closing prevents pending reset callbacks from launching workers.

No collisions, merging, adaptive stepping, 3D, or astronomical data services are included.

## Verification

`mvn test` runs numerical and engine lifecycle tests: force symmetry, moving center of mass and momentum conservation, maximum sampled energy/angular-momentum drift over an eccentric orbit, a 100,000-step circular-orbit stability check, seeded reproducibility, exact reset, snapshot immutability, overlap softening, Euler behavior, validation, and single-step/pause/resume/stop behavior.

An explicit desktop smoke check opens the application, exercises stepping and reset while running, renders the four views, and verifies worker shutdown. It requires a graphical display and saves `target/desktop-smoke.png`:

```sh
mvn test-compile exec:exec -Dexec.executable=java \
  -Dexec.classpathScope=test \
  -Dexec.args='-classpath %classpath edu.cnu.mdi.nbody.DesktopSmoke'
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development and validation conventions.

## License

Copyright © 2026 David Heddle. Distributed under the [MIT License](LICENSE).
