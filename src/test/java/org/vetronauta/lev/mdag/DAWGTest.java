/**
 * MDAG is a Java library capable of constructing character-sequence-storing,
 * directed acyclic graphs of minimal size. 
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

package org.vetronauta.lev.mdag;

import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/**
 *
 * @author Kevin
 */
public class DAWGTest {

    private ArrayList<String> wordArrayList;
    private MDAG dawg1;
    private MDAG dawg2;
    
    @BeforeSuite
    public void initializer() throws IOException {
        wordArrayList = new ArrayList<>(100000);
        BufferedReader breader = new BufferedReader(new FileReader("./src/test/resources/words.txt"));
        String currentWord;
        
        while((currentWord = breader.readLine()) != null) {
            wordArrayList.add(currentWord);
        }

        dawg1 = new MDAG(wordArrayList);
        
        Collections.shuffle(wordArrayList);
        dawg2 = new MDAG(wordArrayList);

        dawg2.simplify();
    }

    @Test
    void dawgBGetMinimizationIndexTest() {
       Assert.assertEquals(dawg1.calculateMinimizationProcessingStartIndex("", ""), -1);
       Assert.assertEquals(dawg1.calculateMinimizationProcessingStartIndex("abcd", "efg"), 0);
       Assert.assertEquals(dawg1.calculateMinimizationProcessingStartIndex("abcd", "ab"), 2);
       Assert.assertEquals(dawg1.calculateMinimizationProcessingStartIndex("abcd", "abcd"), -1);
       Assert.assertEquals(dawg1.calculateMinimizationProcessingStartIndex("abcd", "abd"), 2);
       Assert.assertEquals(dawg1.calculateMinimizationProcessingStartIndex("abcd", "abcr"), 3);
       Assert.assertEquals(dawg1.calculateMinimizationProcessingStartIndex("abcd", ""), 0);
       Assert.assertEquals(dawg1.calculateMinimizationProcessingStartIndex("", "abcd"), -1);
    }

    @Test
    void dawgBGetLongestStoredSubsequenceTest() {
        Assert.assertEquals(dawg1.determineLongestPrefixInMDAG("do"), "do");
        Assert.assertEquals(dawg1.determineLongestPrefixInMDAG("doggy"), "doggy");
        Assert.assertEquals(dawg1.determineLongestPrefixInMDAG("c"), "c");
        Assert.assertEquals(dawg1.determineLongestPrefixInMDAG("catsing"), "cats");
        Assert.assertEquals(dawg1.determineLongestPrefixInMDAG("brolic"), "bro");
        Assert.assertEquals(dawg1.determineLongestPrefixInMDAG("1234"), "");
        Assert.assertEquals(dawg1.determineLongestPrefixInMDAG("Czechoslovakians"), "Czechoslovakians");
    }
    
    @Test
    void dawgBGetTransitionPathFirstConfluenceNodeDataTest() {
        Assert.assertNotNull(dawg1.getTransitionPathFirstConfluenceNodeData((MDAGNode)dawg1.getSourceNode(), "caution").get("confluenceNode"));
        Assert.assertNotNull(dawg1.getTransitionPathFirstConfluenceNodeData((MDAGNode)dawg1.getSourceNode(), "abated").get("confluenceNode"));
        Assert.assertNotNull(dawg1.getTransitionPathFirstConfluenceNodeData((MDAGNode)dawg1.getSourceNode(), "watching").get("confluenceNode"));
    }

    @Test
    void dawgBBuildTest() {
        for (String currentWord : wordArrayList) {
            Assert.assertTrue(dawg1.contains(currentWord), "dawg1 does not contain " + currentWord);
            Assert.assertTrue(dawg2.contains(currentWord), "dawg2 does not contain " + currentWord);
        }
    }
    
    @DataProvider(name = "removeWordDP")
    Object[][] removeWordDataProvider() {
        int numberOfRuns = 20;
        Object[][] parameterObjectDoubleArray = new Object[numberOfRuns][];
        
        int wordArrayListSize = wordArrayList.size();        
        for (int i = 0; i < numberOfRuns; i++, wordArrayListSize--) {
            Object[] currentParameterArray = new Object[1];
            currentParameterArray[0] = (int)(Math.random() * wordArrayListSize);
            parameterObjectDoubleArray[i] = currentParameterArray;
        }
        
        return parameterObjectDoubleArray;
    }
    
    @Test(dataProvider = "removeWordDP")
    void removeWordTest(Integer wordIndex) {
        String toBeRemovedWord = wordArrayList.get(wordIndex);
        
        MDAG testDAWG = new MDAG(wordArrayList);
        testDAWG.removeString(wordArrayList.get(wordIndex));
        
        wordArrayList.remove((int)wordIndex);
        MDAG controlTestDAWG = new MDAG(wordArrayList);
        
        Assert.assertEquals(testDAWG.getNodeCount(), controlTestDAWG.getNodeCount(), "Removed word: " + toBeRemovedWord);
        Assert.assertEquals(testDAWG.getEquivalenceClassCount(), controlTestDAWG.getEquivalenceClassCount(), "Removed word: " + toBeRemovedWord);
        Assert.assertEquals(testDAWG.getTransitionCount(), controlTestDAWG.getTransitionCount(), "Removed word: " + toBeRemovedWord);
    }
    
