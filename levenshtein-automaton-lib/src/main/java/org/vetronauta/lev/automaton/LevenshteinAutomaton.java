/**
 * LevenshteinAutomaton is a fast and comprehensive Java library capable of performing automaton and
 * non-automaton based Levenshtein distance determination and neighbor calculations.
 *
 * <p>Copyright (C) 2012 Kevin Lawson <Klawson88@gmail.com>
 *
 * <p>Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vetronauta.lev.automaton;

import java.util.HashMap;
import java.util.Objects;

/**
 * A compiled, reusable Levenshtein automaton recognizing every String within a maximum edit
 * distance of a fixed reference String.
 *
 * <p>An instance represents the automaton D(s, n) for {@code lev(s, ·) <= n} and is the unit of
 * reuse: compile it once and answer any number of membership queries cheaply. Successive calls such
 * as {@code lev(s, s1) < n} and {@code lev(s, s2) < n} share the compiled instance (and the
 * process-wide parametric transition memo per distance) instead of rebuilding the automaton per
 * query.
 *
 * <pre>{@code
 * LevenshteinAutomaton automaton = LevenshteinAutomaton.compile("kitten", 2);
 * automaton.accepts("sitting");        // false, via memoized lookups
 * automaton.accepts("mitten");         // true, same instance
 * automaton.fuzzySearch(dictionary);   // dictionary search reusing the same instance
 * }</pre>
 *
 * <p>Two compilation modes are available:
 *
 * <ul>
 *   <li>{@link #compile(String, int)} — memo-based. Transitions resolve through the lazily
 *       materialized parametric transition table of {@link ParametricAutomaton} (shared
 *       process-wide per distance, compactly encoded as primitives; construction is O(1) and memory
 *       grows only with the (state, vector) pairs actually used). This scales to large max edit
 *       distances; beyond {@link ParametricAutomaton#MAX_SUPPORTED_EDIT_DISTANCE} it transparently
 *       falls back to simulation.
 *   <li>{@link #compileIterative(String, int)} — table-free. Transitions are computed on the fly
 *       from member positions, with no shared memo growth at all. Useful when even memoized state
 *       growth is undesired.
 * </ul>
 *
 * <p>Instances are immutable after compilation and safe to share across threads.
 *
 * @author Kevin
 */
public final class LevenshteinAutomaton {
    // The State that serves as the initial state of every (non-memo-based) automaton simulation
    private static final State initialState = new State(new Position[] {new Position(0, 0, false)});

    // The reference String that all edit distance calculations carried out by this automaton are
    // relative to
    private final String automatonString;

    // The length of automatonString (kept as a field to bypass repeated method calls)
    private final int automatonStringLength;

    // The maximum amount of edit operations allowed by this automaton
    private final int maxEditDistance;

    // The string-independent parametric automaton backing memo-based operations; null for
    // instances compiled with compileIterative (or beyond the packing-supported distance)
    private final ParametricAutomaton parametricAutomaton;

    // Occurrence bitmasks of the reference String, one 64-bit-word array per distinct character:
    // bit i of occurrenceMaskByChar[c] is set iff automatonString.charAt(i) == c. Characteristic
    // vectors are derived from these in O(1) with zero allocation
    private final HashMap<Character, long[]> occurrenceMaskByChar;

    /**
     * Constructs a LevenshteinAutomaton.
     *
     * @param automatonString the reference String that all edit distance calculations are to be
     *     carried out in relation to
     * @param maxEditDistance the maximum amount of edit operations allowed by the automaton
     * @param memoized a boolean denoting whether transitions are to be resolved through the shared
     *     parametric transition memo (as opposed to being computed on the fly); automatically
     *     downgraded to simulation beyond {@link ParametricAutomaton#MAX_SUPPORTED_EDIT_DISTANCE}
     */
    private LevenshteinAutomaton(String automatonString, int maxEditDistance, boolean memoized) {
        Objects.requireNonNull(automatonString, "automatonString");
        if (maxEditDistance < 0)
            throw new IllegalArgumentException(
                    "maxEditDistance must be non-negative, got " + maxEditDistance);

        this.automatonString = automatonString;
        this.automatonStringLength = automatonString.length();
        this.maxEditDistance = maxEditDistance;

        boolean memo =
                (memoized && maxEditDistance <= ParametricAutomaton.MAX_SUPPORTED_EDIT_DISTANCE);
        this.parametricAutomaton = (memo ? ParametricAutomaton.forDistance(maxEditDistance) : null);
        this.occurrenceMaskByChar = (memo ? buildOccurrenceMasks(automatonString) : null);
    }

    /** Builds the per-character occurrence bitmasks of a reference String. */
    private static HashMap<Character, long[]> buildOccurrenceMasks(String automatonString) {
        int length = automatonString.length();
        HashMap<Character, long[]> occurrenceMaskByChar = new HashMap<>();

        for (int i = 0; i < length; i++) {
            char c = automatonString.charAt(i);
            long[] mask = occurrenceMaskByChar.get(c);
            if (mask == null) {
                mask = new long[(length + 63) >>> 6];
                occurrenceMaskByChar.put(c, mask);
            }

            mask[i >>> 6] |= (1L << (i & 63));
        }

        return occurrenceMaskByChar;
    }

