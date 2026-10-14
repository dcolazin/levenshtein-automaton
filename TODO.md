# Refactoring TODO — Reusable Levenshtein Automaton

Companion to `PLAN.md`. Phases 0–4 are complete; Phase 5 items are documented
follow-ups (non-blocking).

## Phase 0 — Docs

- [x] `PLAN.md` (analysis → plan document)
- [x] `TODO.md` (this file)

## Phase 1 — ParametricAutomaton extraction

- [x] `ParametricState`: precomputed acceptance threshold offset `min_j(e_j − off_j)`
      (`getAcceptanceThresholdOffset()`) — state accepting iff `minB >= |s| − n + offset`
- [x] New `ParametricAutomaton.java`:
  - [x] table build moved from `LevenshteinAutomaton` **as-is** (behavior frozen), t-position
        subsumption quirk preserved with an explanatory `// NOTE:` comment
  - [x] thread-safe per-`n` cache via `ParametricAutomaton.forDistance(int)` +
        `ConcurrentHashMap.computeIfAbsent`
  - [x] API: `transition(ParametricState, AugBitSet)`, `getInitialState()`, `getStates()`,
        `getMaxEditDistance()`, `getStateCount()`
  - [x] lifecycle: `static clearCache()`, `static getCachedDistanceCount()`
- [x] `LevenshteinAutomaton`: moved code + `transitionHashMapContainerHashMap` removed

## Phase 2 — Instance API (the D(s,n) object)

- [x] Instance fields incl. lazy `ConcurrentHashMap<Character, AugBitSet>[]` vector memo
- [x] Factories: `compile(String s, int n)` (table mode), `compileIterative(String s, int n)`;
      validates `s != null`, `n >= 0`; private constructor
- [x] `accepts(String)` / `isWithinEditDistance(String)`:
  - [x] table mode: pure `(ParametricState, minB)` walk — no concrete `State` allocation
  - [x] iterative mode: `State` simulation with memoized vectors
- [x] `characteristicVector(int minB, char c)` memoized, clamped at 0 (parity verified by test)
- [x] O(1) acceptance `isAcceptingParametricState(ps, minB)` (package-private for tests)
- [x] Instance `tableFuzzySearch(MDAG)` / `iterativeFuzzySearch(MDAG)` / `fuzzySearch(MDAG)`
      (dispatches on instance mode); typed `TableProcessingStep`/`IterativeProcessingStep`
      replaced the `Object[]` stack entries
- [x] Deprecated static facades delegating (source/binary compat):
  - [x] `isWithinEditDistance(int, String, String)` → `compile(s1, n).accepts(s2)`
  - [x] `tableFuzzySearch(int, String, MDAG)` → `compile(s, n).tableFuzzySearch(mdag)`
  - [x] `iterativeFuzzySearch(int, String, MDAG)` → `compileIterative(s, n).iterativeFuzzySearch(mdag)`
- [x] `public static isAcceptState(State, int, int)` kept

## Phase 3 — Tests (TestNG, Java 21)

- [x] `ParametricAutomatonTest`:
  - [x] exhaustive characterization vs `ClassicLevenshtein` — binary alphabet {a,b} len ≤ 5,
        `n ∈ {0,1,2}` for table mode + iterative + deprecated static; ternary alphabet {a,b,c}
        len ≤ 4, `n ∈ {1,2}` table mode and `n ∈ {1,2,3}` iterative mode
        (adjustment from plan: table-mode coverage capped at `n ≤ 2` because the `n = 3`
        parametric table exhausts the default test heap — consistent with the library's own
        documented guidance)
  - [x] acceptance-formula equivalence vs `isAcceptState` over all table states × all `minB`
  - [x] characteristic-vector memo parity vs `State.getRelevantSubwordCharacteristicVector`
  - [x] cache contract (`forDistance` reuse, `clearCache`) + invalid-argument rejection
- [x] `ReusableLevenshteinAutomatonTest` (words.txt dictionary):
  - [x] reuse correctness: one compiled instance, every dictionary word as candidate, both
        modes, vs DP baseline
  - [x] `fuzzySearch`/`tableFuzzySearch`/`iterativeFuzzySearch` result sets == DP baseline ==
        deprecated statics
  - [x] repeated queries on the same instance are stable (memoization never changes answers)
- [x] Existing test classes untouched and green

## Phase 4 — Bench + docs

- [x] `SimpleBenchmark`: `classic`/`automaton` single-shot kept; added `classicReused` and
      compiled-instance reuse scenarios at `n = 2` and `n = 4`
- [x] `Main.java`: reuse demo (compile once, verdicts for several candidates)
- [x] README usage section (instance API + mode guidance matrix + deprecation note)
- [x] Full build green: `mvn test` → 4139 tests, 0 failures, 0 errors, both modules SUCCESS

## Phase 5 — Optional follow-ups (non-blocking)

- [x] ~~deliberate fix + regression test for the t-position subsumption quirk in
      `procureBasedStates`~~ — resolved by Phase 6: the eager enumeration containing the quirk was
      deleted; states are now discovered on demand, never enumerated
- [ ] `precompute()` / eager materialization warm-up if profiling demands (post-Phase 6 the lazy
      memo fills during the first queries, so a warm-up is rarely needed)
- [ ] drop deprecated static facades in a future major version

## Phase 6 — Scalable data structures for large max edit distances

Motivation: table-mode construction eagerly materialized `states × 2^(2n+1)` vectors as nested
`HashMap`s of `AugBitSet` keys (~130 KB per state at `n = 4`) — distances beyond 2 were
unbuildable and the committed benchmark could not even run. See `PLAN.md` section 7 for the
design.

- [x] `ParametricAutomaton`: rewritten as a lazily materialized transition memo — state forms
      interned to dense int ids; transitions computed on demand from the existing
      `State`/`Position` machinery and memoized compactly; failure transitions memoized
      (sentinel); construction O(1); eager enumeration (and its t-position subsumption quirk)
      deleted; `MAX_SUPPORTED_EDIT_DISTANCE = 28` (vector packing limit)
- [x] Characteristic vectors packed into primitive longs (`bits << 6 | size`), replacing
      `AugBitSet` keys and per-step allocations
- [x] Per-state copy-on-write sorted `long[]` transition snapshots (16 bytes/entry, lock-free
      reads) replacing nested `HashMap`s; transition values pack
      `(targetStateId << 7) | boundaryOffset`
- [x] `LevenshteinAutomaton`: per-character occurrence bitmasks → O(1) zero-allocation vector
      lookup (replaces the per-(boundary, char) `ConcurrentHashMap` memo); memo walk on interned
      state ids; automatic simulation fallback for `n > 28`
- [x] Tests: memo-mode exhaustive coverage extended to distance 3 (binary/ternary) plus
      big-distance exhaustive/targeted tests at distances 4/5/6 and fallback checks;
      packed-vector parity vs `State.getRelevantSubwordCharacteristicVector`;
      acceptance-formula test drives lazy discovery first
- [x] Bench: distance-4 and distance-8 reuse scenarios run out of the box (construction is
      instant); `Main` demo includes distance 8
- [x] README/PLAN/TODO updated; full `mvn test` green
