/**
 * LevenshteinAutomaton is a fast and comprehensive Java library capable
 * of performing automaton and non-automaton based Levenshtein distance
 * determination and neighbor calculations.
 * 
 *  Copyright (C) 2012 Kevin Lawson <Klawson88@gmail.com>
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vetronauta.lev.automaton;

import org.testng.Assert;
import org.vetronauta.lev.automaton.Position;
import org.vetronauta.lev.automaton.State;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 *
 * @author Kevin
 */
public class PositionTest {

    @DataProvider(name = "transitionInternalTestDp")
    Object[][] transitionInternalTestDataProvider() {
        int numberOfTests = 1000;
        int maxE = 10;
        
        Object[][] newArgArrayContainerArray = new Object[numberOfTests][1];
        
        for (int i = 0; i < numberOfTests; i++) {
            int randomN = (int)(Math.random() * maxE) + 1;
            int randomI = (int)(Math.random() * (2 * randomN + 1));
            boolean randomT = (i % 2 == 0);
            
            newArgArrayContainerArray[i] = new Object[]{new Position(randomI, randomN, randomT)};
        }
        
        return newArgArrayContainerArray;
    }

    private void matchTransitionAssertion(Position p, State s) {
        Assert.assertEquals(s.getMemberPositions()[0], new Position(p.getI() + 1, p.getE(), false));
    }

    private void insertionTransitionAssertion(Position p, State s) {
        Assert.assertEquals(s.getMemberPositions()[0], new Position(p.getI(), p.getE() + 1, false));
    }

    private void transpositionTransitionAssertion(Position p, State s) {
        Assert.assertEquals(s.getMemberPositions()[0], new Position(p.getI() + 2, p.getE(), false));
    }

    private void preTranspositionTransitionAssertion(Position p, State s, int hitIndex) {
        Position assertPos0 = new Position(p.getI(), p.getE() + 1, false);
        Position assertPos1 = new Position(p.getI(), p.getE() + 1, true);
        Position assertPos2 = new Position(p.getI() + 1, p.getE() + 1, false);
        Position assertPos3 = new Position(p.getI() + hitIndex + 1, p.getE() + (hitIndex + 1) - 1, false);
        
        Position[] memberPositionArray = s.getMemberPositions();
        
        Assert.assertEquals(memberPositionArray[0], assertPos0);
        Assert.assertEquals(memberPositionArray[1], assertPos1);
        Assert.assertEquals(memberPositionArray[2], assertPos2);
        Assert.assertEquals(memberPositionArray[3], assertPos3);
    }

    private void defaultTransitionAssertion(Position p, State s) {
        Position assertPos0 = new Position(p.getI(), p.getE() + 1, false);
        Position assertPos1 = new Position(p.getI() + 1, p.getE() + 1, false);
        Position[] memberPositionArray = s.getMemberPositions();

        Assert.assertEquals(memberPositionArray[0], assertPos0);
        Assert.assertEquals(memberPositionArray[1], assertPos1);
    }

