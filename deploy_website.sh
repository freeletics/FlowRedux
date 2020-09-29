set -ex
cp README.md docs/index.md
rm -rf docs/Javadoc
./gradlew dokka
mkdocs build -d ../site
git reset --hard
git checkout gh-pages
ls | grep -v .git | xargs rm -rf
cp -R ../site/ .
git add .
git commit -am "Releasing new version of docs"
git push origin gh-pages