Releasing
========

 1. Change the version in `gradle.properties` to a non-SNAPSHOT version.
 2. Update the `README.md` with the new version.
 3. `git commit -am "Prepare for release X.Y.Z."` (where X.Y.Z is the new version)
 4. `git tag -a X.Y.X -m "Version X.Y.Z"` (where X.Y.Z is the new version)
 5. Update the `gradle.properties` to the next SNAPSHOT version.
 6. `git commit -am "Prepare next development version."`
 7. `git push && git push --tags`
 8. Create a Github Release
