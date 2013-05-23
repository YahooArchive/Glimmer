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

class VerticalDocument extends RDFDocument {
    private List<List<String>> fields = new ArrayList<List<String>>();

    protected VerticalDocument(VerticalDocumentFactory factory) {
	super(factory);
	fields = new ArrayList<List<String>>(factory.getFieldCount());
	while (fields.size() < factory.getFieldCount()) {
	    fields.add(new ArrayList<String>());
	}
    }

    @Override
    public IndexType getIndexType() {
	return IndexType.VERTICAL;
    }

    protected void ensureParsed_(Iterator<Relation> relations) throws IOException {
	// clear fields
	for (List<String> field : fields) {
	    field.clear();
	}

	while (relations.hasNext()) {
	    Relation relation = relations.next();
	    String predicate = relation.getPredicate().toString();
	    // Check if prefix is on blacklist
	    if (RDFDocumentFactory.isOnPredicateBlacklist(predicate)) {
		factory.incrementCounter(RdfCounters.BLACKLISTED_TRIPLES, 1);
		continue;
	    }
	    // Determine whether we need to index, and the field
	    int fieldIndex = factory.getFieldIndex(predicate);
	    if (fieldIndex == -1) {
		factory.incrementCounter(RdfCounters.UNINDEXED_PREDICATE_TRIPLES, 1);
		continue;
	    }
	    
	    List<String> fieldForPredicate = fields.get(fieldIndex);

	    if (relation.getObject() instanceof Resource || relation.getObject() instanceof BNode) {
		// Encode the resource URI or bnode ID using the resources hash
		String objectId = factory.lookupResource(relation.getObject().toString(), true);
		if (objectId == null) {
		    throw new IllegalStateException("Object " + relation.getObject().toString() + " not in resources hash function!");
		}
		fieldForPredicate.add(objectId);
		
		if (predicate.equals(RDF.TYPE.toString())) {
		    // If the predicate is RDF type and the object is a Resource we use the ontology(if set)
		    // to also index all super types.
		    factory.incrementCounter(RdfCounters.RDF_TYPE_TRIPLES, 1);
		    
		    for (String ancestor : factory.getAncestors(relation.getObject().toString())) {
			String ancestorId = factory.lookupResource(ancestor, true);
			if (ancestorId == null) {
			    throw new IllegalStateException("Ancestor(" + ancestor + ") of " + relation.getObject().toString() + " not in resources hash function!. Was the same ontology used with the PrepTool?");
			}
			fieldForPredicate.add(ancestorId);
		    }
		}
	    } else {
		String object = relation.getObject().toString();

		// Iterate over the words of the value
		FastBufferedReader fbr = new FastBufferedReader(object.toCharArray());
		MutableString word = new MutableString();
		MutableString nonWord = new MutableString();
		while (fbr.next(word, nonWord)) {
		    if (word != null && !word.equals("")) {
			if (CombinedTermProcessor.getInstance().processTerm(word)) {
			    fieldForPredicate.add(word.toString());
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
	return new WordArrayReader(fields.get(field));
    }
}