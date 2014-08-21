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

import it.unimi.dsi.io.WordReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;
import org.semanticweb.yars.nx.parser.ParseException;

import com.yahoo.glimmer.indexing.RDFDocumentFactory.IndexType;
import com.yahoo.glimmer.indexing.RDFDocumentFactory.RdfCounters;
import com.yahoo.glimmer.util.BySubjectRecord;
import com.yahoo.glimmer.util.BySubjectRecord.BySubjectRecordException;


// TODO The RDFDocument/RDFDocumentFactory classes could be simpler.  They are as they are because they were derived from MG4J's Document/DocumentFactory interfaces.
public abstract class RDFDocument {
    public static final String NO_CONTEXT = "";

    protected final RDFDocumentFactory factory;
    private BySubjectRecord record = new BySubjectRecord();
    /** Whether we already parsed the document. */
    private boolean parsed;
    /** The cached raw content. */
    private byte[]  contentBytes;
    private int contentLength;
    private Long id;
    private String subject;

    public abstract WordReader content(final int field) throws IOException;

    public abstract IndexType getIndexType();

    protected abstract void ensureParsed_(Iterator<Relation> relations) throws IOException;

    public RDFDocument(RDFDocumentFactory factory) {
	this.factory = factory;
    }

    public void setContent(byte[] bytes, int length) {
	contentBytes = bytes;
	contentLength = length;
	parsed = false;
	id = null;
	subject = null;
    }

    protected void ensureParsed() throws IOException {
	if (parsed) {
	    return;
	}
	parsed = true;

	if (contentLength == 0) {
	    factory.incrementCounter(RdfCounters.EMPTY_LINES, 1);
	    return;
	}
	
	try {
	    record.readFrom(contentBytes, 0, contentLength);
	} catch (BySubjectRecordException e) {
	    factory.incrementCounter(RdfCounters.PARSE_ERROR, 1);
	    // TODO How to fail?
	}
	
	id = record.getId();
	subject = record.getSubject();

	List<Relation> relations = new ArrayList<Relation>();
	for (String relationString : record.getRelations()) {
	    try {
		Node[] relationNodes = NxParser.parseNodes(relationString);
		Relation relation = new Relation(relationNodes);
		relations.add(relation);
	    } catch (ParseException e) {
		System.err.println("Parsing failed for " + subject + ": " + e.getMessage() + "Content was: \n" + relationString);
		return;
	    }
	}
	if (relations.isEmpty()) {
	    factory.incrementCounter(RdfCounters.EMPTY_DOCUMENTS, 1);
	    return;
	}

	ensureParsed_(relations.iterator());
    }

    public long getId() {
	try {
	    ensureParsed();
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
	return id;
    }

    public String getSubject() {
	try {
	    ensureParsed();
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
	return subject;
    }

    public String toString() {
	return getSubject().toString();
    }
    
    protected static class Relation {
	private final Node[] nodes;
	
	/**
	 * 
	 * @param nodes The array of Nodes excluding the subject.
	 */
	public Relation(Node[] nodes) {
	    this.nodes = nodes;
	}
	
	public Node getPredicate() {
	    return nodes[0];
	}
	public Node getObject() {
	    return nodes[1];
	}
	public Node getContext() {
	    if (nodes.length > 2) {
		return nodes[2];
	    }
	    return null;
	}
    }
}