    @DataProvider(name = "removeWord2DP")
    Object[][] removeWord2DataProvider() {
        int numberOfRuns = 20;
        int intervalSize = 20;
        
        Object[][] parameterObjectDoubleArray = new Object[numberOfRuns][];

        int wordArrayListSize = wordArrayList.size();
        for (int i = 0; i < numberOfRuns; i++, wordArrayListSize -= intervalSize) {
            int intervalBoundaryIndex1 = (int)(Math.random() * wordArrayListSize);
            int intervalBoundaryIndex2;
            
            if (intervalBoundaryIndex1 + intervalSize >= wordArrayListSize) {
                intervalBoundaryIndex2 = intervalBoundaryIndex1 - intervalSize;
            }
            else {
                intervalBoundaryIndex2 = intervalBoundaryIndex1 + intervalSize;
            }
            
            Object[] currentParameterArray = new Object[2];
            currentParameterArray[0] = Math.min(intervalBoundaryIndex1, intervalBoundaryIndex2);
            currentParameterArray[1] = Math.max(intervalBoundaryIndex1, intervalBoundaryIndex2);
            parameterObjectDoubleArray[i] = currentParameterArray;
        }
        
        return parameterObjectDoubleArray;
    }
    
    
    @Test(dataProvider = "removeWord2DP")
    void removeWord2(Integer intervalBegin, Integer onePastIntervalEnd) {
        MDAG testDAWG = new MDAG(wordArrayList);
        
        int intervalSize = onePastIntervalEnd - intervalBegin;
        for (int i = 0; i < intervalSize; i++) {
            testDAWG.removeString(wordArrayList.get(intervalBegin + i));
        }
        
        for (int i = 0; i < intervalSize; i++) {
            wordArrayList.remove(intervalBegin.intValue());
        }
        
        MDAG controlTestDAWG = new MDAG(wordArrayList);
        
        Assert.assertEquals(testDAWG.getNodeCount(), controlTestDAWG.getNodeCount());
        Assert.assertEquals(testDAWG.getEquivalenceClassCount(), controlTestDAWG.getEquivalenceClassCount());
        Assert.assertEquals(testDAWG.getTransitionCount(), controlTestDAWG.getTransitionCount());
    }

    @Test
    void getAllWordsTest() {
        HashSet<String> wordHashSet1 = dawg1.getAllStrings();
        HashSet<String> wordHashSet2 = dawg2.getAllStrings();
        Assert.assertTrue(wordHashSet1.containsAll(wordArrayList));
        Assert.assertTrue(wordHashSet2.containsAll(wordArrayList));
    }

    @Test
    void containsTest() {
        for (int i = 0; i < 100; i++) {
            Assert.assertTrue(dawg1.contains(wordArrayList.get(i)));
            Assert.assertTrue(dawg2.contains(wordArrayList.get(i)));
        }
    }
    
    @DataProvider(name = "searchDP")
    Object[][] searchDataProvider() {
        return new Object[][]{
                    {"ang"},
                    {"iter"},
                    {"con"},
                    {"pro"},
                    {"nan"},
                    {"ing"},
                    {"inter"},
                    {"ton"},
                    {"tion"},
                };
    }
    
    
    @Test(dataProvider = "searchDP")
    void getAllWordsWithPrefixTest(String prefixStr) {
        HashSet<String> controlSet = new HashSet<>();
        
        for (String str : wordArrayList) {
            if (str.startsWith(prefixStr)) {
                controlSet.add(str);
            }
        }
        
        Assert.assertEquals(dawg1.getStringsStartingWith(prefixStr), controlSet);
        Assert.assertEquals(dawg2.getStringsStartingWith(prefixStr), controlSet);
    }
    
    
    @Test(dataProvider = "searchDP")
    void getStringsWithSubstringTest(String substringStr) {
        HashSet<String> controlSet = new HashSet<>();
        
        for (String str : wordArrayList) {
            if (str.contains(substringStr)) {
                controlSet.add(str);
            }
        }

        Assert.assertEquals(dawg1.getStringsWithSubstring(substringStr), controlSet);
        Assert.assertEquals(dawg2.getStringsWithSubstring(substringStr), controlSet);
    }
    
    
    @Test(dataProvider = "searchDP")
    void getStringsEndingWithTest(String suffixStr) {
        HashSet<String> controlSet = new HashSet<>();
        
        for (String str : wordArrayList) {
            if (str.endsWith(suffixStr)) {
                controlSet.add(str);
            }
        }

        Assert.assertEquals(dawg1.getStringsEndingWith(suffixStr), controlSet);
        Assert.assertEquals(dawg2.getStringsEndingWith(suffixStr), controlSet);
    }
}
