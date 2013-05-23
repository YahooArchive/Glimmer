package com.yahoo.glimmer.util;

/*
 * Copyright (c) 2012 Yahoo! Inc. All rights reserved.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is 
 *  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 *  See accompanying LICENSE file.
 */

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class UtilTest {

    @Test
    public void nullTest() {
	List<String> shortNames = Util.generateShortNames(null, null, ' ');
	assertEquals(0, shortNames.size());
    }
    
    @Test
    public void emptyTest() {
	List<String> names = new ArrayList<String>();
	List<String> shortNames = Util.generateShortNames(names, null, ' ');
	assertEquals(0, shortNames.size());
    }
    
    @Test
    public void identTest() {
	List<String> names = new ArrayList<String>();
	names.add("b");
	names.add("a");
	names.add("c");
	List<String> shortNames = Util.generateShortNames(names, null, ' ');
	assertEquals(names.size(), shortNames.size());
	assertEquals("b", shortNames.get(0));
	assertEquals("a", shortNames.get(1));
	assertEquals("c", shortNames.get(2));
    }
    
    @Test
    public void simpleTest() {
	List<String> names = new ArrayList<String>();
	names.add("http://schema.org/name");
	names.add("http://schema.org/url");
	names.add("http://schema.org/tracks");
	names.add("http://schema.org/duration");
	List<String> shortNames = Util.generateShortNames(names, null, '/');
	assertEquals(names.size(), shortNames.size());
	assertEquals("name", shortNames.get(0));
	assertEquals("url", shortNames.get(1));
	assertEquals("tracks", shortNames.get(2));
	assertEquals("duration", shortNames.get(3));
    }
    
    @Test
    public void test() {
	List<String> names = new ArrayList<String>();
	names.add("http://schema.org/name");
	names.add("http://schema.org/url");
	names.add("http://domain/topicA");
	names.add("http://domain/topicB");
	names.add("http://domain/topic/A");
	names.add("http://domain/topic/B");
	names.add("http://domain/event/A");
	names.add("http://domain/event/B");
	names.add("http://schema.org/excludeMe");
	names.add("http://domain/excludeMe");
	names.add("http://domain/name");
	names.add("http://domain/url");
	List<String> shortNames = Util.generateShortNames(names, Collections.singleton("excludeMe"), '/');
	assertEquals(names.size(), shortNames.size());
	assertEquals("name", shortNames.get(0));
	assertEquals("url", shortNames.get(1));
	assertEquals("topicA", shortNames.get(2));
	assertEquals("topicB", shortNames.get(3));
	assertEquals("A", shortNames.get(4));
	assertEquals("B", shortNames.get(5));
	assertEquals("event/A", shortNames.get(6));
	assertEquals("event/B", shortNames.get(7));
	assertEquals("schema.org/excludeMe", shortNames.get(8));
	assertEquals("domain/excludeMe", shortNames.get(9));
	assertEquals("domain/name", shortNames.get(10));
	assertEquals("domain/url", shortNames.get(11));
    }
    
    @Test
    public void caseTest() {
	List<String> names = new ArrayList<String>();
	names.add("http_schema.org_articleBody");
	names.add("http_schema.org_ArticleBody");
	List<String> shortNames = Util.generateShortNames(names, null, '_');
	assertEquals(names.size(), shortNames.size());
	assertEquals("articleBody", shortNames.get(0));
	assertEquals("ArticleBody", shortNames.get(1));
    }
    
    //TODO
    //@Test
    public void removeVersionTest() {
	assertEquals("path", Util.removeVersion(""));
    }
}