    /**
     * Compiles a memo-based Levenshtein automaton for a reference String and maximum edit distance.
     * The returned instance can be reused for any number of membership queries and dictionary
     * searches.
     *
     * <p>Memo-based instances share the lazily materialized parametric transition table of {@link
     * ParametricAutomaton}, built once per max edit distance process-wide: construction is O(1),
     * transitions are computed on first use and stored in compact primitive structures, so memory
     * grows only with the (state, vector) pairs actually touched by queries. This scales to large
     * max edit distances; beyond {@link ParametricAutomaton#MAX_SUPPORTED_EDIT_DISTANCE} the
     * instance transparently falls back to table-free simulation.
     *
     * @param automatonString the reference String that all edit distance calculations are to be
     *     carried out in relation to
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed by the
     *     automaton; must be non-negative
     * @return a reusable LevenshteinAutomaton for {@code lev(automatonString, ·) <=
     *     maxEditDistance}
     */
    public static LevenshteinAutomaton compile(String automatonString, int maxEditDistance) {
        return new LevenshteinAutomaton(automatonString, maxEditDistance, true);
    }

    /**
     * Compiles a table-free Levenshtein automaton whose transitions are computed on the fly, with
     * no shared memo growth at all. Useful when even memoized state growth is undesired (e.g. huge
     * max edit distances over short-lived automata).
     *
     * @param automatonString the reference String that all edit distance calculations are to be
     *     carried out in relation to
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed by the
     *     automaton; must be non-negative
     * @return a reusable LevenshteinAutomaton for {@code lev(automatonString, ·) <=
     *     maxEditDistance}
     */
    public static LevenshteinAutomaton compileIterative(
            String automatonString, int maxEditDistance) {
        return new LevenshteinAutomaton(automatonString, maxEditDistance, false);
    }

    /**
     * Returns the reference String this automaton carries out edit distance calculations in
     * relation to.
     *
     * @return a String of this automaton's reference String
     */
    public String getAutomatonString() {
        return automatonString;
    }

    /**
     * Returns the maximum amount of edit operations allowed by this automaton.
     *
     * @return an int of this automaton's max edit distance
     */
    public int getMaxEditDistance() {
        return maxEditDistance;
    }

    /**
     * Returns a value indicating whether this automaton resolves transitions through the shared
     * parametric transition memo.
     *
     * @return true if this instance is memo-based; false if it computes transitions on the fly
     */
    public boolean isTableBased() {
        return (parametricAutomaton != null);
    }

    /**
     * Determines if a String is within this automaton's max edit distance of its reference String.
     *
     * @param candidate a String to be checked against this automaton
     * @return true if {@code candidate} is within {@code maxEditDistance} edit operations of this
     *     automaton's reference String; false otherwise
     */
    public boolean isWithinEditDistance(String candidate) {
        return accepts(candidate);
    }

    /**
     * Determines if a String is within this automaton's max edit distance of its reference String,
     * i.e. whether it is a member of this automaton's language.
     *
     * <p>Memo-based instances execute a table-lookup walk over interned parametric states;
     * iterative instances simulate the automaton's member positions.
     *
     * @param candidate a String to be checked against this automaton
     * @return true if {@code candidate} is within {@code maxEditDistance} edit operations of this
     *     automaton's reference String; false otherwise
     */
    public boolean accepts(String candidate) {
        return (parametricAutomaton != null
                ? acceptsViaMemo(candidate)
                : acceptsViaSimulation(candidate));
    }

    /**
     * Carries out a lookup walk of the lazily materialized parametric transition table, tracking
     * only the current interned state id and minimal boundary (no concrete States are
     * materialized).
     */
    private boolean acceptsViaMemo(String candidate) {
        int currentStateId = parametricAutomaton.getInitialStateId();
        int minimalBoundary = 0;

        for (int i = 0; i < candidate.length(); i++) {
            long packedVector = packedVector(minimalBoundary, candidate.charAt(i));
            long packedTransition = parametricAutomaton.transition(currentStateId, packedVector);

            if (ParametricAutomaton.isFailureTransition(packedTransition)) return false;

            currentStateId = ParametricAutomaton.targetStateId(packedTransition);
            minimalBoundary += ParametricAutomaton.transitionBoundaryOffset(packedTransition);
        }

        return isAcceptingParametricState(
                parametricAutomaton.getStateById(currentStateId), minimalBoundary);
    }

    /**
     * Simulates the automaton on the fly from the initial State, transitioning each encountered
     * State on its relevant subword's characteristic vector.
     */
    private boolean acceptsViaSimulation(String candidate) {
        State currentState = initialState;

        for (int i = 0; i < candidate.length(); i++) {
            currentState =
                    currentState.transition(maxEditDistance, automatonString, candidate.charAt(i));
            if (currentState == null) return false;
        }

        return isAcceptState(currentState, automatonStringLength, maxEditDistance);
    }

