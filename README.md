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

// dictionary (MDAG) search reusing the same compiled instance
LinkedList<String> neighbors = automaton.fuzzySearch(mdag);
```

Two compilation modes are available:

- `LevenshteinAutomaton.compile(s, n)` — table-based. Transitions resolve through the parametric
  transition table of `ParametricAutomaton`, which is built once per max edit distance and shared
  process-wide. Recommended for `n <= 2`; greater distances require huge amounts of memory.
- `LevenshteinAutomaton.compileIterative(s, n)` — table-free. Transitions are computed on the fly
  (characteristic vectors are still memoized per instance). Advised for larger max edit distances.

The pre-refactor static entry points (`LevenshteinAutomaton.isWithinEditDistance`,
`tableFuzzySearch`, `iterativeFuzzySearch`) are deprecated delegators kept for backward
compatibility: they rebuild an automaton per call, so prefer compiling and reusing instances. See
`PLAN.md` for the full design rationale.