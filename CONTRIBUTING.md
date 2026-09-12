# Contributing

This is a standalone repository for an educational MDI demonstration. The MDI framework is maintained separately at https://github.com/heddle/mdi.

Use Java 17 or later and Maven. Follow the README to install the compatible MDI dependency, then run:

```sh
mvn clean verify
```

For UI changes, also run the desktop smoke check in the README and inspect the application at a normal desktop size. CI runs the headless numerical and simulation lifecycle tests; it does not replace visual review.

Keep numerical code under `model` independent of Swing and MDI. The simulation worker owns mutable model state; views consume immutable snapshots on the EDT. Reuse MDI lifecycle and plotting APIs. Keep histories bounded and stop workers during cleanup.

Describe the behavior changed and validation performed in pull requests. Add focused tests for numerical or lifecycle changes. Avoid expanding this demonstration into a full astrophysics package.

When changing the MDI dependency, update `pom.xml`, the pinned framework revision in `.github/workflows/build.yml`, and the README setup instructions together. Verify the new revision is available in the upstream repository before submitting.
