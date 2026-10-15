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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A class representing a collection of Positions that are subsubmed by a common base Position and
 * do not subsume each other.
 *
 * @author Kevin
 */
public class State {
    // An array of Positions representing the boundary and edit count combinations that can result
    // from
    // using the containing automaton to process a substring of the given String against a sequence
    // of characters
    private final Position[] memberPositionArray;

    /**
     * Constructs a State from a sorted array of Positions. Positions in the array are assumed to
     * satisfy the requirements of membership in a state (see class description).
     *
     * @param memberPositionArray an array of Positions (sorted in ascending order) that will become
     *     members of the to-be-created state
     */
    public State(Position[] memberPositionArray) {
        this.memberPositionArray = memberPositionArray;
    }

    /**
     * Creates a State from the Positions serving as members in a collection of States.
     *
     * @param stateCollection a Collection of States
     * @param maxEditDistance an int of the maximum amount of edit operations allowed by the
     *     automaton associated with the States in {@code stateCollection} (and commutatively, the
     *     to-be-created State)
     */
    State(Collection<State> stateCollection, int maxEditDistance) {
        // HashSet which will contain Positions that will be members of the to-be-created State
        HashSet<Position> prospectiveMemberPositionCollection = new HashSet<>();

        // Loop through the positions in each state, adding them to
        // prospectiveMemberPositionCollection
        for (State state : stateCollection) {
            for (Position p :
                    state.memberPositionArray) { // PERF: this is in a thigh loop, do not create
                // lists unnecessarily
                prospectiveMemberPositionCollection.add(p);
            }
        }

        // Iterate through prospectiveMemberPositionCollection, removing Positions
        // which are subsubmed by at least one other Position in the set
        Iterator<Position> it1 = prospectiveMemberPositionCollection.iterator();
        while (it1.hasNext()) {
            // Processing position which is assumed to be a susumption target of another Position in
            // the set
            Position position1 = it1.next();

            // Iterate through prospectiveMemberPositionCollection again, checking for the presence
            // of a Position which subsumes position1. If such a state exists, remove position1
            // from the set (no longer satisfies membership requirements).
            for (Position position2 : prospectiveMemberPositionCollection) {
                if (position2.subsumes(position1, maxEditDistance)) {
                    it1.remove();
                    break;
                }
            }
        }

        // Convert prospectiveMemberPositionCollection to an array and sort it
        memberPositionArray =
                prospectiveMemberPositionCollection.toArray(
                        new Position[prospectiveMemberPositionCollection.size()]);
        Arrays.sort(memberPositionArray);
    }

    /**
     * Returns this State's member positions.
     *
     * @return an array of Positions contained in this State
     */
    public Position[] getMemberPositions() {
        return memberPositionArray;
    }

    /**
     * Returns the State resulting from the processing of this State and the characteristic vector
     * of its relevant subword.
     *
     * @param maxEditDistance an int of the maximum number of edit operations desired
     * @param vectorBits a long whose bit k denotes whether position k of this State's relevant
     *     subword holds the processing char
     * @param vectorSize an int of the bit count of the characteristic vector
     * @return the State resulting from the transitions of each of this State's member Positions
     *     (with regards to the arguments) or null if none of these transitions yields a non-failure
     *     State
     */
    public State transition(int maxEditDistance, long vectorBits, int vectorSize) {
        // HashSet which will hold the States resulting from transitioning each Position in
        // memberPositionArray
        HashSet<State> newStateHashSet = new HashSet<>();

        // The int which will be used to determine the index in the characteristic vector that the
        // relevant subwords of positions in memberPositionArray start at
        int boundaryOffset = memberPositionArray[0].getI();

        // Perform a transition on each member position, adding the resulting
        // State to newStateHashSet provided it isn't a failure state
        for (Position p : memberPositionArray) {
            State transitionState =
                    p.transition(
                            maxEditDistance, p.getI() - boundaryOffset, vectorBits, vectorSize);
            if (transitionState != null) newStateHashSet.add(transitionState);
        }

        return (newStateHashSet.isEmpty() ? null : new State(newStateHashSet, maxEditDistance));
    }

    /**
     * Returns a String representation of this State.
     *
     * @return a String consisting of the String representation of this State's member positions
     */
    @Override
    public String toString() {
        StringBuilder returnString = new StringBuilder(memberPositionArray[0].toString());

        int memberPositionCount = memberPositionArray.length;
        for (int i = 1; i < memberPositionCount; i++)
            returnString.append(" ").append(memberPositionArray[i].toString());

        return returnString.toString();
    }

    /**
     * Determines the difference between the minimal boundary values contained by Positions in two
     * States.
     *
     * @param state1 a State
     * @param state2 a State
     * @return an int denoting the difference between the minimal boundary value of a Position in
     *     {@code state1} with that of {@code state2}
     */
    public static int getMinimumBoundariesDifference(State state1, State state2) {
        return Math.abs(
                state1.memberPositionArray[0].getI() - state2.memberPositionArray[0].getI());
    }
}