    /**
     * Procures (in O(1), allocation-free) the packed characteristic vector of the relevant subword
     * starting at a minimal boundary, with respect to a letter. Equivalent to {@code
     * State.getRelevantSubwordCharacteristicVector(maxEditDistance, automatonString, letter)} for a
     * State with the given minimal boundary, in the packed format of {@link
     * ParametricAutomaton#packVector}.
     *
     * @param minimalBoundary an int of the minimal boundary of the consuming State's member
     *     positions
     * @param letter the char that the characteristic vector is to be based off of
     * @return a long of the packed characteristic vector
     */
    long packedVector(int minimalBoundary, char letter) {
        // Relevant subword size: min(2n+1, |s| - minimalBoundary), clamped at 0
        int relevantSubwordSize =
                Math.min(2 * maxEditDistance + 1, automatonStringLength - minimalBoundary);
        if (relevantSubwordSize <= 0) return ParametricAutomaton.packVector(0L, 0);

        long[] occurrenceMask = occurrenceMaskByChar.get(letter);
        if (occurrenceMask == null)
            return ParametricAutomaton.packVector(0L, relevantSubwordSize); // letter not in s

        // Window the occurrence bits onto [minimalBoundary, minimalBoundary + relevantSubwordSize)
        int wordIndex = minimalBoundary >>> 6;
        int bitOffset = minimalBoundary & 63;

        long lowBits = occurrenceMask[wordIndex] >>> bitOffset;
        long highBits =
                (bitOffset != 0 && wordIndex + 1 < occurrenceMask.length)
                        ? occurrenceMask[wordIndex + 1] << (64 - bitOffset)
                        : 0L;

        long bits = (lowBits | highBits) & ((1L << relevantSubwordSize) - 1);
        return ParametricAutomaton.packVector(bits, relevantSubwordSize);
    }

    /**
     * Determines (in O(1)) whether a parametric state at a given minimal boundary is accepting.
     *
     * <p>See {@link ParametricState#getAcceptanceThresholdOffset()} for the derivation.
     *
     * @param s a ParametricState
     * @param minimalBoundary an int of the minimal boundary of the concrete State of form {@code s}
     * @return true if a concrete State of form {@code s} with minimal boundary {@code
     *     minimalBoundary} is accepting in this automaton; false otherwise
     */
    boolean isAcceptingParametricState(ParametricState s, int minimalBoundary) {
        return (minimalBoundary
                >= automatonStringLength - maxEditDistance + s.getAcceptanceThresholdOffset());
    }

    /**
     * Determines if two Strings are within a specified edit distance of one another.
     *
     * <p>This static convenience entry point compiles a memo-based automaton per call; it is kept
     * for backward compatibility. To actually benefit from the automaton's reusability (and avoid
     * rebuilding per query), compile once and reuse the instance:
     *
     * <pre>{@code
     * LevenshteinAutomaton automaton = LevenshteinAutomaton.compile(str1, maxEditDistance);
     * automaton.isWithinEditDistance(str2);
     * }</pre>
     *
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed to be
     *     used to (hypothetically) turn one String in to another
     * @param str1 a String
     * @param str2 a String
     * @return true if {@code str1} and {@code str2} are within {@code maxEditDistance} edit
     *     operations of each other; false otherwise
     * @deprecated use {@link #compile(String, int)} and reuse the resulting instance's {@link
     *     #isWithinEditDistance(String)}
     */
    @Deprecated
    public static boolean isWithinEditDistance(int maxEditDistance, String str1, String str2) {
        return compile(str1, maxEditDistance).accepts(str2);
    }

    /**
     * Determines if a Position is accepting.
     *
     * @param p a Position
     * @param automatonStringLength the String that the parent automaton is carrying all edit
     *     distance calculations in relation to
     * @param automatonMaxEditDistance an int denoting the max edit distance of {@code p}'s parent
     *     automaton
     * @return true if {@code p} is an accepting position; false otherwise
     */
    private static boolean isAcceptingPosition(
            Position p, int automatonStringLength, int automatonMaxEditDistance) {
        return (automatonStringLength - p.getI() <= automatonMaxEditDistance - p.getE());
    }

    /**
     * Determines if a State is accepting.
     *
     * @param s a State
     * @param automatonStringLength the String that the parent automaton is carrying all edit
     *     distance calculations in relation to
     * @param automatonMaxEditDistance an int denoting the max edit distance of {@code s}'s parent
     *     automaton
     * @return true if {@code s} is an accepting State; false otherwise
     */
    public static boolean isAcceptState(
            State s, int automatonStringLength, int automatonMaxEditDistance) {
        for (Position p : s.getMemberPositions()) {
            if (isAcceptingPosition(p, automatonStringLength, automatonMaxEditDistance))
                return true;
        }

        return false;
    }
}
