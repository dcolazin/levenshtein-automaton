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
import org.testng.annotations.Test;
import org.vetronauta.lev.mdag.MDAG;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 *
 * @author Kevin
 */
public class LevenshteinAutomatonTest {

    private final List<String> wordArrayList = new ArrayList<>();
    private MDAG mdag = null;

    @BeforeClass
    public void initializer() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/words.txt")) {
            BufferedReader breader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String currentWord;

            while ((currentWord = breader.readLine()) != null) {
                wordArrayList.add(currentWord);
            }
        }
        
        mdag = new MDAG(wordArrayList);
    }
    
     @DataProvider(name = "automatonWordDataProvider")
     Object[][] automatonWordDataProvider() {
         int testCount = 15;
         Object[][] argArrayContainerArray = new Object[testCount][];
         
         int wordCount = wordArrayList.size();
         
         for (int i = 0; i < testCount; i++) {
             int maxEditDistanceToTest = 2;
             int randomMaxEditDistance =  (int)(Math.random() * maxEditDistanceToTest) + 1;
             int randomIndex = (int)(Math.random() * wordCount);
             argArrayContainerArray[i] = new Object[]{randomMaxEditDistance, wordArrayList.get(randomIndex)};
         }
         
         return argArrayContainerArray;
     }
     

     @Test(enabled = false, dataProvider = "automatonWordDataProvider")
     void tableFuzzyProfiler(int maxEditDistance, String str) {
         LevenshteinAutomaton.tableFuzzySearch(maxEditDistance, str, mdag);
     }

     @Test(dataProvider = "automatonWordDataProvider")
     void tableFuzzySearchTest(int maxEditDistance, String str) {
         HashSet<String> resultHashSet1 = new HashSet<>();
         
         for (String currentWord : wordArrayList) {
             if (ClassicLevenshtein.computeEditDistance(str, currentWord) <= maxEditDistance) {
                 resultHashSet1.add(currentWord);
             }
         }
         
         HashSet<String> resultHashSet2 = new HashSet<>(LevenshteinAutomaton.tableFuzzySearch(maxEditDistance, str, mdag));

         Assert.assertEquals(resultHashSet1, resultHashSet2);
     }

     @Test(dataProvider = "automatonWordDataProvider")
     void iterativeFuzzySearchTest(int maxEditDistance, String str) {
         HashSet<String> resultHashSet1 = new HashSet<>();
         
         for (String currentWord : wordArrayList) {
             if (ClassicLevenshtein.computeEditDistance(str, currentWord) <= maxEditDistance) {
                 resultHashSet1.add(currentWord);
             }
         }

         HashSet<String> resultHashSet2 = new HashSet<>(LevenshteinAutomaton.iterativeFuzzySearch(maxEditDistance, str, mdag));
         Assert.assertEquals(resultHashSet1, resultHashSet2);
     }

     @Test(dataProvider = "automatonWordDataProvider")
     void isWithinEditDistance(int maxEditDistance, String str) {
         for (String currentWord : wordArrayList) {
             Assert.assertEquals(ClassicLevenshtein.computeEditDistance(str, currentWord) <= maxEditDistance,
                 LevenshteinAutomaton.isWithinEditDistance(maxEditDistance, str, currentWord));
         }
     }

}
