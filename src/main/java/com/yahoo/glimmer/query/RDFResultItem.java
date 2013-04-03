package com.yahoo.glimmer.query;

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

import java.util.ArrayList;
import java.util.List;

public class RDFResultItem {
    public static class Relation {
	private final String predicate;
	private final String object;
	private final Long subjectIdOfObject;
	private final String context;
	private final boolean indexed;
	private final String label;
	
	private Relation(String predicate, String object, Long subjectIdOfObject, String context, boolean indexed, String label) {
	    this.predicate = predicate;
	    this.object = object;
	    this.subjectIdOfObject = subjectIdOfObject;
	    this.context = context;
	    this.indexed = indexed;
	    this.label = label;
	}

	public String getPredicate() {
	    return predicate;
	}

	public String getObject() {
	    return object;
	}
	
	public Long getSubjectIdOfObject() {
	    return subjectIdOfObject;
	}

	public String getContext() {
	    return context;
	}

	public boolean isIndexed() {
	    return indexed;
	}

	public String getLabel() {
	    return label;
	}
	
	@Override
	public String toString() {
	    return toString(new StringBuilder()).toString();
	}
	public StringBuilder toString(StringBuilder sb) {
	    sb.append(predicate);
	    sb.append(' ');
	    sb.append(object);
	    sb.append(' ');
	    sb.append(context);
	    sb.append(' ');
	    sb.append(indexed);
	    sb.append(' ');
	    sb.append(label);
	    return sb;
	}
    }

    // The Subject which is the same as the title and URI.
    private String subject;
    private long subjectId;
    private double score;
    private final List<Relation> relations = new ArrayList<Relation>();

    // Label of this object from rdfs:label or any property ending with label
    public String label;

    public String getSubject() {
	return subject;
    }
    public void setSubject(String subject) {
	this.subject = subject;
    }
    public long getSubjectId() {
	return subjectId;
    }
    public void setSubjectId(long subjectId) {
	this.subjectId = subjectId;
    }
    
    public double getScore() {
	return score;
    }
    public void setScore(double score) {
	this.score = score;
    }
    
    public List<Relation> getRelations() {
	return relations;
    }
    public void addRelation(String predicate, String object, Long subjectIdOfObject, String context, boolean indexed, String label) {
	relations.add(new Relation(predicate, object, subjectIdOfObject, context, indexed, label));
    }

    public String getLabel() {
	return label;
    }
    public void setLabel(String label) {
	this.label = label;
    }

    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append(subject);
	sb.append('(');
	sb.append(subjectId);
	sb.append(") score:");
	sb.append(score);
	sb.append('\n');
	for (Relation relation : relations) {
	    sb.append('\t');
	    relation.toString(sb);
	    sb.append('\n');
	}
	return sb.toString();
    }
}
