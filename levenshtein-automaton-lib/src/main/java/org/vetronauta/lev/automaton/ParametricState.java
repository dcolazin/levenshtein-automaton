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

/**
 * A class capable of representing groups of {@link org.vetronauta.lev.automaton.State}s which
 * contain positions that are related in a specific way.
 *
 * @author Kevin
 */
public class ParametricState {
    // Array of ints containing the relative boundary values of positions in a State of this form.
    // This array, like the State position array it will be based off, will contain values in
    // ascending order.
    private final int[] memberPositionBoundaryOffsetArray;

    // Array of ints containing the edit distance values of positions in a State of this form.
    // Though this array
    // is based off a State position array, its values may not necessarily be stored in ascending
    // order.
    private final int[] memberPositionEArray;

    // Array of booleans containing values each denoting the transposition position
    // status of a member position in a state of this form.
    private final boolean[] memberPositionTArray;

    // Int denoting min(e_j - offset_j) over the member positions of a State of this form. A
    // concrete State of this form with minimal boundary minB is accepting with respect to a string
    // of length L and a max edit distance n iff minB >= L - n + acceptanceThresholdOffset.
    private final int acceptanceThresholdOffset;

    /**
     * Constructs a ParametricState from a concrete State.
     *
     * @param state a State object
     */
    public ParametricState(State state) {
        Position[] stateMemberPositionArray = state.getMemberPositions();

        int memberPositionCount = stateMemberPositionArray.length;
        memberPositionBoundaryOffsetArray = new int[memberPositionCount];
        memberPositionEArray = new int[memberPositionCount];
        memberPositionTArray = new boolean[memberPositionCount];

        // Loop through stateMemberPositionArray (which contains positions sorted in ascending
        // order),
        // inserting the relative (from the minimal boundary) boundaries and absolute edit distance
        // and T values in to the corresponding indices of the relevant arrays
        int minimumEditMinusOffset = Integer.MAX_VALUE;
        for (int i = 0; i < memberPositionCount; i++) {
            memberPositionBoundaryOffsetArray[i] =
                    stateMemberPositionArray[i].getI() - stateMemberPositionArray[0].getI();
            memberPositionEArray[i] = stateMemberPositionArray[i].getE();
            memberPositionTArray[i] = stateMemberPositionArray[i].getT();

            minimumEditMinusOffset =
                    Math.min(
                            minimumEditMinusOffset,
                            memberPositionEArray[i] - memberPositionBoundaryOffsetArray[i]);
        }

        acceptanceThresholdOffset = minimumEditMinusOffset;
    }

    /**
     * Returns the largest relative boundary of a position in a State of this form.
     *
     * @return an int denoting the largest boundary of a position in a State of this form
     */
    public int getLargestPositionOffset() {
        return memberPositionBoundaryOffsetArray[memberPositionBoundaryOffsetArray.length - 1];
    }

    /**
     * Returns the acceptance threshold offset of this ParametricState.
     *
     * <p>A concrete State of this form with minimal boundary {@code minB} is accepting with respect
     * to a reference string of length {@code L} and max edit distance {@code n} iff {@code minB >=
     * L - n + getAcceptanceThresholdOffset()}.
     *
     * <p>This is derived from the per-position acceptance rule (position j with boundary {@code
     * minB + offset_j} and edit count {@code e_j} is accepting iff {@code L - (minB + offset_j) <=
     * n - e_j}): the state is accepting iff some member position is, i.e. iff {@code minB >= L - n
     * + min_j(e_j - offset_j)}.
     *
     * @return an int of min(e_j - offset_j) over the member positions of a State of this form
     */
    public int getAcceptanceThresholdOffset() {
        return acceptanceThresholdOffset;
    }

    /**
     * Creates a State with Positions which that satisfy the relationships defined by this
     * ParametricState.
     *
     * @param minimalBoundary an int of the desired value of the to-be-created State's minimal
     *     boundary (all other boundaries in the State will be based off this)
     * @return a State containing member positions which satisfy the relationships defined by this
     *     ParametricState
     */
    public State createActualState(int minimalBoundary) {
        // Define the number of member Positions in the to-be-created State
        // (since a state of identical form was used to create this ParametricState,
        // this number is simply the size either of the array fields)
        int memberPositionCount = memberPositionBoundaryOffsetArray.length;

        // Create an array to hold the to-be-created Positions that will serve as members of the
        // to-be-created State
        Position[] actualStateMemberPositionArray = new Position[memberPositionCount];

        // Loop through memberPositionBoundaryOffsetArray, memberPositionEArray and
        // memberPositionTArray,
        // using the values in the corresponding indices of each (along with minimalBoundary)
        // to create Positions to be inserted in to the corresponding index in
        // stateMemberPositionArray
        for (int i = 0; i < memberPositionCount; i++) {
            int currentBoundaryOffset = memberPositionBoundaryOffsetArray[i];
            int currentE = memberPositionEArray[i];
            boolean currentT = memberPositionTArray[i];

            actualStateMemberPositionArray[i] =
                    new Position(minimalBoundary + currentBoundaryOffset, currentE, currentT);
        }

        return new State(actualStateMemberPositionArray);
    }

    /**
     * Determines the equality of this ParametricState with another object.
     *
     * <p>A ParametricState is equal to another object iff the argument object is also a
     * ParametricState and has field arrays that are equal to those of this ParametricState.
     *
     * @param obj an Object
     * @return true if {@code obj} is a ParametricState and its field arrays are equal to those of
     *     this ParametricState; false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        boolean areEqual = (this == obj);

        if (!areEqual && obj != null && obj.getClass().equals(ParametricState.class)) {
            ParametricState pState = (ParametricState) obj;

            areEqual =
                    (Arrays.equals(
                                    this.memberPositionBoundaryOffsetArray,
                                    pState.memberPositionBoundaryOffsetArray)
                            && Arrays.equals(this.memberPositionEArray, pState.memberPositionEArray)
                            && Arrays.equals(
                                    this.memberPositionTArray, pState.memberPositionTArray));
        }

        return areEqual;
    }

    /**
     * Returns the hash code value of this ParametricState.
     *
     * @return an int of the hash code value of this ParametricState
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Arrays.hashCode(this.memberPositionBoundaryOffsetArray);
        hash = 61 * hash + Arrays.hashCode(this.memberPositionEArray);
        hash = 61 * hash + Arrays.hashCode(this.memberPositionTArray);
        return hash;
    }

    /**
     * Returns a String representation of this ParametricState.
     *
     * @return a String of this ParametricState's parametric positions
     */
    @Override
    public String toString() {
        StringBuilder returnString =
                new StringBuilder(
                        "i"
                                + (memberPositionTArray[0] ? "(t)" : "")
                                + "#"
                                + memberPositionEArray[0]
                                + ")");

        int memberPositionCount = memberPositionBoundaryOffsetArray.length;
        for (int i = 1; i < memberPositionCount; i++)
            returnString
                    .append(" (i + ")
                    .append(memberPositionBoundaryOffsetArray[i])
                    .append(")")
                    .append(memberPositionTArray[i] ? "(t)" : "")
                    .append("(#")
                    .append(memberPositionEArray[i])
                    .append(")");

        return returnString.toString();
    }
}
