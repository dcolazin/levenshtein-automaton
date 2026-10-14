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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A string-independent (parametric) Levenshtein automaton for a fixed maximum edit distance.
 *
 * <p>Per the parametric construction of Levenshtein automata, the transition function of the
 * automaton recognizing {@code lev(s, ·) <= n} does not depend on the reference string {@code s}
 * itself, but only on the characteristic vectors (of length at most {@code 2n+1}) of the consumed
 * character relative to the current window of {@code s}. Consequently a single ParametricAutomaton
 * per edit distance {@code n} can serve every {@link LevenshteinAutomaton} compiled for that
 * distance.
 *
 * <p><b>Scalable, lazily-materialized transition table.</b> Instead of eagerly materializing the
 * full cross product of parametric states and characteristic vectors (whose memory grows roughly
 * with {@code states(n) × 2^(2n+1)} and makes distances greater than 2 practically unbuildable),
 * transitions are computed on demand from the underlying {@link State}/{@link Position} machinery
 * and memoized compactly:
 *
 * <ul>
 *   <li>characteristic vectors are packed into primitive {@code long}s ({@code bits << 6 | size}),
 *       not {@link AugBitSet} objects;
 *   <li>parametric states are interned to {@code int} ids (a form registry);
 *   <li>each state's memoized transitions live in immutable, copy-on-write sorted arrays (16 bytes
 *       per entry, allocation-free and lock-free reads);
 *   <li>failure transitions are memoized too (sentinel value), so repeated dead-ends stay cheap.
 * </ul>
 *
 * <p>Construction is O(1); memory grows proportionally to the (state, vector) pairs actually used
 * by queries. Note that the eager state enumeration of the former implementation — including its
 * t-position subsumption quirk — is gone entirely: states are discovered, never enumerated.
 *
 * <p>Instances are thread-safe. One instance per distance is kept in a process-wide cache managed
 * by {@link #forDistance(int)}; {@link #clearCache()} drops it. Vector packing supports max edit
 * distances up to {@link #MAX_SUPPORTED_EDIT_DISTANCE}; {@link LevenshteinAutomaton#compile(String,
 * int)} transparently falls back to table-free simulation beyond that (see {@link
 * LevenshteinAutomaton#compileIterative(String, int)}).
 *
 * @author Kevin
 */
public final class ParametricAutomaton {
    /** The maximum max edit distance supported by the packed-vector transition memo. */
    public static final int MAX_SUPPORTED_EDIT_DISTANCE = 28;

    // Process-wide cache of the parametric automaton per encountered max edit distance
    private static final ConcurrentHashMap<Integer, ParametricAutomaton> AUTOMATON_CACHE =
            new ConcurrentHashMap<>();

    // Sentinel packed transition value denoting a memoized failure transition (all valid packed
    // values are non-negative)
    private static final long FAILURE_TRANSITION = Long.MIN_VALUE;

    // Packing layout of characteristic vectors: bits in [6, 6+2n+1), size (<= 2n+1 <= 57) in [0,6)
    private static final int VECTOR_SIZE_BITS = 6;
    private static final long VECTOR_SIZE_MASK = (1L << VECTOR_SIZE_BITS) - 1;

    // Packing layout of transition values: target state id in [7, ..), boundary offset in [0,7)
    private static final int OFFSET_BITS = 7;
    private static final long OFFSET_MASK = (1L << OFFSET_BITS) - 1;

    private final int maxEditDistance;

    // Registry of interned parametric state forms: form <-> dense id. statesById/stateCount are
    // guarded by this (writes happen only on discovery of a new form)
    private final ConcurrentHashMap<ParametricState, Integer> stateIdByForm =
            new ConcurrentHashMap<>();
    private final ArrayList<ParametricState> statesById = new ArrayList<>();
    private int stateCount = 0;

    // Lazily-populated transition memo per discovered state id. Snapshots are immutable, so reads
    // are lock-free; writes (rare, discovery-time only) are synchronized and copy-on-write
    private final ConcurrentHashMap<Integer, TransitionTable> transitionTableByStateId =
            new ConcurrentHashMap<>();

    // The parametric form of the initial State ({(0, 0)}) shared by every automaton simulation;
    // interned as state id 0 at construction
    private final ParametricState initialState;

    /**
     * Procures the ParametricAutomaton for a given max edit distance, caching it process-wide for
     * reuse by every subsequent caller (thread-safe). Construction is O(1): the transition table
     * materializes lazily as queries touch it.
     *
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed by the
     *     automaton; must be in [0, {@link #MAX_SUPPORTED_EDIT_DISTANCE}]
     * @return the ParametricAutomaton for {@code maxEditDistance}
     */
    public static ParametricAutomaton forDistance(int maxEditDistance) {
        if (maxEditDistance < 0 || maxEditDistance > MAX_SUPPORTED_EDIT_DISTANCE)
            throw new IllegalArgumentException(
                    "maxEditDistance must be in [0, "
                            + MAX_SUPPORTED_EDIT_DISTANCE
                            + "], got "
                            + maxEditDistance);

        return AUTOMATON_CACHE.computeIfAbsent(maxEditDistance, ParametricAutomaton::new);
    }

    /** Drops every cached ParametricAutomaton, releasing the memory held by their tables. */
    public static void clearCache() {
        AUTOMATON_CACHE.clear();
    }

    /**
     * Returns the number of parametric automata currently held in the cache.
     *
     * @return an int of the number of cached automata
     */
    public static int getCachedDistanceCount() {
        return AUTOMATON_CACHE.size();
    }

    private ParametricAutomaton(int maxEditDistance) {
        this.maxEditDistance = maxEditDistance;
        this.initialState =
                new ParametricState(new State(new Position[] {new Position(0, 0, false)}));
        synchronized (this) {
            intern(initialState); // the initial state always has id 0
        }
    }

    /**
     * Returns the max edit distance this automaton was built for.
     *
     * @return an int of this automaton's max edit distance
     */
    public int getMaxEditDistance() {
        return maxEditDistance;
    }

    /**
     * Returns the id of the initial state ({@code 0}, kept as a method for readability).
     *
     * @return an int of the initial state's id
     */
    public int getInitialStateId() {
        return 0;
    }

    /**
     * Returns the interned parametric state form registered under a state id.
     *
     * @param stateId an int of a state id previously handed out by this automaton
     * @return the ParametricState form of {@code stateId}
     */
    public synchronized ParametricState getStateById(int stateId) {
        return statesById.get(stateId);
    }

    /**
     * Returns the parametric state forms discovered by this automaton so far.
     *
     * <p>Since the transition table materializes lazily, the returned set grows as queries touch
     * new states (as opposed to the former eager enumeration of every possible form).
     *
     * @return an unmodifiable Set of the discovered ParametricStates
     */
    public synchronized Set<ParametricState> getStates() {
        return Collections.unmodifiableSet(new HashSet<>(statesById));
    }

    /**
     * Returns the number of parametric state forms discovered so far.
     *
     * @return an int of the number of discovered parametric states
     */
    public synchronized int getStateCount() {
        return stateCount;
    }

    /**
     * Executes (memoizing) a transition of a parametric state on a packed characteristic vector.
     *
     * <p>On first encounter of a (state, vector) pair the transition is computed from the
     * underlying {@link State}/{@link Position} machinery — exactly as the former eager table build
     * did — and memoized; subsequent lookups are a lock-free binary search.
     *
     * @param stateId an int of the source state's id
     * @param packedVector a long of the packed characteristic vector of the consuming state's
     *     relevant subword with respect to the processing character (see {@link #packVector})
     * @return a long of the packed transition ({@link #targetStateId}/{@link
     *     #transitionBoundaryOffset}), or the failure sentinel (see {@link #isFailureTransition})
     */
    public long transition(int stateId, long packedVector) {
        TransitionTable table = transitionTableByStateId.get(stateId);
        if (table != null) {
            int index = Arrays.binarySearch(table.packedVectors, packedVector);
            if (index >= 0) return table.packedTransitions[index];
        }

        synchronized (this) {
            table = transitionTableByStateId.get(stateId);
            int index =
                    (table == null ? -1 : Arrays.binarySearch(table.packedVectors, packedVector));
            if (index < 0) table = computeTransition(stateId, packedVector, table);
            index = Arrays.binarySearch(table.packedVectors, packedVector);
            return table.packedTransitions[index];
        }
    }

    /**
     * Computes a transition from the position machinery and stores it copy-on-write. Called only
     * under this automaton's lock, on a memo miss.
     */
    private TransitionTable computeTransition(
            int stateId, long packedVector, TransitionTable current) {
        State sourceState = getStateById(stateId).createActualState(0);
        State targetState = sourceState.transition(maxEditDistance, toAugBitSet(packedVector));

        long packedTransition;
        if (targetState == null) {
            packedTransition = FAILURE_TRANSITION;
        } else {
            int boundaryOffset = State.getMinimumBoundariesDifference(sourceState, targetState);
            if (boundaryOffset > OFFSET_MASK)
                throw new IllegalStateException(
                        "transition boundary offset "
                                + boundaryOffset
                                + " exceeds packing limit; distance too large");

            int targetStateId = intern(new ParametricState(targetState));
            packedTransition = ((long) targetStateId << OFFSET_BITS) | boundaryOffset;
        }

        TransitionTable updated = insert(current, packedVector, packedTransition);
        transitionTableByStateId.put(stateId, updated);
        return updated;
    }

    /** Registers a parametric state form (or returns its existing id). Callers hold the lock. */
    private int intern(ParametricState form) {
        Integer existingId = stateIdByForm.get(form);
        if (existingId != null) return existingId;

        int newId = stateCount++;
        statesById.add(form);
        stateIdByForm.put(form, newId);
        return newId;
    }

    /** Returns whether a packed transition value denotes a failure transition. */
    public static boolean isFailureTransition(long packedTransition) {
        return (packedTransition == FAILURE_TRANSITION);
    }

    /**
     * Unpacks the target state id of a (non-failure) packed transition value.
     *
     * @param packedTransition a long produced by {@link #transition(int, long)}
     * @return an int of the target state's id
     */
    public static int targetStateId(long packedTransition) {
        return (int) (packedTransition >>> OFFSET_BITS);
    }

    /**
     * Unpacks the minimal-boundary offset of a (non-failure) packed transition value: the minimal
     * boundary of the target concrete state is that of the source plus this offset.
     *
     * @param packedTransition a long produced by {@link #transition(int, long)}
     * @return an int of the transition's minimal-boundary offset
     */
    public static int transitionBoundaryOffset(long packedTransition) {
        return (int) (packedTransition & OFFSET_MASK);
    }

    /**
     * Packs a characteristic vector into the primitive long key format used by the transition memo:
     * {@code bits << 6 | size}, with {@code size <= 57}.
     *
     * @param bits a long whose bit i denotes whether position i of the relevant subword holds the
     *     processing character
     * @param size an int of the relevant subword size (number of significant bits)
     * @return a long of the packed vector
     */
    public static long packVector(long bits, int size) {
        if (size < 0 || size > VECTOR_SIZE_MASK)
            throw new IllegalArgumentException("vector size out of packable range: " + size);

        return (bits << VECTOR_SIZE_BITS) | size;
    }

    /**
     * Unpacks a packed vector's size (its significant bit count).
     *
     * @param packedVector a long in the format produced by {@link #packVector}
     * @return an int of the vector's size
     */
    public static int vectorSize(long packedVector) {
        return (int) (packedVector & VECTOR_SIZE_MASK);
    }

    /**
     * Unpacks a packed vector's bits.
     *
     * @param packedVector a long in the format produced by {@link #packVector}
     * @return a long whose bit i denotes whether position i of the relevant subword holds the
     *     processing character
     */
    public static long vectorBits(long packedVector) {
        return (packedVector >>> VECTOR_SIZE_BITS);
    }

    /** Materializes an AugBitSet from a packed vector (used only on memo-miss computations). */
    private static AugBitSet toAugBitSet(long packedVector) {
        int size = vectorSize(packedVector);
        long bits = vectorBits(packedVector);

        AugBitSet characteristicVector = new AugBitSet(size);
        for (int i = 0; i < size; i++) {
            if ((bits & (1L << i)) != 0) characteristicVector.set(i);
        }

        return characteristicVector;
    }

    /** Copy-on-write insertion into a snapshot's sorted arrays. */
    private static TransitionTable insert(TransitionTable table, long key, long value) {
        long[] oldKeys = (table == null ? EMPTY_KEYS : table.packedVectors);
        long[] oldTransitions = (table == null ? EMPTY_KEYS : table.packedTransitions);
        int length = oldKeys.length;

        int insertionIndex = -(Arrays.binarySearch(oldKeys, key)) - 1;

        long[] newKeys = new long[length + 1];
        long[] newTransitions = new long[length + 1];

        System.arraycopy(oldKeys, 0, newKeys, 0, insertionIndex);
        System.arraycopy(oldTransitions, 0, newTransitions, 0, insertionIndex);
        newKeys[insertionIndex] = key;
        newTransitions[insertionIndex] = value;
        System.arraycopy(
                oldKeys, insertionIndex, newKeys, insertionIndex + 1, length - insertionIndex);
        System.arraycopy(
                oldTransitions,
                insertionIndex,
                newTransitions,
                insertionIndex + 1,
                length - insertionIndex);

        return new TransitionTable(newKeys, newTransitions);
    }

    private static final long[] EMPTY_KEYS = new long[0];

    /**
     * An immutable snapshot of a state's memoized transitions: packed vectors (sorted) mapped
     * positionally to packed transition values.
     */
    private static final class TransitionTable {
        final long[] packedVectors;
        final long[] packedTransitions;

        TransitionTable(long[] packedVectors, long[] packedTransitions) {
            this.packedVectors = packedVectors;
            this.packedTransitions = packedTransitions;
        }
    }
}
