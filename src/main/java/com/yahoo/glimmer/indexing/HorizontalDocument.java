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

import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.WordReader;
import it.unimi.dsi.lang.MutableString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.semanticweb.yars.nx.BNode;
import org.semanticweb.yars.nx.Resource;
import org.semanticweb.yars.nx.namespace.RDF;

import com.yahoo.glimmer.indexing.RDFDocumentFactory.IndexType;
import com.yahoo.glimmer.indexing.RDFDocumentFactory.RdfCounters;

/**
 * A RDF document.
 * 
 * <p>
 * We delay the actual parsing until it is actually necessary, so operations
 * like getting the document URI will not require parsing.
 */

class HorizontalDocument extends RDFDocument {
    /*
     * The fields objects, predicates & contexts are used in 'parallel' So the
     * value at index I from the three lists refers to the same relation. If the
     * object is a Resource or BNode it's single hash value is put in the
     * objects list at index I. If the object is a Literal with n terms. The
     * terms are put in the objects list at indexes I to I+n-1
     */
    // Literals objects are the terms.
    // Resource/NBode objects are the hash values.
    private List<String> objects = new ArrayList<String>();
    // Predicates holds the encoded urls http_www_blar_com_something
    private List<String> predicates = new ArrayList<String>();
    // Contexts are the hash values.
    private List<String> contexts = new ArrayList<String>();

    // subjectTokens are tokens extracted from the subject Resource/BNode
    private List<String> subjectTokens = new ArrayList<String>();

    protected HorizontalDocument(HorizontalDocumentFactory factory) {
	super(factory);
    }

    @Override
    public IndexType getIndexType() {
	return IndexType.HORIZONTAL;
    }

    protected void ensureParsed_(Iterator<Relation> relations) throws IOException {
	objects.clear();
	predicates.clear();
	contexts.clear();
	subjectTokens.clear();

	// Index subject tokens
	// We index the BNode id. Do we need it?
	String subject = getSubject();
	FastBufferedReader fbr;
	// remove http/https or _:
	int startAt = subject.indexOf(':');
	
	if (startAt < 0) {
	    fbr = new FastBufferedReader(subject.toCharArray());
	} else {
	    startAt++;
	    fbr = new FastBufferedReader(subject.toCharArray(), startAt, subject.length() - startAt);
	}
	MutableString word = new MutableString();
	MutableString nonWord = new MutableString();
	while (fbr.next(word, nonWord)) {
	    if (word != null && !word.equals("")) {
		if (CombinedTermProcessor.getInstance().processTerm(word)) {
		    subjectTokens.add(word.toString().toLowerCase());
		}
	    }
	}
	fbr.close();

	while (relations.hasNext()) {
	    Relation relation = relations.next();
	    String predicate = relation.getPredicate().toString();

	    // Check if prefix is on blacklist
	    if (RDFDocumentFactory.isOnPredicateBlacklist(predicate.toLowerCase())) {
		factory.incrementCounter(RdfCounters.BLACKLISTED_TRIPLES, 1);
		continue;
	    }
	    
	    if (predicate.equals(RDF.TYPE.toString())) {
		factory.incrementCounter(RdfCounters.RDF_TYPE_TRIPLES, 1);
	    }
	    
	    String predicateId = factory.lookupResource(predicate, true);
	    if (predicateId == null) {
		throw new IllegalStateException("Predicate " + predicate + " not in resources hash function!");
	    }

	    String contextId = NO_CONTEXT;
	    if (factory.isWithContexts() && relation.getContext() != null) {
		if (relation.getContext() instanceof Resource || relation.getContext() instanceof BNode) {
		    contextId = factory.lookupResource(relation.getContext().toString(), true);
		    if (contextId == null) {
			throw new IllegalStateException("Context " + relation.getContext() + " not in resources hash function!");
		    }
		} else {
		    throw new IllegalStateException("Context " + relation.getContext() + " is not a Resource.");
		}
	    }

	    if (relation.getObject() instanceof Resource || relation.getObject() instanceof BNode) {
		String objectId = factory.lookupResource(relation.getObject().toString(), true);
		if (objectId == null) {
		    throw new IllegalStateException("Object " + relation.getObject() + " not in resources hash function!");
		}
		objects.add(objectId);
		predicates.add(predicateId);
		contexts.add(contextId);
	    } else {
		String object = relation.getObject().toString();
		// Iterate over the words of the value
		fbr = new FastBufferedReader(object.toCharArray());
		while (fbr.next(word, nonWord)) {
		    if (word != null && !word.equals("")) {
			if (CombinedTermProcessor.getInstance().processTerm(word)) {
			    // Lowercase terms
			    objects.add(word.toString());

			    // Preserve casing for properties and
			    // contexts
			    predicates.add(predicateId);
			    contexts.add(contextId);
			}

		    }
		}
		fbr.close();
	    }

	    factory.incrementCounter(RdfCounters.INDEXED_TRIPLES, 1);
	}
    }

    @Override
    public WordReader content(final int field) throws IOException {
	factory.ensureFieldIndex(field);
	ensureParsed();
	switch (field) {
	case 0:
	    return new WordArrayReader(objects);
	case 1:
	    return new WordArrayReader(predicates);
	case 2:
	    return new WordArrayReader(contexts);
	case 3:
	    return new WordArrayReader(subjectTokens);
	default:
	    throw new IllegalArgumentException();
	}
    }
}