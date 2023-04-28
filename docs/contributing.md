# Contributing

Contribution is more than welcome!
If you would like to contribute code to FlowRedux you can do so through GitHub by forking the repository and sending a pull request.

## Ktlint
The project uses [Ktlint](https://pinterest.github.io/ktlint) to make the code base consistent.
You can run the formatter by using these commands:

```shell
# This runs the lint formatter
 ./kotlinw .kts/ktlint.main.kts

# This runs the lint checker
 ./kotlinw .kts/ktlint.main.kts --fail-on-changes
```
The CI runs the check, too.