    private void deletionTransitionAssertion(Position p, State s, int hitIndex) {
        Position assertPos0 = new Position(p.getI(), p.getE() + 1, false);
        Position assertPos1 = new Position(p.getI() + 1, p.getE() + 1, false);
        Position assertPos2 = new Position(p.getI() + hitIndex + 1, p.getE() + (hitIndex + 1) - 1, false);
        Position[] memberPositionArray = s.getMemberPositions();

        Assert.assertEquals(memberPositionArray[0], assertPos0);
        Assert.assertEquals(memberPositionArray[1], assertPos1);
        Assert.assertEquals(memberPositionArray[2], assertPos2);
    }
    
    
    @Test(dataProvider = "transitionInternalTestDp")
    void transitionInternalTest(Position p) {
        int mdStartIndex = p.getE() + (!p.getT() ? 2 : 1);
        
        for (int mdi = mdStartIndex; mdi >= p.getE(); mdi--) {
            for (int rsi = 0; rsi <= 2; rsi++) {
                for (int hii = -1; hii <= 4; hii++) {
                    int currentE = (mdi == p.getE() + 2 ? 0 : p.getE());
                    boolean currentT = (rsi >= 2 && p.getT());

                    Position currentPosition = new Position(p.getI(),currentE, currentT);
                    State s = currentPosition.transitionInternal(mdi, rsi, hii);
                    
                    if (currentE == 0 && currentE < mdi) {
                        switch(rsi) {
                            case 0:             insertionTransitionAssertion(currentPosition, s); 
                                                break;
                            case 1: {
                                if (hii == 0) {
                                    matchTransitionAssertion(currentPosition, s);
                                } else {
                                    defaultTransitionAssertion(currentPosition, s);
                                }
                                break;
                            }
                            default: {
                                switch(hii) {
                                    case -1:    defaultTransitionAssertion(currentPosition, s); break;
                                    case 0:     matchTransitionAssertion(currentPosition, s); break;
                                    case 1:     preTranspositionTransitionAssertion(currentPosition, s, hii); break;
                                    default:    deletionTransitionAssertion(currentPosition, s, hii); break;
                                }
                                break;
                            }
                        }
                    }
                    else if (currentE > 0 && currentE < mdi) {
                        switch(rsi) {
                            case 0:             insertionTransitionAssertion(currentPosition, s); 
                                                break;
                            case 1: {
                                if (hii == 0) {
                                    matchTransitionAssertion(currentPosition, s);
                                } else {
                                    defaultTransitionAssertion(currentPosition, s);
                                }
                                break;
                            }
                            default: {
                                if (!currentT) {
                                    switch(hii) {
                                        case -1:    defaultTransitionAssertion(currentPosition, s); break;
                                        case 0:     matchTransitionAssertion(currentPosition, s); break;
                                        case 1:     preTranspositionTransitionAssertion(currentPosition, s, hii); break;
                                        default:    deletionTransitionAssertion(currentPosition, s, hii); break;
                                    }
                                    break;
                                }
                                else {
                                    if (hii == 0) {
                                        transpositionTransitionAssertion(currentPosition, s);
                                    } else {
                                        Assert.assertNull(s);
                                    }
                                }
                            }
                        }
                    }
                    else {
                        if (rsi == 0) {
                            assert (s == null);
                        } else {
                            if (!currentPosition.getT()) {
                                if (hii == 0) {
                                    matchTransitionAssertion(currentPosition, s);
                                } else {
                                    Assert.assertNull(s);
                                }
                            } else {
                                if (hii == 0) {
                                    transpositionTransitionAssertion(currentPosition, s);
                                } else {
                                    Assert.assertNull(s);
                                }
                            }
                        }
                    }
                }
            }
        }
        
    }

    @DataProvider(name = "getPositionTransitionDataDP")
    Object[][] getPositionTransitionDataDataProvider() {
        int testCount = 1000;
        
        int maxN = 100;
        Object[][] argArrayContainerArray = new Object[testCount][];

        
        for (int i = 0; i < testCount; i++) {
            int randomN = (int)(Math.random() * maxN);
            int rsSize = 2 * randomN + 1;
            
            AugBitSet characteristicVec = new AugBitSet(rsSize);
            for (int j = 0; j < rsSize; j++) characteristicVec.set(j, ((int)(Math.random() * 2)) == 1);
    
            int randomStartIndex = (int)(Math.random() * rsSize) + (i % 2 == 0 ? 1 : 0);
            
            argArrayContainerArray[i] = new Object[]{randomN, randomStartIndex, characteristicVec};
        }
        
        return argArrayContainerArray;
        
    }
    
    @Test(dataProvider = "getPositionTransitionDataDP")
    void getPositionTransitionDataTest(int maxEditDistance, int positionStartIndex, AugBitSet stateRSCharacteristicVector) {
        int stateRSSize = stateRSCharacteristicVector.getRelevantBitSetSize();
        
        int randomE = (int)(Math.random() * maxEditDistance);
        boolean randomT = (positionStartIndex <= stateRSSize - 2) && (((int) (Math.random() * 2)) == 0);
        
        int[] transitionDataArray = (new Position(positionStartIndex, randomE, randomT)).procurePositionTransitionData(maxEditDistance, positionStartIndex, stateRSCharacteristicVector);

        Assert.assertEquals(transitionDataArray[0], (positionStartIndex == stateRSSize ? 0 : Math.min(maxEditDistance - randomE + 1, stateRSSize - positionStartIndex)));
        Assert.assertEquals(transitionDataArray[1], (positionStartIndex == stateRSSize ? -1 : stateRSCharacteristicVector.get(positionStartIndex, positionStartIndex + Math.min(maxEditDistance - randomE + 1, stateRSCharacteristicVector.getRelevantBitSetSize() - positionStartIndex)).nextSetBit(0)));
    }

