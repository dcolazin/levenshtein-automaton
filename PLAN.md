# Refactoring Plan: Making the Levenshtein Automaton Reusable

> Status: approved &mdash; see `TODO.md` for the step-by-step execution checklist.

The README states the goal of this project: *"Main improvement is the possibility to reuse the
automaton."* The current code does not deliver it: `lev(s, s1) < n` and `lev(s, s2) < n` both
(re)build the full automaton, while the Levenshtein automaton was implemented exactly for reuse.
This document analyzes the problem and defines the target design and migration path.

## 1. Where the problem lives today

The library already contains the machinery for reuse &mdash; the parametric transition table
(Schulz&ndash;Mihov style) &mdash; but the API makes it impossible to exploit it. Concretely, in
`LevenshteinAutomaton.java`:

| Method | What it builds per call | Reuse across calls |
|---|---|---|
| `isWithinEditDistance(n, s, s')` (L59) | Every reachable `State`/`Position` of D(s,n), **by on-the-fly simulation** from the shared static `initialState` | **None** |
| `iterativeFuzzySearch(n, s, mdag)` (L386) | Same per-step simulation, synced with MDAG traversal | **None** |
| `tableFuzzySearch(n, s, mdag)` (L482) | Parametric table cached in static `transitionHashMapContainerHashMap` (L41) keyed by `n` &mdash; but still recomputes characteristic vectors and materializes concrete `State`s per step, per call | Table only (per `n`, not per `s`) |

So `lev(s, s1) < n` and `lev(s, s2) < n` each re-derive the whole automaton: every
`State.transition(n, s, char)` call (State.java ~L193) allocates a substring + `AugBitSet` for the
characteristic vector, then re-runs all member `Position` transitions with `HashSet` churn and
array sorts &mdash; and the only cached artifact (the parametric table) is not even used by the
membership method. The Javadoc's own admission ("too slow to use in production") is the symptom:
the automaton was built for reuse, but nothing in the API can hold the reused thing.

## 2. Root causes

1. **API shape** &mdash; everything is a `static` utility taking `(n, s, s')` on every call. There
   is no object representing D(s, n), so there is nothing to attach per-automaton caches to. This
   is the core design flaw.
2. **The reusable structure is hidden** &mdash; the string-independent parametric table is a
   private static `HashMap`, populated lazily, and consumed by exactly one method.
3. **Per-step recomputation** &mdash; characteristic vectors depend only on
   `(minimalBoundary, char)` relative to `s`, yet they are recomputed for every state, every char,
   every call; concrete states are re-materialized via `ParametricState.createActualState` at each
   step; acceptance is re-scanned over positions each time.
4. **Unsafe global cache** &mdash; `transitionHashMapContainerHashMap` is a plain `HashMap` filled
   without synchronization (race on first concurrent `tableFuzzySearch` per `n`), unbounded, with
   no lifecycle/eviction.
5. **Coupled responsibilities** &mdash; `LevenshteinAutomaton` mixes parametric-table construction,
   membership simulation, MDAG traversal, and acceptance logic; untyped `Object[]` stack entries
   (`createProcessingStepStackEntry`) make the traversal fragile.

## 3. Target design

Split the single static god-class into cohesive types (package layout unchanged;
`State`/`Position`/`ParametricState`/`AugBitSet` semantics unchanged):

```
ParametricAutomaton            (per n, string-independent, shared)
   |- owns Map<ParametricState, Map<AugBitSet, ParametricState>>
   |- factory: ConcurrentHashMap.computeIfAbsent -> thread-safe, one build per n
   |- transition(ParametricState, AugBitSet), getInitialState(), getStates(), metrics
   |- static clearCache() for lifecycle management

LevenshteinAutomaton           (instance = D(s, n), the missing object)
   |- static compile(String s, int n)          // table-based instance
   |- static compileIterative(String s, int n) // table-free instance (for large n)
   |- boolean accepts(String candidate)        // lev(s, candidate) <= n
   |- boolean isWithinEditDistance(String candidate)
   |- LinkedList<String> fuzzySearch(MDAG)     // dispatches per instance mode
   |- LinkedList<String> tableFuzzySearch(MDAG)
   |- LinkedList<String> iterativeFuzzySearch(MDAG)
   |- internals: s, n, |s|, ParametricAutomaton ref, per-instance memos

(static facade on LevenshteinAutomaton)        // deprecated delegators, keeps API compat
```

