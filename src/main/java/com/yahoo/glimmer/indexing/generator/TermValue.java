package com.yahoo.glimmer.indexing.generator;

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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.WritableComparable;

/**
 * The value that is passed between the mapper and reducer.
 * 
 * Can represent different thing depending the type
 * 
 * @author tep
 */
public class TermValue implements WritableComparable<TermValue>, Cloneable {
    /**
     * TermValue Type. The order of this enum is important as it determines the
     * order in which the values are give in the Reducers values Iterable.
     */
    public enum Type {
	/**
	 * For every doc a DOC_STATS is written. v1 = term occurrence count in
	 * doc. v2 = position of last term occurrence.
	 */
	DOC_STATS,

	/**
	 * For each unique term in a doc, a PREDICATE_ID is written. v1 = the id
	 * of the index for the term.
	 */
	INDEX_ID,

	/**
	 * For every term in every doc an OCCURRENCE is written. v1 = doc id, v2
	 * = terms position.
	 */
	OCCURRENCE;
    }

    private Type type;
    private long v1;
    private int v2;

    public TermValue(Type type, long v1) {
	if (type != Type.INDEX_ID) {
	    throw new IllegalArgumentException("Type " + type + " is not value with 1 arg");
	}
	this.type = type;
	this.v1 = v1;
    }

    public TermValue(Type type, long v1, int v2) {
	if (type != Type.DOC_STATS && type != Type.OCCURRENCE) {
	    throw new IllegalArgumentException("Type " + type + " is not value with 2 args");
	}
	this.type = type;
	this.v1 = v1;
	this.v2 = v2;
    }

    public TermValue() {
    }

    public TermValue(TermValue that) {
	set(that);
    }

    public void set(TermValue that) {
	type = that.type;
	v1 = that.v1;
	v2 = that.v2;
    }

    public Type getType() {
	return type;
    }

    public long getV1() {
	return v1;
    }

    public int getV2() {
	return v2;
    }

    public void readFields(DataInput in) throws IOException {
	type = Type.values()[in.readInt()];
	v1 = in.readLong();
	v2 = in.readInt();
    }

    public void write(DataOutput out) throws IOException {
	out.writeInt(type.ordinal());
	out.writeLong(v1);
	out.writeInt(v2);
    }

    @Override
    public boolean equals(Object o) {
	if (o instanceof TermValue) {
	    TermValue that = (TermValue) o;
	    return type == that.type && v1 == that.v1 && v2 == that.v2;
	}
	return false;
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 31 * hash + type.hashCode();
	hash = 31 * hash + (int)(v1 ^ (v1 >>> 32));
	hash = 31 * hash + v2;
	return hash;
    }

    public String toString() {
	return type.name() + "(" + v1 + "," + v2 + ")";
    }

    public int compareTo(TermValue that) {
	long i = type.compareTo(that.type);
	if (i != 0) {
	    return (int)i;
	}
	i = v1 - that.v1;
	if (i != 0) {
	    return i > 0 ? 1 : -1;
	}
	i = v2 - that.v2;
	return (int)i;
    }
}