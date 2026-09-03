# Interprocedural Resource Leak Companion

Flags a resource delegated to a helper method that doesn't guarantee
closing it, where the caller never closes it either.

## Why it exists

CWE-772 (Resource Leak), in the interprocedural form academic
frameworks like LeakChecker (built on Soot, using demand-driven
points-to analysis) document as genuinely harder to detect than the
single-method case -- real tools (FindBugs, Coverity, Infer) exist for
this, all CI/build-step, not inline IDE plugins. The platform's own
bundled "JDBC resource opened but not safely closed" inspection covers
the simple never-touched-at-all case within one method; this plugin
adds the genuinely new, interprocedural angle on top, deliberately
gated so it never re-flags what the bundled inspection already does.

## Why built this way

- **Combines two techniques this catalog already proved separately**
  -- the real, from-scratch Tarjan's SCC algorithm and fixed-point
  summary computation (`log-injection-companion`'s own approach,
  interprocedural) with the branch-merging path-sensitive typestate
  engine (`jdbc-double-close-companion`'s own approach,
  path-sensitive). A helper method's own "does it close this parameter
  on every path" summary is ITSELF computed path-sensitively (both
  branches of every fork inside the helper are visited and merged),
  then that summary propagates across the whole project's call graph
  via the same SCC-ordered fixed point real mutual/recursive helper
  relationships need.
- **Only fires on a real delegation attempt** -- a local resource
  variable that's simply never touched at all is the bundled
  inspection's job, not this plugin's; this one specifically requires
  the variable to have been PASSED to a project method first.

## v0.1 scope — stated honestly, not exhaustively

- Only `Connection`/`Statement`/`PreparedStatement`/
  `CallableStatement`/`ResultSet`/`InputStream`/`OutputStream`/
  `Reader`/`Writer`.
- Only a direct reference argument (`helper(conn)`) -- a wrapped
  expression (`helper(wrap(conn))`) breaks the chain.
- A `catch` block's starting state is the state BEFORE the `try` (same
  conservative approximation as `jdbc-double-close-companion`); a loop
  body is analyzed for one iteration merged with zero.
- A project with more than 3,000 analyzable methods (methods with at
  least one resource-typed parameter) skips analysis entirely.

## Usage

Open a Java method that creates a resource, passes it to a helper
method (in any class) that doesn't guarantee closing it on every path,
and never closes it itself -- the delegation call site shows a warning.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
