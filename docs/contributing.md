# Contributing

Contribution is more than welcome!
If you would like to contribute code to FlowRedux you can do so through GitHub by forking the repository and sending a pull request.

## Ktlint
The project uses [Ktlint for gradle](https://pinterest.github.io/ktlint/install/integrations/#jlleitschuhktlint-gradle), this helps to make consistent the code base.
You can run the formatter by using these commands:

```shell
# This runs the lint formatter
 ./gradlew ktlintFormat

# This runs the lint checker
 ./gradlew ktlintCheck
```
The CI runs the `ktlintCheck`, too.