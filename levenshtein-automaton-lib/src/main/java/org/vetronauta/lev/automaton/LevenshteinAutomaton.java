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

import java.util.LinkedList;
import java.util.Objects;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.vetronauta.lev.mdag.MDAG;
import org.vetronauta.lev.mdag.MDAGNode;
import org.vetronauta.lev.mdag.SimpleMDAGNode;

/**
 * A compiled, reusable Levenshtein automaton recognizing every String within a maximum edit
 * distance of a fixed reference String.
 *
 * <p>An instance represents the automaton D(s, n) for {@code lev(s, ·) <= n} and is the unit of
 * reuse: compile it once and answer any number of membership queries cheaply. Successive calls such
 * as {@code lev(s, s1) < n} and {@code lev(s, s2) < n} share the compiled instance (and the
 * process-wide parametric transition table per distance) instead of rebuilding the automaton per
 * query.
 *
 * <pre>{@code
 * LevenshteinAutomaton automaton = LevenshteinAutomaton.compile("kitten", 2);
 * automaton.accepts("sitting");        // false, via memoized table lookups
 * automaton.accepts("mitten");         // true, same instance
 * automaton.fuzzySearch(dictionary);   // dictionary search reusing the same instance
 * }</pre>
 *
 * <p>Two compilation modes are available:
 *
 * <ul>
 *   <li>{@link #compile(String, int)} — table-based. Transitions resolve through the parametric
 *       transition table of {@link ParametricAutomaton} (built once per distance, cached
 *       process-wide). Recommended for max edit distances of at most 2; larger distances require
 *       huge amounts of memory.
 *   <li>{@link #compileIterative(String, int)} — table-free. Transitions are computed on the fly
 *       from member positions (with characteristic vectors still memoized per instance). Advised
 *       for larger max edit distances.
 * </ul>
 *
 * <p>Instances are immutable after compilation and safe to share across threads (memo maps are
 * populated lazily via benign races; contents are deterministic functions of the instance).
 *
 * @author Kevin
 */
public final class LevenshteinAutomaton {
    // The State that serves as the initial state of every (non-table-based) automaton simulation
    private static final State initialState = new State(new Position[] {new Position(0, 0, false)});

    // The reference String that all edit distance calculations carried out by this automaton are
    // relative to
    private final String automatonString;

    // The length of automatonString (kept as a field to bypass repeated method calls)
    private final int automatonStringLength;

    // The maximum amount of edit operations allowed by this automaton
    private final int maxEditDistance;

    // The string-independent parametric automaton backing table-based operations; null for
    // instances compiled with compileIterative
    private final ParametricAutomaton parametricAutomaton;

    // Lazily-populated memo of characteristic vectors, indexed by the minimal boundary of the
    // consuming State. The vector for a step depends solely on (minimal boundary, letter) given
    // this automaton's reference String and max edit distance. Buckets are created on first use;
    // racing threads may briefly create duplicate (equivalent) buckets, which is benign.
    private final ConcurrentHashMap<Character, AugBitSet>[] characteristicVectorMemoByBoundary;

    /**
     * Constructs a LevenshteinAutomaton.
     *
     * @param automatonString the reference String that all edit distance calculations are to be
     *     carried out in relation to
     * @param maxEditDistance the maximum amount of edit operations allowed by the automaton
     * @param tableBased a boolean denoting whether transitions are to be resolved through the
     *     parametric transition table (as opposed to being computed on the fly)
     */
    private LevenshteinAutomaton(String automatonString, int maxEditDistance, boolean tableBased) {
        Objects.requireNonNull(automatonString, "automatonString");
        if (maxEditDistance < 0)
            throw new IllegalArgumentException(
                    "maxEditDistance must be non-negative, got " + maxEditDistance);

        this.automatonString = automatonString;
        this.automatonStringLength = automatonString.length();
        this.maxEditDistance = maxEditDistance;
        this.parametricAutomaton =
                (tableBased ? ParametricAutomaton.forDistance(maxEditDistance) : null);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Character, AugBitSet>[] memo =
                new ConcurrentHashMap[automatonStringLength + 1];
        this.characteristicVectorMemoByBoundary = memo;
    }

