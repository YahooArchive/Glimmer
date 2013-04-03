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

import com.yahoo.glimmer.indexing.RDFDocumentFactory.IndexType;

public class VerticalDocumentFactoryTest extends AbstractDocumentFactoryTest {
    @Test
    public void test1() throws IOException {
	VerticalDocumentFactory.setupConf(conf, IndexType.VERTICAL, true, null, "@", new String[] { "http://predicate/1", "http://predicate/2", "http://predicate/3" });

	resourcesHash.put("http://context/1", 55l);
	resourcesHash.put("http://object/1", 45l);
	resourcesHash.put("http://object/2", 46l);

	VerticalDocumentFactory factory = (VerticalDocumentFactory) RDFDocumentFactory.buildFactory(conf);
	factory.setResourcesHashFunction(resourcesHash);
	assertEquals(3, factory.getFieldCount());
	VerticalDocument document = (VerticalDocument) factory.getDocument();
	document.setContent(CONTENT_BYTES, CONTENT_BYTES.length);


	assertEquals("http://subject/", document.getSubject());
	assertEquals(33, document.getId());

	MutableString word = new MutableString();
	MutableString nonWord = new MutableString();

	WordArrayReader reader = (WordArrayReader) document.content(0);
	assertTrue(reader.next(word, nonWord));
	assertEquals("@45", word.toString());
	assertEquals("", nonWord.toString());
	assertFalse(reader.next(word, nonWord));

	reader = (WordArrayReader) document.content(1);
	assertTrue(reader.next(word, nonWord));
	assertEquals("@46", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(reader.next(word, nonWord));
	assertEquals("@47", word.toString());
	assertEquals("", nonWord.toString());
	assertFalse(reader.next(word, nonWord));

	reader = (WordArrayReader) document.content(2);
	assertTrue(reader.next(word, nonWord));
	assertEquals("object", word.toString());
	assertEquals("", nonWord.toString());
	assertTrue(reader.next(word, nonWord));
	assertEquals("3", word.toString());
	assertEquals("", nonWord.toString());
	assertFalse(reader.next(word, nonWord));

	context.assertIsSatisfied();

	assertEquals(4l, factory.getCounters().findCounter(RDFDocumentFactory.RdfCounters.INDEXED_TRIPLES).getValue());
    }
}
