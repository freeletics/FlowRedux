set -ex
cp README.md docs/index.md
rm -rf docs/Javadoc
./gradlew dokka
mkdocs gh-deploy
rm -rf site