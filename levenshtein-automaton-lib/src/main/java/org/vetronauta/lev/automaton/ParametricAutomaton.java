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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A string-independent (parametric) Levenshtein automaton for a fixed maximum edit distance.
 *
 * <p>Per the parametric construction of Levenshtein automata, the transition structure of the
 * automaton recognizing {@code lev(s, ·) <= n} does not depend on the reference string {@code s}
 * itself, but only on the characteristic vectors (of length at most {@code 2n+1}) of the consumed
 * character relative to the current window of {@code s}. Consequently a single ParametricAutomaton
 * per edit distance {@code n} can be built once and shared by every {@link LevenshteinAutomaton}
 * compiled for that distance.
 *
 * <p>Instances are immutable and thread-safe. One instance per distance is kept in a process-wide
 * cache managed by {@link #forDistance(int)}; {@link #clearCache()} drops it.
 *
 * <p>Note: the transition table grows roughly with the number of parametric states times {@code
 * 2^(2n+1)} characteristic vectors, which is why table-based automata are recommended for max edit
 * distances of at most 2. Use {@link LevenshteinAutomaton#compileIterative(String, int)} to avoid
 * the table entirely for larger distances.
 *
 * @author Kevin
 */
public final class ParametricAutomaton {
    // Process-wide cache of the parametric automaton built for each encountered max edit distance.
    // Populated atomically via computeIfAbsent, replacing the former unsynchronized static HashMap.
    private static final ConcurrentHashMap<Integer, ParametricAutomaton> AUTOMATON_CACHE =
            new ConcurrentHashMap<>();

    private final int maxEditDistance;

    // Map containing the transition relationships between the parametrized versions of States in
    // the automaton defined by maxEditDistance. A null target denotes the failure transition.
    private final Map<ParametricState, HashMap<AugBitSet, ParametricState>> transitionTable;

    // The parametric form of the initial State ({(0, 0)}) shared by every automaton simulation
    private final ParametricState initialState;

    /**
     * Procures the ParametricAutomaton for a given max edit distance, building it on first use and
     * caching it for reuse by every subsequent caller (thread-safe).
     *
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed by the
     *     automaton; must be non-negative
     * @return the ParametricAutomaton for {@code maxEditDistance}
     */
    public static ParametricAutomaton forDistance(int maxEditDistance) {
        if (maxEditDistance < 0)
            throw new IllegalArgumentException(
                    "maxEditDistance must be non-negative, got " + maxEditDistance);

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
        this.transitionTable = createParametricStateTransitionTable(maxEditDistance);
        this.initialState =
                new ParametricState(new State(new Position[] {new Position(0, 0, false)}));
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
     * Returns the parametric form of the initial State of any Levenshtein automaton.
     *
     * @return a ParametricState representing the State {(0, 0)}
     */
    public ParametricState getInitialState() {
        return initialState;
    }

    /**
     * Returns the number of distinct parametric states in this automaton's transition table.
     *
     * @return an int of the number of parametric states
     */
    public int getStateCount() {
        return transitionTable.size();
    }

    /**
     * Returns an unmodifiable view of the parametric states in this automaton's transition table.
     *
     * @return a Set of the ParametricStates of this automaton
     */
    public Set<ParametricState> getStates() {
        return Collections.unmodifiableSet(transitionTable.keySet());
    }

    /**
     * Executes a transition of a parametric state on a characteristic vector.
     *
     * @param state a ParametricState
     * @param characteristicVector an AugBitSet representation of the characteristic vector of the
     *     consuming state's relevant subword with respect to the processing character
     * @return the ParametricState the transition yields, or null if the transition is a failure
     */
    public ParametricState transition(ParametricState state, AugBitSet characteristicVector) {
        Map<AugBitSet, ParametricState> stateTransitionMap = transitionTable.get(state);
        return (stateTransitionMap == null ? null : stateTransitionMap.get(characteristicVector));
    }

    /**
     * Creates a Map containing the transition relationships between the parameterized versions of
     * States in an automaton defined by a given max edit distance.
     *
     * <p>Moved as-is from {@code LevenshteinAutomaton.createParametricStateTransitionMap} (which
     * formerly also cached the result in a static map; caching now lives in {@link #forDistance}).
     *
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed by
     *     specific automaton
     * @return a HashMap containing the transition relationships between the parametrized versions
     *     of States in the automaton described by maxEditDistance
     */
    private static Map<ParametricState, HashMap<AugBitSet, ParametricState>>
            createParametricStateTransitionTable(int maxEditDistance) {
        // HashMap which will store the transition relationships between the parametrized
        // versions of States in the automaton described by maxEditDistance
        HashMap<ParametricState, HashMap<AugBitSet, ParametricState>> transitionHashMap =
                new HashMap<>();

        int maxRelevantSubwordSize = 2 * maxEditDistance + 1;

        // Procure the set of ParametricStates collectively describing the forms of all the
        // States in the automaton defined by maxRelevantSubwordSize and maxEditDistance
        HashSet<ParametricState> parametricStateHashSet =
                procureParametricStates(maxRelevantSubwordSize, maxEditDistance);

        // Produce all the possible characteristic vectors of sizes 0 to maxEditDistance
        ArrayList<AugBitSet> characteristicVectorPermutationContainerArrayList =
                produceBinaryPermutations(maxRelevantSubwordSize);

        // Loop through possible characteristic vectors and ParametricStates, using them to
        // create transition relationships that will be represented and stored in
        // transitionHashMap
        for (AugBitSet characteristicVector : characteristicVectorPermutationContainerArrayList) {
            for (ParametricState currentParametricState : parametricStateHashSet) {
                // If States of the form described by currentParametricState have Position
                // sets that can fit in a relevant subword of the form descirbed by
                // characteristicVector
                if (currentParametricState.getLargestPositionOffset()
                        <= characteristicVector.getRelevantBitSetSize()) {
                    // Use currentParametricState to create a dummy state with a minimum
                    // boundary of zero
                    State currentConcreteState = currentParametricState.createActualState(0);

                    // Execute a transiton on currentConcreteState given maxEditDistance and
                    // characteristicVector
                    State transitionState =
                            currentConcreteState.transition(maxEditDistance, characteristicVector);

                    ParametricState transitionParametricState = null;

                    // If the transition yields a non-failure State, get the result State's
                    // ParametricState
                    if (transitionState != null) {
                        int minimumBoundariesDifference =
                                State.getMinimumBoundariesDifference(
                                        currentConcreteState, transitionState);
                        transitionParametricState =
                                new ParametricState(transitionState, minimumBoundariesDifference);
                    }

                    // Store the transition relationship between currentParametricState,
                    // characteristicVector, and transitionParametricState in transitionHashMap
                    if (!transitionHashMap.containsKey(currentParametricState))
                        transitionHashMap.put(currentParametricState, new HashMap<>());

                    transitionHashMap
                            .get(currentParametricState)
                            .put(characteristicVector, transitionParametricState);
                }
            }
        }

        return transitionHashMap;
    }

    /**
     * Procures the collection of ParametricStates collectively representing the forms of every
     * State in a Levenshtein automaton.
     *
     * @param maxRelevantSubwordSize an int denoting the max relevant subword size of a State in a
     *     Levenshtein automaton (based on {@code maxEditDistance}
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed by
     *     specific automaton
     * @return a HashSet of Parametric states collectively representing the forms of every State in
     *     the automaton defined by maxRelevantSubwordSize and maxEditDistance
     */
    private static HashSet<ParametricState> procureParametricStates(
            int maxRelevantSubwordSize, int maxEditDistance) {
        // HashSet which will contain ParametricState objects representing the forms of every
        // state in the automaton defined by maxRelevantSubwordSize and maxEditDistance
        HashSet<ParametricState> parametricStateHashSet = new HashSet<>();

        // Loop through the possible relevant subword sizes, collectively using them
        // to create the set of all possible States, and in turn, ParametricStates
        // in the automaton defined by maxRelevantSubwordSize and maxEditDistance
        for (int i = 0; i < maxRelevantSubwordSize; i++) {
            // Create a base state from the current relevant subword size
            Position basePosition = new Position(i, 0, false);

            // Get the States based by basePosition
            LinkedList<State> basedStateLinkedList =
                    procureBasedStates(basePosition, maxRelevantSubwordSize, maxEditDistance);

            // Add the parametric versions of each state in basedStateLinkedList to
            // parametricStateHashSet
            for (State currentNewState : basedStateLinkedList)
                parametricStateHashSet.add(new ParametricState(currentNewState));
        }

        return parametricStateHashSet;
    }

    /**
     * Procures the collection of states that are based by a position, given a maximum relevant
     * subword size of an automaton state and the max edit distance of an automaton.
     *
     * @param basePosition the Position that State procurement will be based on
     * @param maxRelevantSubwordSize an int denoting the max relevant subword size of a State in a
     *     Levenshtein automaton (based on {@code maxEditDistance}
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed by a
     *     specific automaton
     * @return a LinkedList of States that are based by basePosition
     */
    private static LinkedList<State> procureBasedStates(
            Position basePosition, int maxRelevantSubwordSize, int maxEditDistance) {
        // LinkedList which will hold all the States that are based by basePosition
        LinkedList<State> basedStateLinkedList = new LinkedList<>();
        basedStateLinkedList.add(new State(new Position[] {basePosition}));

        // Loop through the set of all possible edit distances and boundaries to create
        // Positions which will be used to creates States that are based by basePosition
        for (int e = 1; e <= maxEditDistance; e++) {
            for (int i = 0; i < maxRelevantSubwordSize; i++) {
                // Create a standard position from the current processing edit distance and
                // boundary. If it is
                // subsumed by basePosition, use it to insert States in to basedStateLinkedList that
                // can be formed
                // solely by it and the set of Positions appearing as members in the elements of
                // basedStateLinkedList
                Position currentPosition = new Position(i, e, false);
                if (basePosition.subsumes(currentPosition, maxEditDistance))
                    procureNewBasedStates(basedStateLinkedList, currentPosition, maxEditDistance);

                // If the t-position with the current processing edit distance and boundary is
                // subsumed by basePosition,
                // use it to insert States in to basedStateLinkedList in a manner identical to that
                // used above
                if (i <= (maxRelevantSubwordSize - 2)) {
                    // NOTE: the subsumption check below intentionally tests the standard twin
                    // (currentPosition) rather than the t-position itself, replicating the original
                    // LevenshteinAutomaton behavior exactly; see PLAN.md section 6.
                    Position currentTPosition = new Position(i, e, true);
                    if (basePosition.subsumes(currentPosition, maxEditDistance))
                        procureNewBasedStates(
                                basedStateLinkedList, currentTPosition, maxEditDistance);
                }
            }
        }

        return basedStateLinkedList;
    }

    /**
     * Procures with a collection of commonly based States and a given position, those States which
     * can be formed using the given position and the set of member positions appearing in the
     * aforementioned State collection.
     *
     * @param basedStateLinkedList a LinkedList of States collectively based by a given Position
     * @param p a Position subsumed by the same Position which bases each of the elements in {@code
     *     basedStateLinkedList}
     */
    private static void procureNewBasedStates(
            LinkedList<State> basedStateLinkedList, Position p, int maxEditDistance) {
        // LinkedList which will hold all the States based by basePosition that contain
        // currentPosition
        LinkedList<State> newBasedStateLinkedList = new LinkedList<>();

        // Add a State with currentPosition as its sole member to newBasedLinkedList
        newBasedStateLinkedList.add(new State(new Position[] {p}));

        // Loop through the States in basedStateLinkedList, and for each, determine if its
        // members Positions can form a State with currentPosition. If so, create a new State
        // with these set of Positions as members and add it to newBasedLinkedList
        for (State currentState : basedStateLinkedList) {
            if (State.canBeState(currentState, p, maxEditDistance))
                newBasedStateLinkedList.add(new State(currentState, p));
        }

        // Add all the newly created states to basedStateLinkedList
        basedStateLinkedList.addAll(newBasedStateLinkedList);
    }

    /**
     * Produces all the binary permutations with bit counts less than or equal to a given value.
     *
     * @param maxBitCount an int denoting the maximum bit count of a permutation to be produced
     * @return an ArrayList of AugBitSets that collectively represent the set of binary permutations
     *     of bit counts from 0 to {@code maxBitCount}
     */
    private static ArrayList<AugBitSet> produceBinaryPermutations(int maxBitCount) {
        // Determine the total number of permutations to be produced
        int totalPermutationCount = 0;
        for (int i = 1; i < maxBitCount + 1; i++) totalPermutationCount += Math.pow(2, i);

        // ArrayList of AugBitSets which will each represent a binary permutation. "+1" accomodates
        // the empty permutation
        ArrayList<AugBitSet> binaryPermutationContainerArrayList =
                new ArrayList<>(totalPermutationCount + 1);

        // Add the AugBitSet representstions of the permutations {0} and {1} to
        // binaryPermutationContainerArrayList
        for (int i = 0; i <= 1; i++) {
            binaryPermutationContainerArrayList.add(new AugBitSet(1));
            binaryPermutationContainerArrayList
                    .get(binaryPermutationContainerArrayList.size() - 1)
                    .set(0, (i != 0));
        }

        // Loop through the AugBitSets in binaryPermutationContainerArrayList, using each to create
        // new permutations (by separately adding a and 1 to the end of each) that are added to
        // the end of
        // the ArrayList. This is done until one is encountered that has a size equal to
        // maxRelevantSubwordSize
        for (int i = 0;
                binaryPermutationContainerArrayList.get(i).getRelevantBitSetSize() < maxBitCount;
                i++) {
            // Loop through the set [0,1], using each value to create a new binary permutation
            // by appending the value to the end of a copy of the permutation located at i
            for (int j = 0; j <= 1; j++) {
                AugBitSet currentPermutationBitSet = binaryPermutationContainerArrayList.get(i);
                AugBitSet newPermutationBitSet =
                        new AugBitSet(currentPermutationBitSet.getRelevantBitSetSize() + 1);

                newPermutationBitSet.xor(currentPermutationBitSet); // copies the relevant bits in
                // currentPermutationBitSet
                newPermutationBitSet.set(
                        newPermutationBitSet.getRelevantBitSetSize() - 1, (j != 0));

                binaryPermutationContainerArrayList.add(newPermutationBitSet);
            }
        }

        binaryPermutationContainerArrayList.add(
                new AugBitSet(0)); // Add the empty permutation as well
        return binaryPermutationContainerArrayList;
    }
}
