set -ex
cp README.md docs/index.md
mkdocs gh-deploy
rm -rf site