    @Test
    void subsumesTest() {
        for (int i = 0; i < 1000; i++) {
            int randomN = (int)(Math.random() * 10) + 1;
            int randomE1 = randomN / 2;
            int randomI1 = 2 * randomN + 1;
            
            int offset = (int)(Math.random() * randomE1) + 1;
            
            int randomE2 = randomE1 + offset;
            int randomI2 = randomI1 + (int)(Math.random() * offset);
            
            int randomE3 = randomE1 + offset;
            int randomI3 = randomI1 + offset + (int)(Math.random() * 10) + 1;
            
            Position p1 = new Position(randomI1, randomE1, false);
            Position p2 = new Position(randomI2, randomE2, false);
            Position p3 = new Position(randomI3, randomE3, false);
            Position p4 = new Position(randomI1, randomN, false);
            
            Position tp1 = new Position(randomI1, randomE1, true);
            Position tp2 = new Position (randomI1, randomE2, true);

            Assert.assertTrue((!p1.subsumes(p1, randomN) && p1.subsumes(p2, randomN) && !p2.subsumes(p1, randomN) && !p1.subsumes(p3, randomN)
                     && tp1.subsumes(p4, randomN) && !tp1.subsumes(p1, randomN) && tp1.subsumes(tp2, randomN) && !tp2.subsumes(tp1, randomN)));
        }        
    }

    @Test
    void compareToTest() {
        for (int i = 0; i < 1000; i++) {
            int randomN = (int)(Math.random() * 10) + 1;
            int randomE1 = (int)(Math.random() * randomN);
            int randomI1 = 2 * randomN + 1;
            
            int randomE2 = randomE1 + (int)(Math.random() * 5);
            int randomI2 = randomI1 - ((int)(Math.random() * 10) + 1);
            
            int randomE3 = randomE1 - 1;

            Position p1 = new Position(randomI1, randomE1, false);
            Position p2 = new Position(randomI2, randomE2, false);
            Position p3 = new Position(randomI1, randomE3, false);
            
            Position tp1 = new Position(randomI1, randomE1, true);
           
            Assert.assertTrue((p1.compareTo(p2) > 0 && p2.compareTo(p1) < 0 && p3.compareTo(p1) < 0
                    && tp1.compareTo(p2) > 0 && p2.compareTo(tp1) < 0 && p3.compareTo(tp1) < 0
                    && tp1.compareTo(p1) > 0 && p1.compareTo(tp1) < 0));
        }        
    }
    
    @Test
    void equalsTest() {
        for (int i = 0; i < 1000; i++) {
            int eqN1 = (int)(Math.random() * 1000) + 1;
            int eqE1 = (int)(Math.random() * eqN1);
            int eqI1 = 2 * eqN1 + 1;
            boolean eqT1 = (i % 2 == 0);

            Assert.assertEquals(new Position(eqI1, eqE1, eqT1), new Position(eqI1, eqE1, eqT1));
        }
        
        for (int i = 0; i < 1000; i++) {
            int randomN1 = (int)(Math.random() * 1000) + 1;
            int randomE1 = (int)(Math.random() * randomN1);
            int randomI1 = 2 * randomN1 + 1;
            boolean randomT1 = (i % 2 == 0);
            
            int randomE2 = randomE1;
            int randomI2 = randomI1;
            boolean randomT2 = randomT1;
            
            switch(i % 3) {
                case 0: randomE2 += (int)(Math.random() * 5) + 1; break;
                case 1: randomI2 +=  (int)(Math.random() * 5) + 1; break;
                case 2: randomT2 = !randomT1;
            }

            Assert.assertNotEquals(new Position(randomI1, randomE1, randomT1), new Position(randomI2, randomE2, randomT2));
        }        
    }
}
