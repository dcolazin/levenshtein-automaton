levenshtein-automaton
======================

A no-dependencies implementation of a [Levenshtein automaton](https://en.wikipedia.org/wiki/Levenshtein_automaton), 
built starting from two libraries developed by Kevin Lawson ([MDAG](https://github.com/klawson88/MDAG) and [LevenshteinAutomaton](https://github.com/klawson88/LevenshteinAutomaton)). 
Main improvement is the possibility to reuse the automaton.

## Dependency management

### Maven
```xml
<dependency>
    <groupId>org.vetronauta</groupId>
    <artifactId>levenshtein-automaton</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

Compile the automaton once for a reference string and a maximum edit distance, then reuse it for
any number of candidate strings:

```java
LevenshteinAutomaton automaton = LevenshteinAutomaton.compile("kitten", 2);

automaton.accepts("sitting");             // lev("kitten", "sitting") <= 2 → false
automaton.isWithinEditDistance("mitten"); // true, same instance
automaton.isWithinEditDistance("kitten"); // true, memoized lookups only
```

Two compilation modes are available:

- `LevenshteinAutomaton.compile(s, n)` — memo-based. Transitions resolve through the parametric
  transition table of `ParametricAutomaton`, shared process-wide per distance and materialized
  lazily: construction is O(1) and memory grows only with the (state, characteristic-vector) pairs
  actually used by queries. This scales to large max edit distances (up to
  `ParametricAutomaton.MAX_SUPPORTED_EDIT_DISTANCE = 28`, beyond which compilation transparently
  falls back to simulation).
- `LevenshteinAutomaton.compileIterative(s, n)` — table-free. Transitions are computed on the fly
  with no shared memo growth at all. Useful when even memoized state growth is undesired.

Reference strings of any length are supported for every practical max edit distance. Only the
extreme corner of large distances on long strings (`|s| > 63` and `n > 31`) degrades gracefully:
those instances answer membership queries via length bounds plus the dynamic-programming
baseline, the appropriate algorithm at such distances anyway. See `PLAN.md` for the full design
rationale.