package com.yahoo.glimmer.indexing.preprocessor;

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
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

public class PredicatePrefixTupleFilterTest {
    private PredicatePrefixTupleFilter filter;
    private Tuple tuple;
    
    @Before
    public void before() {
	filter = new PredicatePrefixTupleFilter();
	filter.setPredicatePrefix("http://schema.org/");
	filter.setFilterNonMatches(true);
	filter.setLowercase(true);
	tuple = new Tuple();
    }
    
    @Test
    public void test() {
	assertFalse(filter.filter(tuple));
	assertNull(tuple.predicate.type);
	assertNull(tuple.predicate.text);
	assertNull(tuple.predicate.n3);
	
	tuple.predicate.type = TupleElement.Type.RESOURCE;
	
	filter("http://not.schema.org/Path", false, "http://not.schema.org/Path");
	// Should be unchanged.
	assertEquals(TupleElement.Type.RESOURCE, tuple.predicate.type);
	
	// Permits rdf:*
	filter("http://www.w3.org/1999/02/22-rdf-syntax-ns#", true, "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
	filter("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", true, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
	filter("http://www.w3.org/1999/02/22-rdf-syntax-nslabel", false, "http://www.w3.org/1999/02/22-rdf-syntax-nslabel");
	
	filter.setRdfTypePrefix("http://not.schema.org/");
	tuple.object.text = "not an schema,org rdfType";
	filter("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", false, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
	tuple.object.text = "http://not.schema.org/Author";
	filter("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", true, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
	
	// Permits rdfs:*
	filter("http://www.w3.org/2000/01/rdf-schema#", true, "http://www.w3.org/2000/01/rdf-schema#");
	filter("http://www.w3.org/2000/01/rdf-schema#comment", true, "http://www.w3.org/2000/01/rdf-schema#comment");
	filter("http://www.w3.org/2000/01/rdf-schemaAAA", false, "http://www.w3.org/2000/01/rdf-schemaAAA");
	
	// Permits owl:*
	filter("http://www.w3.org/2002/07/owl#", true, "http://www.w3.org/2002/07/owl#");
	filter("http://www.w3.org/2002/07/owl#Ontology", true, "http://www.w3.org/2002/07/owl#Ontology");
	filter("http://www.w3.org/2002/07/owl", false, "http://www.w3.org/2002/07/owl");
	
	filter("http://schema.org/path", true, "http://schema.org/path");
	
	filter("http://schema.org/path/property", true, "http://schema.org/property");
	
	filter("http://schema.org/a/longer/path/property", true, "http://schema.org/property");
	
	filter("http://schema.org", false, "http://schema.org");
	
	filter("http://schema.org/", true, "http://schema.org/");
	
	// Should try and do something sensible when given nonsense
	filter("http://schema.org/a/longer/path/nonsense/", true, "http://schema.org/nonsense");
    }
    
    private void filter(String predicateIn, boolean accept, String predicateOut) {
	tuple.predicate.text = predicateIn;
	tuple.predicate.n3 = "<" + predicateIn + ">";
	assertEquals(accept, filter.filter(tuple));
	assertEquals(predicateOut, tuple.predicate.text);
	assertEquals("<" + predicateOut + ">", tuple.predicate.n3);
    }
}
