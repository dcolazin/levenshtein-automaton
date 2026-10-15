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

## Phase 7 — Post-MDAG slimming (throw away more first-implementation code)

Motivation: with the MDAG dictionary gone (user's cleanup), the remaining first-implementation code
was dead weight — and `ReusableLevenshteinAutomatonTest` still referenced the deleted MDAG, so the
test suite did not even compile. See `PLAN.md` section 9 for the design notes.

- [x] Fixed the broken test compile: `ReusableLevenshteinAutomatonTest` rewritten sans MDAG
      (dictionary baseline for both compilation modes, distances 1–3 + repeated-query stability)
- [x] `AugBitSet` deleted entirely (167 lines); `State`/`Position` transitions operate on
      primitive `(bits, size)` vectors; the memo miss path passes unpacked primitives (no per-miss
      allocation)
- [x] Deleted `State.getRelevantSubwordCharacteristicVector`, `State.transition(int, String, char)`,
      `State.canBeState`, `Position.procurePositionTransitionData` (folded into the primitive
      `transition(int, int, long, int)`), `ParametricState`'s offset remnants (field, constructor,
      getter, `toString` branch), `ParametricAutomaton.getStateCount()`, `toAugBitSet()`,
      `ClassicLevenshtein.fuzzySearchNonAutomaton`, and the deprecated static facade
      `LevenshteinAutomaton.isWithinEditDistance(int, String, String)`
- [x] `LevenshteinAutomaton`: occurrence masks built in both modes (simulation uses them too via
      `windowBits`); stale `fuzzySearch` Javadoc removed
- [x] Window-fit validation added: `|s| > 63 && n > 31` now throws `IllegalArgumentException`
      (tested, including the boundary case `n = 31` on a 64-char string)
- [x] Tests: `StateTest` and `LevenshteinAutomatonTest` deleted (coverage consolidated);
      `PositionTest` trimmed; packed-vector parity now uses a first-principles oracle
- [x] Bench single-shot scenario uses `compile(...).accepts(...)`; README de-MDAG-ed;
      `dependency-reduced-pom.xml` git-ignored
- [x] Full `mvn clean test` green: 2013 tests, 0 failures, 0 errors (main sources 2334 → 1804
      lines)

## Phase 7.1 — Graceful degradation for extreme distances on long strings

Follow-up to the Phase 7 window cap (which initially threw `IllegalArgumentException` for
`|s| > 63 && n > 31`): `compile` now degrades that corner to a DP-backed membership check, so no
(distance, length) combination is ever rejected. Long strings were always usable for practical
distances (memo ≤ 28, simulation ≤ 31); now nothing throws.

- [x] `dpBacked` mode in `LevenshteinAutomaton`: length-bound reject (`||s|−|x|| > n`), trivial
      accept (`|s|+|x| <= n`), then `ClassicLevenshtein` DP
- [x] Occurrence masks skipped for DP-backed instances (null — never used)
- [x] Tests: degraded-mode correctness covering all three answer paths + the `n = 31` boundary
- [x] README, PLAN §8.4/§9 amended; full build green
