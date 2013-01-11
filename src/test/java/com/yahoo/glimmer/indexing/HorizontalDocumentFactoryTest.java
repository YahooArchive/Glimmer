package com.yahoo.glimmer.indexing;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import it.unimi.dsi.lang.MutableString;

import java.io.IOException;

import org.junit.Test;

public class HorizontalDocumentFactoryTest extends AbstractDocumentFactoryTest {
    
    @Test
    public void withContextTest() throws IOException {
	HorizontalDocumentFactory.setupConf(conf, true, null, "@");
	HorizontalDocumentFactory factory = (HorizontalDocumentFactory)RDFDocumentFactory.buildFactory(conf);
	factory.setResourcesHashFunction(resourcesHash);
	assertEquals(4, factory.getFieldCount());
	
	HorizontalDocument document = (HorizontalDocument)factory.getDocument();
	document.setContent(CONTENT_BYTES, CONTENT_BYTES.length);
	
	assertEquals("http://subject/", document.getSubject());
	
	MutableString word = new MutableString();
	MutableString nonWord = new MutableString();
	
	// token, predicate & context are positional/parallel indexes.
	WordArrayReader objectReader = (WordArrayReader)document.content(0);
	WordArrayReader predicateReader = (WordArrayReader)document.content(1);
	WordArrayReader contextReader = (WordArrayReader)document.content(2);
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("@45", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@60", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals("@22", word.toString());
	assertEquals("", nonWord.toString());
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("@46", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@61", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals(RDFDocument.NO_CONTEXT, word.toString());
	assertEquals("", nonWord.toString());
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("object", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@62", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals("@55", word.toString());
	assertEquals("", nonWord.toString());
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("3", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@62", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals("@55", word.toString());
	assertEquals("", nonWord.toString());
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("@88", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@63", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals(RDFDocument.NO_CONTEXT, word.toString());
	assertEquals("", nonWord.toString());
	
	assertFalse(objectReader.next(word, nonWord));
	assertFalse(predicateReader.next(word, nonWord));
	assertFalse(contextReader.next(word, nonWord));
	
	WordArrayReader uriReader = (WordArrayReader)document.content(3);
	assertTrue(uriReader.next(word, nonWord));
	assertEquals("subject", word.toString());
	assertEquals("", nonWord.toString());
	assertFalse(uriReader.next(word, nonWord));
	
	context.assertIsSatisfied();
	
	assertEquals(4l, factory.getCounters().findCounter(RDFDocumentFactory.RdfCounters.INDEXED_TRIPLES).getValue());
    }
    
    @Test
    public void withoutContextTest() throws IOException {
	HorizontalDocumentFactory.setupConf(conf, false, null, "@");
	HorizontalDocumentFactory factory = (HorizontalDocumentFactory) RDFDocumentFactory.buildFactory(conf);
	factory.setResourcesHashFunction(resourcesHash);
	assertEquals(4, factory.getFieldCount());
	
	HorizontalDocument document = (HorizontalDocument)factory.getDocument();
	document.setContent(CONTENT_BYTES, CONTENT_BYTES.length);
	assertEquals("http://subject/", document.getSubject());
	
	MutableString word = new MutableString();
	MutableString nonWord = new MutableString();
	
	// token, predicate & context are positional/parallel indexes.
	WordArrayReader objectReader = (WordArrayReader)document.content(0);
	WordArrayReader predicateReader = (WordArrayReader)document.content(1);
	WordArrayReader contextReader = (WordArrayReader)document.content(2);
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("@45", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@60", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals(RDFDocument.NO_CONTEXT, word.toString());
	assertEquals("", nonWord.toString());
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("@46", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@61", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals(RDFDocument.NO_CONTEXT, word.toString());
	assertEquals("", nonWord.toString());
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("object", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@62", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals(RDFDocument.NO_CONTEXT, word.toString());
	assertEquals("", nonWord.toString());
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("3", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@62", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals(RDFDocument.NO_CONTEXT, word.toString());
	assertEquals("", nonWord.toString());
	
	assertTrue(objectReader.next(word, nonWord));
	assertEquals("@88", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(predicateReader.next(word, nonWord));
	assertEquals("@63", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(contextReader.next(word, nonWord));
	assertEquals(RDFDocument.NO_CONTEXT, word.toString());
	assertEquals("", nonWord.toString());
	
	assertFalse(objectReader.next(word, nonWord));
	assertFalse(predicateReader.next(word, nonWord));
	assertFalse(contextReader.next(word, nonWord));
	
	WordArrayReader uriReader = (WordArrayReader)document.content(3);
	assertTrue(uriReader.next(word, nonWord));
	assertEquals("subject", word.toString());
	assertEquals("", nonWord.toString());
	assertFalse(uriReader.next(word, nonWord));
	
	context.assertIsSatisfied();
	
	assertEquals(4l, factory.getCounters().findCounter(RDFDocumentFactory.RdfCounters.INDEXED_TRIPLES).getValue());
    }
}
