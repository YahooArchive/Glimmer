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

import org.semanticweb.yars.nx.Resource;

public class PredicatePrefixTupleFilter implements TupleFilter {
    private static final String RDF_SYNTAX_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String RDF_SCHEMA_NS = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String OWL_NS = "http://www.w3.org/2002/07/owl#";

    private String predicatePrefix;
    private String rdfTypePrefix;
    private boolean filterNonMatches;
    private boolean lowercase;
    private StringBuilder sb;

    public PredicatePrefixTupleFilter() {
	readResolve();
    }

    public void setPredicatePrefix(String predicatePrefix) {
	this.predicatePrefix = predicatePrefix;
	readResolve();
    }

    public void setRdfTypePrefix(String rdfTypePrefix) {
	this.rdfTypePrefix = rdfTypePrefix;
    }

    public void setLowercase(boolean lowercase) {
	this.lowercase = lowercase;
    }

    public void setFilterNonMatches(boolean filterNonMatches) {
	this.filterNonMatches = filterNonMatches;
    }

    // On instantiation by XStream Java's field initialization or default
    // constructor aren't used used.
    // XStream uses the same mechanism as the JDK serialization.
    private Object readResolve() {
	if (predicatePrefix != null) {
	    sb = new StringBuilder(predicatePrefix);
	}
	return this;
    }

    // TODO.  simplify logic...
    @Override
    public boolean filter(Tuple tuple) {
	if (tuple.predicate.type != TupleElement.Type.RESOURCE) {
	    return false;
	}

	String predicateText = tuple.predicate.text;
	if (predicateText.startsWith(RDF_SCHEMA_NS) || predicateText.startsWith(OWL_NS)) {
	    return true;
	}

	if (predicateText.startsWith(RDF_SYNTAX_NS)) {
	    if (rdfTypePrefix != null) {
		if (!tuple.object.text.startsWith(rdfTypePrefix)) {
		    return false;
		}

		String objectText = rewriteResource(tuple.object.text, rdfTypePrefix);
		if (!objectText.equals(tuple.object.text)) {
		    tuple.object.text = objectText.toLowerCase();
		    tuple.object.n3 = new Resource(tuple.object.text).toN3();
		}
	    }
	    return true;
	}

	if (tuple.predicate.text.startsWith(predicatePrefix)) {
	    predicateText = rewriteResource(predicateText, predicatePrefix);
	    if (!predicateText.equals(tuple.predicate.text)) {
		tuple.predicate.text = predicateText.toLowerCase();
		tuple.predicate.n3 = new Resource(tuple.predicate.text).toN3();
	    }
	    return true;
	} else if (!filterNonMatches) {
	    if (lowercase) {
		predicateText = tuple.predicate.text.toLowerCase();
	    }
	} else {
	    return false;
	}

	if (!predicateText.equals(tuple.predicate.text)) {
	    tuple.predicate.text = predicateText;
	    tuple.predicate.n3 = new Resource(predicateText).toN3();
	}
	return true;
    }

    private String rewriteResource(String url, String removeUrlPrefix) {
	String s = url;
	if (lowercase) {
	    s = s.toLowerCase();
	}
	int end = s.length();
	while (s.charAt(--end) == '/') {
	}

	int start = s.lastIndexOf('/', end) + 1;
	if (start > removeUrlPrefix.length()) {
	    // remove path
	    sb.setLength(removeUrlPrefix.length());
	    sb.append(url.substring(start, end + 1));
	    s = sb.toString();
	}
	return s;
    }
}