Usage becomes exactly the reuse pattern the theory promises:

```java
LevenshteinAutomaton la = LevenshteinAutomaton.compile("kitten", 2);
la.accepts("sitting");   // cheap: memoized lookups
la.accepts("written");   // same automaton, near-zero new work
la.fuzzySearch(mdag);    // traversal reuses the same instance state
```

### 3.1 Theoretical basis

A Levenshtein automaton for `lev(s, ·) <= n` depends on `s` only through the *characteristic
vectors* (bit vectors of length at most `2n+1`) of the consumed character relative to the current
window of `s`. Hence one *parametric* automaton per `n` serves **all** strings `s`, and a concrete
run of D(s, n) is just table lookups plus boundary arithmetic. The library already implements this
(`ParametricState` + transition map) &mdash; the refactor surfaces it as a first-class, reusable
object.

### 3.2 The three per-instance optimizations that make `accepts` cheap

1. **Characteristic-vector memo** &mdash; the vector for a step depends only on
   `(minimalBoundary, char)`; memoize `Map<Character, AugBitSet>` per boundary index. Kills the
   per-step substring/`BitSet` allocations.
2. **Pure (ParametricState, minimalBoundary) simulation** &mdash; the table traversal does not need
   to materialize concrete `State` objects at all: the next state's minimal boundary is
   `minB + next.getTransitionBoundaryOffset()`. The parametric table *is* the memoized DFA.
3. **O(1) acceptance** &mdash; from `isAcceptingPosition` (`|s| − (minB + off_j) <= n − e_j`) each
   `ParametricState` precomputes `c = min_j(e_j − off_j)`; a state is accepting iff
   `minB >= |s| − n + c`. No per-position scan. (The derivation only uses `i`/`e`, so it is exact
   for transposition positions too.)

For large `n` (where the table's `2^(2n+1)` vectors blow up memory &mdash; the reason the current
docs discourage `n > 2`), `compileIterative(s, n)` produces an instance that runs today's on-the-fly
`Position` transitions while still keeping the vector memo.

### 3.3 Fixes that fall out for free

- **Thread safety:** table built once via `computeIfAbsent`; instances are immutable-after-compile
  (lazy memo population uses benign-race `ConcurrentHashMap`s) &rarr; safely shareable.
