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
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Kevin
 */
public class StateTest {

    @Test(enabled = false)
    void StateConstructorTest() {
        for (int i = 0; i < 1000; i++) {
            int memberCount = (int) (Math.random() * 100);
            List<Position> memberPositionArrayList = new ArrayList<>(memberCount);
            memberPositionArrayList.add(new Position(0, 0, false));

            for (int j = 0; j < memberCount - 1; j++) {
                int randomE = (int) (Math.random() * i) + 1;
                int randomI = (int) (Math.random() * randomE);
                boolean randomT = (i % 2 == 0);

                memberPositionArrayList.add(new Position(randomI, randomE, randomT));
            }

            Collections.sort(memberPositionArrayList);
            State invalidState = new State(memberPositionArrayList.toArray(new Position[0]));

            State validState = new State(List.of(invalidState), memberCount);

            Assert.assertEquals(validState.getMemberPositions().length, 1);
        }
        for (int i = 0; i < 1000; i++) {
            int memberCount = (int) (Math.random() * 100);
            List<Position> memberPositionArrayList = new ArrayList<>(memberCount);
            memberPositionArrayList.add(new Position(0, 1, true));

            for (int j = 0; j < memberCount - 1; j++) {
                boolean randomT = (i % 2 == 0);

                int randomE = (int) (Math.random() * i) + 1;
                int randomI = (randomT ? 0 : (int) (Math.random() * randomE));

                memberPositionArrayList.add(new Position(randomI, randomE, randomT));
            }

            Collections.sort(memberPositionArrayList);
            State invalidState = new State(memberPositionArrayList.toArray(new Position[0]));

            State validState = new State(List.of(invalidState), memberCount);

            Assert.assertEquals(validState.getMemberPositions().length, 1);
        }
        for (int i = 0; i < 1000; i++) {
            int nonBaseMemberCount = (int) (Math.random() * 100);
            List<Position> memberPositionArrayList = new ArrayList<>(nonBaseMemberCount + 1);
            memberPositionArrayList.add(new Position(0, 0, false));

            int iBase = 0;

            for (int j = 0; j < nonBaseMemberCount; j++) {
                iBase += (int) (Math.random() * 100) + 1;
                memberPositionArrayList.add(new Position(iBase, 0, (i % 2 == 0)));
            }

            Collections.sort(memberPositionArrayList);

            State invalidState = new State(memberPositionArrayList.toArray(new Position[0]));
            State validState = new State(List.of(invalidState), 1);

            Assert.assertEquals(validState.getMemberPositions().length, nonBaseMemberCount + 1);
        }

        for (int i = 0; i < 1000; i++) {
            int nonBaseMemberCount = (int) (Math.random() * 100);
            List<Position> memberPositionArrayList = new ArrayList<>(nonBaseMemberCount + 1);
            memberPositionArrayList.add(new Position(0, 1, true));

            int iBase = 0;

            for (int j = 0; j < nonBaseMemberCount; j++) {
                iBase += (int) (Math.random() * 100) + 1;
                memberPositionArrayList.add(new Position(iBase, 1, (i % 2 == 0)));
            }

            Collections.sort(memberPositionArrayList);

            State invalidState = new State(memberPositionArrayList.toArray(new Position[0]));
            State validState = new State(List.of(invalidState), 1);

            Assert.assertEquals(validState.getMemberPositions().length, nonBaseMemberCount + 1);
        }
    }

    @DataProvider(name = "getRelevantSubwordCharacteristicVectorArrayDP")
    Object[][] getRelevantSubwordCharacterisitcVectorArrayTestDataProvider() {
        int testCount = 1000;

        Object[][] argArrayContainerArray = new Object[testCount][];
        int randomStrLength = (int) (Math.random() * 20);

        for (int i = 0; i < testCount; i++) {
            boolean randomT = (i % 2 == 0);
            int randomN = (int) (Math.random() * 100);
            int randomE = (int) (Math.random() * randomN) + (randomT ? 1 : 0);

            int randomI = (int) (Math.random() * randomStrLength - (randomT ? 1 : 0));

            char randomDesiredBinaryDigit = (char) (48 + (int) (Math.random() * 2));

            StringBuilder strBuilder = new StringBuilder();
            for (int j = 0; j < randomStrLength; j++) {
                strBuilder.append((char) (((int) (Math.random() * 2)) + 48));
            }

            argArrayContainerArray[i] =
                    new Object[] {
                        randomN,
                        new Position(randomI, randomE, randomT),
                        strBuilder.toString(),
                        randomDesiredBinaryDigit
                    };
        }

        return argArrayContainerArray;
    }

    @Test(dataProvider = "getRelevantSubwordCharacteristicVectorArrayDP")
    void getRelevantSubwordCharacteristicVectorArrayTest(
            int maxEditDistance, Position p, String automatonString, char letter) {
        State s = new State(new Position[] {p});
        AugBitSet relevantSubwordCV =
                s.getRelevantSubwordCharacteristicVector(maxEditDistance, automatonString, letter);

        String unprocessedAutomatonSubstring = automatonString.substring(p.getI());

        for (int i = 0; i < relevantSubwordCV.getRelevantBitSetSize(); i++) {
            int currentBit = (relevantSubwordCV.get(i) ? 1 : 0);
            Assert.assertEquals(
                    currentBit == (unprocessedAutomatonSubstring.charAt(i) - 48), (letter != '0'));
        }
    }

    @Test
    void canBeStateTestDataProvider() {
        Position[] posArray1 = new Position[] {new Position(0, 0, false)};
        Position[] posArray2 = new Position[] {new Position(0, 1, true)};

        State s1 = new State(posArray1);
        State s2 = new State(posArray2);

        for (int i = 0; i < 1000; i++) {
            boolean randomT = (i % 2 == 0);
            int randomE = (int) (Math.random() * i) + 1;
            int randomI = (int) (Math.random() * randomE);

            Assert.assertFalse(State.canBeState(s1, new Position(randomI, randomE, randomT), 1000));
        }

        for (int i = 0; i < 1000; i++) {
            boolean randomT = (i % 2 == 0);
            int randomI = (int) (Math.random() * i) + 1;
            int randomE = (int) (Math.random() * randomI);

            Assert.assertTrue(
                    State.canBeState(s1, new Position(randomI, randomE, randomT), randomI));
        }

        for (int i = 0; i < 1000; i++) {
            boolean randomT = (i % 2 == 0);
            int baseE = (randomT ? s2.getMemberPositions()[0].getE() : 0);
            int randomE = (randomT ? baseE + (int) (Math.random() * 1000) + 1 : 1000);

            Assert.assertFalse(State.canBeState(s2, new Position(0, randomE, randomT), 1000));
        }

        for (int i = 0; i < 1000; i++) {
            boolean randomT = (i % 2 == 0);

            int randomE =
                    randomT
                            ? (int) (Math.random() * s2.getMemberPositions()[0].getE())
                            : Math.min((int) (Math.random() * i) + 1, 999);

            Assert.assertTrue(State.canBeState(s2, new Position(0, randomE, randomT), 1000));
        }
    }
}
