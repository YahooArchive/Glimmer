package com.yahoo.glimmer.web;

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

import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.query.SelectedInterval;
import it.unimi.di.big.mg4j.query.nodes.Query;
import it.unimi.di.big.mg4j.query.nodes.QueryBuilderVisitorException;
import it.unimi.di.big.mg4j.search.score.DocumentScoreInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;

import com.yahoo.glimmer.query.QueryLogger;
import com.yahoo.glimmer.query.RDFIndex;
import com.yahoo.glimmer.query.RDFQueryResult;
import com.yahoo.glimmer.query.RDFResultItem;
import com.yahoo.glimmer.util.BySubjectRecord;
import com.yahoo.glimmer.util.Util;

/**
 * Wraps the details of doing a query against an RDFIndex.
 * 
 */
public class Querier {
    private final static Logger LOGGER = Logger.getLogger(Querier.class);
    private static final String DEFAULT_CONTEXT = "default:";
    private static final int CACHE_SIZE = 10000;

    private final Map<String, Long> objectsSubjectsIdCache;
    private final Map<Long, String> objectLabelCache;

    private QueryLogger queryLogger = new QueryLogger();

    public Querier() {
	LinkedHashMap<String, Long> idCache = new LinkedHashMap<String, Long>(CACHE_SIZE + 1, 1.1f, true) {
	    private static final long serialVersionUID = -8171861525079261380L;

	    protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> eldest) {
		return size() > CACHE_SIZE;
	    };
	};
	objectsSubjectsIdCache = Collections.synchronizedMap(idCache);

	LinkedHashMap<Long, String> labelCache = new LinkedHashMap<Long, String>(CACHE_SIZE + 1, 1.1f, true) {
	    private static final long serialVersionUID = -6916960713013021549L;

	    protected boolean removeEldestEntry(java.util.Map.Entry<Long, String> eldest) {
		return size() > CACHE_SIZE;
	    };
	};
	objectLabelCache = Collections.synchronizedMap(labelCache);
    }

    public RDFQueryResult doQuery(RDFIndex index, Query query, int startItem, int maxNumItems, boolean deref, Integer objectLengthLimit) throws QueryBuilderVisitorException, IOException {
	if (startItem < 0 || maxNumItems < 0 || maxNumItems > 10000) {
	    throw new IllegalArgumentException("Bad item range - start:" + startItem + " maxNumItems:" + maxNumItems);
	}

	ObjectArrayList<DocumentScoreInfo<Reference2ObjectMap<Index, SelectedInterval[]>>> results = new ObjectArrayList<DocumentScoreInfo<Reference2ObjectMap<Index, SelectedInterval[]>>>();

	queryLogger.start();

	int numResults = index.process(startItem, maxNumItems, results, query);

	if (results.size() > maxNumItems) {
	    results.size(maxNumItems);
	}

	ObjectArrayList<RDFResultItem> resultItems = new ObjectArrayList<RDFResultItem>();
	if (!results.isEmpty()) {
	    for (int i = 0; i < results.size(); i++) {
		DocumentScoreInfo<Reference2ObjectMap<Index, SelectedInterval[]>> dsi = results.get(i);
		LOGGER.debug("Intervals for item " + i);
		LOGGER.debug("score " + dsi.score);
		RDFResultItem item = createRdfResultItem(index, dsi.document, dsi.score, deref, objectLengthLimit);
		if (item == null) {
		    LOGGER.error("Document id " + dsi.document + " isn't in collection(or has null content).");
		} else {
		    resultItems.add(item);
		}
	    }
	}

	long time = queryLogger.endQuery(query.toString(), numResults);
	RDFQueryResult result = new RDFQueryResult(null, query != null ? query.toString() : "", numResults, startItem, maxNumItems, resultItems, (int) time);
	return result;
    }

    public RDFQueryResult doQueryForDocId(RDFIndex index, long id, boolean deref, Integer objectLengthLimit) throws IOException {
	queryLogger.start();
	RDFResultItem resultItem = createRdfResultItem(index, id, 1.0d, deref, objectLengthLimit);
	long time = queryLogger.endQuery("getDoc " + Long.toString(id), 1);

	List<RDFResultItem> results;
	if (resultItem != null) {
	    results = Collections.singletonList(resultItem);
	} else {
	    results = Collections.emptyList();
	}
	return new RDFQueryResult("", null, results.size(), 0, 1, results, (int) time);
    }

    private RDFResultItem createRdfResultItem(RDFIndex index, long docId, double score, boolean lookupObjectLabels, Integer objectLengthLimit) throws IOException {
	InputStream docInputStream;
	try {
	    docInputStream = index.getDocumentInputStream(docId);
	} catch (IOException e) {
	    // TODO fix end of stream errors on BZip2.
	    return null;
	}

	BySubjectRecord record = new BySubjectRecord();

	if (!record.parse(new InputStreamReader(docInputStream))) {
	    throw new RuntimeException("Couldn't parse doc with id:" + docId);
	}

	if (docId != record.getId()) {
	    LOGGER.error("Wanted doc id:" + docId + " but got doc id:" + record.getId());
	}

	RDFResultItem item = new RDFResultItem();
	item.setSubjectId(record.getId());
	item.setSubject(record.getSubject());
	item.setScore(score);

	for (String relationString : record.getRelations()) {
	    Node[] predicateObjectContext;
	    try {
		predicateObjectContext = NxParser.parseNodes(relationString);
	    } catch (Exception e) {
		throw new RuntimeException("Error parsing tuple: " + relationString);
	    }

	    String predicate = predicateObjectContext[0].toString();
	    String object = predicateObjectContext[1].toString();
	    if (objectLengthLimit != null && object.length() > objectLengthLimit) {
		object = object.substring(0, objectLengthLimit) + "...";
	    }
	    String context;
	    if (predicateObjectContext.length > 2) {
		context = predicateObjectContext[2].toString();
	    } else {
		context = DEFAULT_CONTEXT;
	    }
	    boolean indexed = index.getIndexedPredicates().contains(Util.encodeFieldName(predicate));

	    String label = null;
	    // if predicate is an rdfs:label or woo:label, assign the object as
	    // the
	    // items label
	    // TODO. Consider ...name too.
	    if (predicate.endsWith("label") || predicate.endsWith("name")) {
		item.setLabel(object);
		label = object;
	    }

	    Long subjectIdOfObject;

	    if (objectsSubjectsIdCache.containsKey(object)) {
		subjectIdOfObject = objectsSubjectsIdCache.get(object);
	    } else {
		subjectIdOfObject = index.getSubjectId(object);
		objectsSubjectsIdCache.put(object, subjectIdOfObject);
	    }

	    if (label == null && subjectIdOfObject != null && lookupObjectLabels) {
		if (objectLabelCache.containsKey(subjectIdOfObject)) {
		    label = objectLabelCache.get(subjectIdOfObject);
		} else {
		    // If the object is also a subject Resource/BNode this
		    // will return that subjects id with is the same as the
		    // docId. Parse the subject doc that this object refers
		    // too..
		    RDFResultItem objectItem = createRdfResultItem(index, subjectIdOfObject, 0.0d, false, null);
		    if (objectItem != null) {
			label = objectItem.getLabel();
		    }
		    objectLabelCache.put(subjectIdOfObject, label);
		}
	    }

	    item.addRelation(predicate, object, subjectIdOfObject, context, indexed, label);
	}

	docInputStream.close();

	return item;
    }
}