- **Typed traversal:** replace `Object[]` steps with small final `ProcessingStep` classes
  (Java-8-compatible, matching the lib's `maven.compiler.target`).
- **Cache lifecycle:** `ParametricAutomaton.clearCache()` / size introspection become expressible.

## 4. Migration plan (incremental, behavior-preserving)

1. **Extract `ParametricAutomaton`** &mdash; move `createParametricStateTransitionMap`,
   `procureParametricStates`, `procureBasedStates`, `procureNewBasedStates`,
   `produceBinaryPermutations` and the static cache into it. No behavior change; add
   characterization tests (all strings <= ~6 chars over `{a,b,c}`, `n in {0..3}`, vs
   `ClassicLevenshtein`) to freeze semantics &mdash; the transposition-aware distance must stay
   intact.
2. **Introduce the instance API** &mdash; `compile`/`compileIterative`, `accepts`, the three
   memos, O(1) acceptance, instance fuzzy searches with typed steps. Make the static
   `isWithinEditDistance(n, s, s')`, `tableFuzzySearch(n, s, mdag)`,
   `iterativeFuzzySearch(n, s, mdag)` delegate and mark them `@Deprecated`. Existing tests (which
   already check against the DP baseline) keep passing.
3. **Update `levenshtein-automaton-bench`** &mdash; add the decisive scenario: *compile once, query
   many candidates* vs `classic` per-query (the current benchmark only measures single-shot, where
   the automaton cannot win by construction); replace `Main`'s infinite loop; document the
   guidance matrix (table mode `n <= 2`, iterative for larger).
4. **Docs** &mdash; README usage section for the instance API.

## 5. Expected outcome

| Metric (per candidate char) | Today (`isWithinEditDistance`) | After refactor |
|---|---|---|
| Allocations | substring + `AugBitSet` + `HashSet`s + `Position[]` + sort | ~0 (memoized vectors, no concrete states in table mode) |
| Work | re-derive all transitions from scratch | 1–2 hash lookups + O(1) accept check |
| Setup cost | none — but repeated every call | one `compile(s, n)`; parametric table amortized over *all* strings at distance `n` |

Memory cost is bounded and explicit: table ~= S(n) x 2^(2n+1) entries shared globally per `n`;
per-instance memos ~= (|s|+1) map slots x observed alphabet for vectors.

## 6. Risks & notes

- **Latent quirk to pin down:** in `procureBasedStates` (~L146 of the original
  `LevenshteinAutomaton`) the t-position branch tests `basePosition.subsumes(currentPosition)` (the
  non-*t* twin) but then adds `currentTPosition` &mdash; likely a harmless superset of parametric
  states, but the characterization tests must verify behavior is frozen before/after the table
  move. The code is moved **as-is** (quirk preserved) and flagged with a comment; fixing it is a
  separate, deliberate change (Phase 5).
- **API compatibility** is preserved via deprecated delegators; a later major version can drop
  them. New validation on the new API only (`n >= 0`, non-null `s`).
- **Semantics freeze:** restricted-Damerau behavior must not drift &mdash; enforced by the
  exhaustive tests.
- **Memory guidance:** table mode intended for `n <= 2` (as today); `compileIterative` covers the
  rest.

## 7. Revision — scalable data structures (Phase 6)

The table-mode design of section 3 still eagerly materialized the full cross product of parametric
states × characteristic vectors as `Map<ParametricState, HashMap<AugBitSet, ParametricState>>`,
whose cost (object-heavy `AugBitSet` keys, boxed entries, nested `HashMap` tables — roughly
130 KB per state at `n = 4`, times thousands of states) made distances beyond 2 practically
unbuildable: memory is O(states(n) × 2^(2n+1)) regardless of what queries actually need. Four
data-structure changes fix this:

1. **Lazy transition memo instead of eager cross-product.** `ParametricAutomaton` now interns
   parametric state forms to dense `int` ids and computes transitions on demand from the existing
   `State`/`Position` machinery, memoizing only the (state, vector) pairs actually touched by
   queries. Construction is O(1); memory ∝ usage. The eager state enumeration
   (`procureParametricStates`, `procureBasedStates`, `procureNewBasedStates`,
   `produceBinaryPermutations`) is deleted — and with it the t-position subsumption quirk of
   section 6, which lived exclusively in that enumeration. Failure transitions are memoized too
   (a sentinel value).
2. **Characteristic vectors as packed primitive `long`s** (`bits << 6 | size`, size ≤ 2n+1 ≤ 57)
   replace `AugBitSet` keys and per-step allocations.
3. **Per-state copy-on-write sorted `long[]` transition snapshots** (16 bytes per entry; immutable
   snapshots make reads lock-free, writes are synchronized and rare) replace the nested
   `HashMap`s. Transition values pack `(targetStateId << 7) | boundaryOffset`.
4. **Per-character occurrence bitmasks of the reference string** replace the per-instance
   `(boundary, char)` vector memo: the vector is `(occMask[c] >>> minB) & windowMask` — O(1) and
   allocation-free.

`compile(s, n)` now scales to any practical distance. The memo's vector packing supports
distances up to `ParametricAutomaton.MAX_SUPPORTED_EDIT_DISTANCE = 28`; beyond that, compilation
transparently falls back to the table-free simulation (which works for arbitrary `n`). The
exhaustive characterization tests now cover memo mode through distance 6 (the former eager table
exhausted the default heap building distance 3), and the committed benchmark runs `n = 4` and
`n = 8` scenarios out of the box.

## 8. Formal properties, limitations & representability

### 8.1 What is proven, assumed, and tested

Correctness rests on three properties *assumed from the literature* (pinned by bounded-exhaustive
tests, not machine-checked proof):

1. **Position transition table** — `Position.procureTransition` is a faithful transcription of
   the Schulz–Mihov position-transition figure (including the delete-to-first-hit rule). It is
   the oracle for every on-miss transition computation.
2. **Parametricity** — the transition function of a state depends only on its *form* and the
   characteristic vector, never on the absolute boundary. This underwrites computing every
   transition from `createActualState(0)` and sharing one memo per distance across all reference
   strings.
3. **Subsumption reduction** — `Position.subsumes` is a valid position-set reduction for the
   metric.

Mechanical properties discharged in-code or by derivation:

- **Acceptance formula** (`minB >= |s| - n + min_j(e_j - offset_j)`) is an exact algebraic
  rewriting of the per-position rule (it uses only `i`/`e`, so it holds for t-positions too) and
  is additionally verified by test over every discovered state × every minimal boundary.
- **Packing bounds** are validated at pack time (`packVector` size checks, the boundary-offset
  guard).

Empirical assurance: exhaustive equivalence vs `ClassicLevenshtein` over alphabets {a,b}
(lengths ≤ 5, distances 0–3) and {a,b,c} (lengths ≤ 4, distances 1–3), big-distance
exhaustive/targeted checks at distances 4–6, plus dictionary-level tests on `words.txt`.

### 8.2 Semantic scope

- The metric is **restricted Damerau–Levenshtein** (optimal string alignment): insert, delete,
  substitute, transpose adjacent, no substring edited more than once. It is *not* unrestricted
  Damerau–Levenshtein; both the automaton and the DP baseline implement the same metric.
- Characters are **UTF-16 code units**: a transposition may cross a surrogate pair, and distances
  differ from code-point semantics; no Unicode normalization is applied.

### 8.3 Was the pre-Phase-6 (eager) implementation formally correct, given infinite memory?

Component-wise: parametricity sound; characteristic-vector enumeration provably complete (all bit
strings of length 0..2n+1 plus the empty one); transition/offset/acceptance bookkeeping sound.
The weak link was the **state enumeration** in `procureBasedStates`: the t-position branch tested
subsumption of the *standard twin* (`|i − I| ≤ e`) instead of the t-position itself (correct:
`|i − (I−1)| ≤ e`). With base `(I, 0)` and edit count `e`:

- the twin admits `i ∈ [I−e, I+e]`; the correct predicate admits `i ∈ [I−e−1, I+e−1]`;
- hence the enumeration **over-admitted** t-positions at `i = I+e` (unreachable extra forms —
  pure memory waste) and could **under-admit** at `i = I−e−1` (a completeness gap).

If a reachable form was ever omitted, the runtime lookup `transitionHashMap.get(form).get(vector)`
dereferenced null — a **fail-fast NPE, never a silently wrong answer**. Whether any reachable
state requires such a t-position was never determined, and could not be: the eager table
exhausted the heap at distances 3–4 while the test suite only randomized distances 1–2, so the
"infinite memory" premise was precisely the untestable one. Verdict: *empirically correct on every
input ever run; formally unestablished for the transposition construction.* Phase 6 removed the
proof obligation by construction: states are discovered by applying δ and interned on first
encounter, so the reachable set is covered trivially and unreachable forms cost nothing.

### 8.4 Representability: maximum sizes

| Quantity | Memo mode (`compile`) | Simulation (`compileIterative` / fallback) |
|---|---|---|
| Max edit distance | **28** (`MAX_SUPPORTED_EDIT_DISTANCE`) | any non-negative `int` |
| Reference string length | unbounded (see below) | unbounded |
| Candidate length | unbounded (early-exit on failure) | unbounded |
| Discovered states | `int` ids (theoretical 2^31) | — |

- **Why exactly 28:** a characteristic vector packs as `bits << 6 | size` in one `long`. The
  window has `min(2n+1, |s| − minB)` bits at bit-positions 0..2n, so no bit is lost iff
  `(2n+1) + 6 ≤ 63`, i.e. `n ≤ 28`. The 6-bit size field caps at 63 (window ≤ 57 here); the
  boundary offset packs in 7 bits, is provably ≤ 2n+1 ≤ 57 < 128 for supported `n`, and is
  guarded fail-fast regardless. Packed keys are always non-negative, keeping `binarySearch`'s
  signed order consistent.
- **String length is never packed.** Occurrence masks are `⌈|s|/64⌉`-word arrays per distinct
  character (`O(|Σ(s)| · |s|/64)` memory), and the packed vector encodes only the ≤ 57-bit
  *window* around the minimal boundary — a 1 MB reference string works exactly like a 6-char one.
  The only cap on `|s|` is Java's own `String` limit (2^31 − 1). Positions and the minimal
  boundary never exceed `|s|` (the deletion rule advances at most to the window's end), so all
  indices stay comfortably within `int` range.
- **The gate is conservative:** for reference strings with `|s| ≤ 57`, every window has ≤ 57 bits
  *regardless of n* (windows are `min(2n+1, |s| − minB)`), so the packing could represent any
  distance. The code gates on `n` alone for simplicity; a possible refinement is to gate on
  `min(2n+1, |s|) ≤ 57` and allow any `n` for short strings.
- **Beyond 28**, `compile` transparently downgrades to simulation: still automaton-based and
  correct, with per-query cost O(|candidate| · n) and no memo (a performance cliff, not a
  correctness gap). Beyond the 64-bit window limit (`n > 31` on strings longer than 63 chars),
  instances degrade further to a DP-backed membership check (length bounds plus the
  dynamic-programming baseline), so no (distance, length) combination is ever rejected.

### 8.5 Resource properties

- Memo memory is finite but **unbounded with usage** (proportional to touched (state, vector)
  pairs); there is no eviction, and `clearCache()` is global rather than per-distance. The cache
  itself is capped at 29 entries (distances 0–28).
- Transition misses serialize on a single lock per distance (no deadlock by construction; reads
  are lock-free immutable snapshots).

## 9. Post-MDAG slimming (Phase 7)

With the MDAG dictionary removed (it was only ever a consumer of the automaton), the remaining
first-implementation remnants became dead and were deleted:

- **`AugBitSet` (whole class)** — the object-based characteristic vector. `State`/`Position`
  transitions now operate directly on primitive `(bits, size)` windows (`long` + `int`), and the
  memo's miss path passes unpacked primitives, removing the last per-miss allocation and the
  `relevantBitSetSize` bookkeeping.
- `State.getRelevantSubwordCharacteristicVector` and `State.transition(int, String, char)` — the
  string-window computation lives in `LevenshteinAutomaton` (occurrence-mask based, now built in
  both compilation modes) and feeds `State.transition(int, long, int)`.
- `Position.procurePositionTransitionData` — folded into the primitive
  `Position.transition(int, int, long, int)` (windowing is a shift-and-mask; first-hit is
  `numberOfTrailingZeros`).
- `State.canBeState` — its only caller was the eager enumeration deleted in Phase 6.
- `ParametricState`'s `transitionBoundaryOffset` (field, constructor, getter, `toString` branch) —
  offsets are packed into the memo's transition values since Phase 6.
- `ParametricAutomaton.getStateCount()` and `toAugBitSet()`.
- `ClassicLevenshtein.fuzzySearchNonAutomaton` — the collection-based dictionary search.
- The deprecated static facade `LevenshteinAutomaton.isWithinEditDistance(int, String, String)` —
  the last remnant of the original static API (its MDAG siblings were already gone; tests and the
  benchmark now use the instance API).
- Tests: `StateTest` (tested only deleted methods) and `LevenshteinAutomatonTest` (duplicated the
  dictionary-baseline coverage now consolidated in `ReusableLevenshteinAutomatonTest`) were
  deleted; `PositionTest`'s `getPositionTransitionData` block went with the method it tested; the
  packed-vector parity test now uses an independent first-principles oracle instead of the deleted
  `State` helper.

**Representability consequence:** with `AugBitSet` gone, characteristic-vector windows must fit
in a `long`; the former "arbitrary `n` on arbitrary strings" simulation relied exactly on the
object-based vectors deleted here (a theoretical affordance only: distances beyond ~30 accept
essentially everything, and the memo is limited to 28 anyway). Rather than rejecting the corner
(`|s| > 63 && n > 31`), `compile` degrades it gracefully to a DP-backed membership check —
length-bound fast paths plus the dynamic-programming baseline, the appropriate algorithm at such
distances — so the library remains usable for every (distance, length) combination. Long strings
are unaffected for all practical distances: memo `n <= 28`, simulation `n <= 31`, any `n` for
`|s| <= 63`.