    /**
     * Compiles a table-based Levenshtein automaton for a reference String and maximum edit
     * distance. The returned instance can be reused for any number of membership queries and
     * dictionary searches.
     *
     * <p>Table-based instances share the parametric transition table built once per max edit
     * distance (process-wide). Since that table grows roughly with {@code 2^(2n+1)} characteristic
     * vectors, this mode is recommended for max edit distances of at most 2; use {@link
     * #compileIterative(String, int)} for larger distances.
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
     * Compiles a table-free Levenshtein automaton whose transitions are computed on the fly (with
     * characteristic vectors still memoized per instance). Advised for max edit distances greater
     * than 2, where the parametric transition table would require huge amounts of memory.
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
     * Returns a value indicating whether this automaton resolves transitions through the parametric
     * transition table.
     *
     * @return true if this instance is table-based; false if it computes transitions on the fly
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
     * <p>Table-based instances execute a pure table-lookup walk over the parametric states;
     * iterative instances simulate the automaton's member positions. In both modes the
     * characteristic vectors encountered are memoized per instance.
     *
     * @param candidate a String to be checked against this automaton
     * @return true if {@code candidate} is within {@code maxEditDistance} edit operations of this
     *     automaton's reference String; false otherwise
     */
    public boolean accepts(String candidate) {
        return (parametricAutomaton != null
                ? acceptsViaTable(candidate)
                : acceptsViaSimulation(candidate));
    }

    /**
     * Carries out a table-lookup walk of the parametric automaton, tracking only the current
     * parametric state and minimal boundary (no concrete States are materialized).
     */
    private boolean acceptsViaTable(String candidate) {
        ParametricState currentParametricState = parametricAutomaton.getInitialState();
        int minimalBoundary = 0;

        for (int i = 0; i < candidate.length(); i++) {
            AugBitSet characteristicVector =
                    characteristicVector(minimalBoundary, candidate.charAt(i));
            ParametricState transitionParametricState =
                    parametricAutomaton.transition(currentParametricState, characteristicVector);

            if (transitionParametricState == null) return false; // failure transition

            minimalBoundary += transitionParametricState.getTransitionBoundaryOffset();
            currentParametricState = transitionParametricState;
        }

        return isAcceptingParametricState(currentParametricState, minimalBoundary);
    }

    /**
     * Simulates the automaton on the fly from the initial State, transitioning each encountered
     * State on the memoized characteristic vector of its relevant subword.
     */
    private boolean acceptsViaSimulation(String candidate) {
        State currentState = initialState;

        for (int i = 0; i < candidate.length(); i++) {
            currentState =
                    currentState.transition(
                            maxEditDistance,
                            characteristicVector(
                                    currentState.getMinimalBoundary(), candidate.charAt(i)));
            if (currentState == null) return false;
        }

        return isAcceptState(currentState, automatonStringLength, maxEditDistance);
    }

