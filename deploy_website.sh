set -ex
cp README.md docs/index.md
rm -rf docs/Javadoc
./gradlew dokka
mkdocs build
git checkout gh-pages
find . -mindepth 1 ! -regex '^./site' -delete
cp -R site/ .
git add .
git commit -am "Releasing new version of docs"
git push origin gh-pages