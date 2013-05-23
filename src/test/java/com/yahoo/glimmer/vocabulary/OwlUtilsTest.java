package com.yahoo.glimmer.vocabulary;

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

import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;

public class OwlUtilsTest {

    @Test(expected=IllegalArgumentException.class)
    public void nullGetLocalNameTest() {
	OwlUtils.getLocalName(null);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void emptyGetLocalNameTest() {
	OwlUtils.getLocalName(IRI.create(""));
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void nonUrlGetLocalNameTest() {
	OwlUtils.getLocalName(IRI.create("non-URL"));
    }
    
    @Test
    public void hostGetLocalNameTest() {
	assertEquals("host", OwlUtils.getLocalName(IRI.create("http://host")));
	assertEquals("host", OwlUtils.getLocalName(IRI.create("http://host/")));
	assertEquals("host", OwlUtils.getLocalName(IRI.create("http://host//")));
	assertEquals("host", OwlUtils.getLocalName(IRI.create("http://host#")));
	assertEquals("host", OwlUtils.getLocalName(IRI.create("http://host/#")));
	// This maybe wrong
	assertEquals("?", OwlUtils.getLocalName(IRI.create("http://host/#?")));
    }
    
    @Test
    public void pathGetLocalNameTest() {
	assertEquals("host", OwlUtils.getLocalName(IRI.create("http://host/")));
	assertEquals("p", OwlUtils.getLocalName(IRI.create("http://host/p")));
	assertEquals("path", OwlUtils.getLocalName(IRI.create("http://host/path")));
	assertEquals("path", OwlUtils.getLocalName(IRI.create("http://host/path/")));
	
	// This maybe wrong
	assertEquals("path?p=v", OwlUtils.getLocalName(IRI.create("http://host/path?p=v")));
	assertEquals("subpath", OwlUtils.getLocalName(IRI.create("http://host/path/subpath/#")));
    }
    
    @Test
    public void fragmentGetLocalNameTest() {
	assertEquals("path", OwlUtils.getLocalName(IRI.create("http://host/path#")));
	assertEquals("f", OwlUtils.getLocalName(IRI.create("http://host/path#f")));
	assertEquals("fragment", OwlUtils.getLocalName(IRI.create("http://host/path#fragment")));
	assertEquals("fragment#", OwlUtils.getLocalName(IRI.create("http://host/path#fragment#")));
    }
}