    /**
     * Procures (memoized) the characteristic vector of the relevant subword starting at a minimal
     * boundary, with respect to a letter. Equivalent to {@code
     * State.getRelevantSubwordCharacteristicVector(maxEditDistance, automatonString, letter)} for a
     * State with the given minimal boundary.
     *
     * @param minimalBoundary an int of the minimal boundary of the consuming State's member
     *     positions
     * @param letter the char that the characteristic vector is to be based off of
     * @return an AugBitSet representation of the characteristic vector
     */
    AugBitSet characteristicVector(int minimalBoundary, char letter) {
        // Relevant subword size: min(2n+1, |s| - minimalBoundary), clamped at 0
        int relevantSubwordSize =
                Math.min(2 * maxEditDistance + 1, automatonStringLength - minimalBoundary);
        if (relevantSubwordSize < 0) relevantSubwordSize = 0;

        int memoIndex = Math.min(minimalBoundary, automatonStringLength);
        ConcurrentHashMap<Character, AugBitSet> memo =
                characteristicVectorMemoByBoundary[memoIndex];
        if (memo == null) {
            // Benign race: racing threads may create equivalent buckets; contents are
            // deterministic so any bucket is valid
            memo = new ConcurrentHashMap<>();
            characteristicVectorMemoByBoundary[memoIndex] = memo;
        }

        AugBitSet cachedVector = memo.get(letter);
        if (cachedVector != null) return cachedVector;

        AugBitSet relevantSubwordCharacteristicVector = new AugBitSet(relevantSubwordSize);
        for (int i = 0; i < relevantSubwordSize; i++) {
            if (automatonString.charAt(minimalBoundary + i) == letter)
                relevantSubwordCharacteristicVector.set(i);
        }

        AugBitSet racedVector = memo.putIfAbsent(letter, relevantSubwordCharacteristicVector);
        return (racedVector == null ? relevantSubwordCharacteristicVector : racedVector);
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
     * Searches a collection of Strings for those which are within this automaton's max edit
     * distance of its reference String, using the mode this instance was compiled with
     * (table-lookup walk for table-based instances, on-the-fly simulation otherwise).
     *
     * @param mdag an MDAG containing the set of Strings to be processed against this automaton's
     *     reference String
     * @return a LinkedList containing all the Strings in {@code mdag} that are within this
     *     automaton's max edit distance of its reference String
     */
    public LinkedList<String> fuzzySearch(MDAG mdag) {
        return (parametricAutomaton != null ? tableFuzzySearch(mdag) : iterativeFuzzySearch(mdag));
    }

    /**
     * Searches a collection of Strings for those which are within this automaton's max edit
     * distance of its reference String, traversing the MDAG in-sync with a table-lookup walk of the
     * parametric automaton.
     *
     * <p>Requires a table-based instance (see {@link #compile(String, int)}).
     *
     * @param mdag an MDAG containing the set of Strings to be processed against this automaton's
     *     reference String
     * @return a LinkedList containing all the Strings in {@code mdag} that are within this
     *     automaton's max edit distance of its reference String
     */
    public LinkedList<String> tableFuzzySearch(MDAG mdag) {
        if (parametricAutomaton == null)
            throw new IllegalStateException(
                    "tableFuzzySearch requires a table-based instance; use compile(String, int)");

        // LinkedList which will contain Strings in mdag that are within maxEditDistance of
        // automatonString
        LinkedList<String> resultStringLinkedList = new LinkedList<>();

        // Stack to store processing steps of the search process
        Stack<TableProcessingStep> processingStepStack = new Stack<>();

        boolean mdagIsSimplified = mdag.getSourceNode().getClass().equals(SimpleMDAGNode.class);
        SimpleMDAGNode[] simpleMDAGArray = mdag.getSimpleMDAGArray();

        // Push onto processingStepStack the collection of objects which represent the start step of
        // the search process
        processingStepStack.push(
                new TableProcessingStep(
                        "", mdag.getSourceNode(), parametricAutomaton.getInitialState(), 0));

        char[] charArray = procureTransitionLabelArray(mdag);
        int charCount = charArray.length;

        // Transition through the MDAG and the automaton represented by the parametric transition
        // table in-sync, adding to resultStringLinkedList the char sequences that lead to both an
        // accept node (MDAG) and accepting state (automaton)
        while (!processingStepStack.isEmpty()) {
            TableProcessingStep currentProcessingStep = processingStepStack.pop();

            Object currentNodeObj = currentProcessingStep.mdagTransitionPathEndNode;
            ParametricState currentParametricState =
                    currentProcessingStep.lAutomatonTransitionPathEndParametricState;
            int currentMinimalBoundary =
                    currentProcessingStep.lAutomatonTransitionPathEndMinimalBoundary;

            // Loop through the chars in charArray, using each to transition the node & state in the
            // processing step at the top of the stack. If both the node and state have valid
            // transitions on a particular char, push the resulting transition String, node,
            // and state on the top of the stack
            for (int i = 0; i < charCount; i++) {
                char currentChar = charArray[i];

                // Execute a transition on the current MDAG node using currentChar
                Object transitionNode =
                        (mdagIsSimplified
                                ? ((SimpleMDAGNode) currentNodeObj)
                                        .transition(simpleMDAGArray, currentChar)
                                : ((MDAGNode) currentNodeObj).transition(currentChar));

                if (transitionNode != null) {
                    // Get the characteristic vector of the current step's relevant subword (with
                    // respect to currentChar) and use it to look up the transition's result state
                    AugBitSet rscv = characteristicVector(currentMinimalBoundary, currentChar);
                    ParametricState transitionParametricState =
                            parametricAutomaton.transition(currentParametricState, rscv);

                    if (transitionParametricState != null) {
                        int transitionMinimalBoundary =
                                currentMinimalBoundary
                                        + transitionParametricState.getTransitionBoundaryOffset();
                        String transitionPathString =
                                currentProcessingStep.transitionPathString + currentChar;

                        // Push the resulting processing step on to the top of processingStepStack
                        processingStepStack.push(
                                new TableProcessingStep(
                                        transitionPathString,
                                        transitionNode,
                                        transitionParametricState,
                                        transitionMinimalBoundary));

                        // If both transitionNode and the automaton state are "accepting", add the
                        // sequence of chars that lead to them to resultStringLinkedList
                        if (MDAG.isAcceptNode(transitionNode)
                                && isAcceptingParametricState(
                                        transitionParametricState, transitionMinimalBoundary))
                            resultStringLinkedList.add(transitionPathString);
                    }
                }
            }
        }

        return resultStringLinkedList;
    }

    /**
     * Searches a collection of Strings for those which are within this automaton's max edit
     * distance of its reference String, traversing the MDAG in-sync with an on-the-fly simulation
     * of the automaton's States.
     *
     * @param mdag an MDAG containing the set of Strings to be processed against this automaton's
     *     reference String
     * @return a LinkedList containing all the Strings in {@code mdag} that are within this
     *     automaton's max edit distance of its reference String
     */
    public LinkedList<String> iterativeFuzzySearch(MDAG mdag) {
        // LinkedList which will contain Strings in mdag that are within maxEditDistance of
        // automatonString
        LinkedList<String> resultStringLinkedList = new LinkedList<>();

        // Stack to store processing steps of the search process
        Stack<IterativeProcessingStep> processingStepStack = new Stack<>();

        boolean mdagIsSimplified = mdag.getSourceNode().getClass().equals(SimpleMDAGNode.class);
        SimpleMDAGNode[] simpleMDAGArray = mdag.getSimpleMDAGArray();

        // Push onto processingStepStack collection of objects which represent the start step of the
        // search process
        processingStepStack.push(
                new IterativeProcessingStep("", mdag.getSourceNode(), initialState));

        char[] charArray = procureTransitionLabelArray(mdag);
        int charCount = charArray.length;

        // Transition through the MDAG and automaton in-sync, adding to resultStringLinkedList
        // the char sequences that lead to both an accept node (MDAG) and accept state (automaton)
        while (!processingStepStack.isEmpty()) {
            IterativeProcessingStep currentProcessingStep = processingStepStack.pop();

            Object currentNodeObj = currentProcessingStep.mdagTransitionPathEndNode;
            State currentState = currentProcessingStep.lAutomatonTransitionPathEndState;

            // Loop through the chars in charArray, using each to transition the node & state in the
            // processing step at the top of the stack. If both the node and state have valid
            // transitions on a particular char, push the resulting transition String, node,
            // and State on the top of the stack
            for (int i = 0; i < charCount; i++) {
                char currentChar = charArray[i];

                // Execute a transition on the current MDAG node using currentChar
                Object transitionNode =
                        (mdagIsSimplified
                                ? ((SimpleMDAGNode) currentNodeObj)
                                        .transition(simpleMDAGArray, currentChar)
                                : ((MDAGNode) currentNodeObj).transition(currentChar));

                if (transitionNode != null) {
                    // Execute a transition on the current automaton State using currentChar (via
                    // the memoized characteristic vector of its relevant subword)
                    State transitionState =
                            currentState.transition(
                                    maxEditDistance,
                                    characteristicVector(
                                            currentState.getMinimalBoundary(), currentChar));

                    if (transitionState != null) {
                        // Push the resulting processing step on to the top of processingStepStack
                        String transitionPathString =
                                currentProcessingStep.transitionPathString + currentChar;
                        processingStepStack.push(
                                new IterativeProcessingStep(
                                        transitionPathString, transitionNode, transitionState));

                        // If both transitionNode and transitionState are "accepting", add the
                        // sequence of chars that lead to them to resultStringLinkedList
                        if (MDAG.isAcceptNode(transitionNode)
                                && isAcceptState(
                                        transitionState, automatonStringLength, maxEditDistance))
                            resultStringLinkedList.add(transitionPathString);
                    }
                }
            }
        }

        return resultStringLinkedList;
    }

    /**
     * Retrieves the sorted array of characters labeling the transitions of the Strings in an MDAG.
     *
     * @param mdag an MDAG
     * @return a char array of the characters composing the Strings in {@code mdag}
     */
    private static char[] procureTransitionLabelArray(MDAG mdag) {
        TreeSet<Character> charTreeSet = mdag.getTransitionLabelSet();
        int charCount = charTreeSet.size();

        char[] charArray = new char[charCount];
        int counter = 0;
        for (Character c : charTreeSet) charArray[counter++] = c;

        return charArray;
    }

    /**
     * An immutable group of objects collectively representing a processing step in a table-based
     * fuzzy search (replacing the former untyped Object[] stack entries).
     */
    private static final class TableProcessingStep {
        // The sequence of chars used to transition to this step
        final String transitionPathString;

        // The MDAG node of interest in this step
        final Object mdagTransitionPathEndNode;

        // The parametric state of the automaton in this step
        final ParametricState lAutomatonTransitionPathEndParametricState;

        // The minimal boundary of the automaton's concrete State in this step
        final int lAutomatonTransitionPathEndMinimalBoundary;

        TableProcessingStep(
                String transitionPathString,
                Object mdagTransitionPathEndNode,
                ParametricState lAutomatonTransitionPathEndParametricState,
                int lAutomatonTransitionPathEndMinimalBoundary) {
            this.transitionPathString = transitionPathString;
            this.mdagTransitionPathEndNode = mdagTransitionPathEndNode;
            this.lAutomatonTransitionPathEndParametricState =
                    lAutomatonTransitionPathEndParametricState;
            this.lAutomatonTransitionPathEndMinimalBoundary =
                    lAutomatonTransitionPathEndMinimalBoundary;
        }
    }

    /**
     * An immutable group of objects collectively representing a processing step in an
     * iterative-fuzzy-search (replacing the former untyped Object[] stack entries).
     */
    private static final class IterativeProcessingStep {
        // The sequence of chars used to transition to this step
        final String transitionPathString;

        // The MDAG node of interest in this step
        final Object mdagTransitionPathEndNode;

        // The automaton State of interest in this step
        final State lAutomatonTransitionPathEndState;

        IterativeProcessingStep(
                String transitionPathString,
                Object mdagTransitionPathEndNode,
                State lAutomatonTransitionPathEndState) {
            this.transitionPathString = transitionPathString;
            this.mdagTransitionPathEndNode = mdagTransitionPathEndNode;
            this.lAutomatonTransitionPathEndState = lAutomatonTransitionPathEndState;
        }
    }

    /**
     * Determines if two Strings are within a specified edit distance of one another.
     *
     * <p>This static convenience entry point compiles a table-based automaton per call; it is kept
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
     * Searches a collection of Strings for those which are within a given edit distance from a
     * particular String, using the parametric transition table.
     *
     * <p>This static convenience entry point is kept for backward compatibility; compile once and
     * reuse the instance to avoid rebuilding per query.
     *
     * @param maxEditDistance an int denoting the maximum amount of edit operations that can
     *     separate a String in the to-be-searched collection with the String of interest
     * @param automatonString the String that all edit-distance calculations are to be carried out
     *     in relation to
     * @param mdag an MDAG containing the set of Strings to be processed against {@code
     *     automatonString}
     * @return a LinkedList containing all the Strings in {@code mdag} that are at most {@code
     *     maxEditDistance} away from {@code automatonString}
     * @deprecated use {@link #compile(String, int)} and reuse the resulting instance's {@link
     *     #tableFuzzySearch(MDAG)}
     */
    @Deprecated
    public static LinkedList<String> tableFuzzySearch(
            int maxEditDistance, String automatonString, MDAG mdag) {
        return compile(automatonString, maxEditDistance).tableFuzzySearch(mdag);
    }

    /**
     * Searches a collection of Strings for those which are within a given edit distance from a
     * particular String, simulating the automaton's States on the fly.
     *
     * <p>This static convenience entry point is kept for backward compatibility; compile once and
     * reuse the instance to avoid rebuilding per query.
     *
     * @param maxEditDistance an int denoting the maximum amount of edit operations that can
     *     separate a String in the to-be-searched collection with the String of interest
     * @param automatonString the String that all edit-distance calculations are to be carried out
     *     in relation to
     * @param mdag an MDAG containing the set of Strings to be processed against {@code
     *     automatonString}
     * @return a LinkedList containing all the Strings in {@code mdag} that are at most {@code
     *     maxEditDistance} away from {@code automatonString}
     * @deprecated use {@link #compileIterative(String, int)} and reuse the resulting instance's
     *     {@link #iterativeFuzzySearch(MDAG)}
     */
    @Deprecated
    public static LinkedList<String> iterativeFuzzySearch(
            int maxEditDistance, String automatonString, MDAG mdag) {
        return compileIterative(automatonString, maxEditDistance).iterativeFuzzySearch(mdag);
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
