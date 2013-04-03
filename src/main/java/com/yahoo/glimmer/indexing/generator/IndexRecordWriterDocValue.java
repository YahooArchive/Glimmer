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

public class IndexRecordWriterDocValue implements IndexRecordWriterValue {
    private long document;
    private int[] occurrences;
    private int occurrenceCount;

    public IndexRecordWriterDocValue() {
	occurrences = new int[4096];
    }
    public IndexRecordWriterDocValue(int initialOccurrencesSize) {
	occurrences = new int[initialOccurrencesSize];
    }
    
    public long getDocument() {
	return document;
    }
    public void setDocument(long document) {
	this.document = document;
    }

    /**
     * @param occurrence
     *            to add at next to end of list
     */
    public void addOccurrence(int occurrence) {
	if (occurrenceCount == occurrences.length) {
	    // expand array.
	    int[] newOccurrences = new int[occurrences.length * 2];
	    System.arraycopy(occurrences, 0, newOccurrences, 0, occurrences.length);
	    occurrences = newOccurrences;
	}
	occurrences[occurrenceCount++] = occurrence;
    }

    public boolean hasOccurrence() {
	return occurrenceCount > 0;
    }

    public int getOccurrenceCount() {
	return occurrenceCount;
    }

    public int[] getOccurrences() {
	return occurrences;
    }
    
    public void clearOccerrences() {
	occurrenceCount = 0;
    }

    @Override
    public void readFields(DataInput in) throws IOException {
	throw new UnsupportedOperationException();
    }

    @Override
    public void write(DataOutput out) throws IOException {
	throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
	if (o instanceof IndexRecordWriterDocValue) {
	    IndexRecordWriterDocValue that = (IndexRecordWriterDocValue) o;
	    if (document == that.document && occurrenceCount == that.occurrenceCount) {
		for (int i = 0; i < occurrenceCount; i++) {
		    if (occurrences[i] != that.occurrences[i]) {
			return false;
		    }
		}
		return true;
	    }
	}
	return false;
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 31 * hash + (int)(document ^ (document >>> 32));
	hash = 31 * hash + occurrenceCount;
	for (int i = 0; i < occurrenceCount; i++) {
	    hash = 31 * hash + occurrences[i];
	}
	return hash;
    }

    @Override
    public int compareTo(IndexRecordWriterValue o) {
	throw new UnsupportedOperationException();
    }

    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append(document);
	sb.append(" (");
	for (int i = 0; i < occurrenceCount; i++) {
	    if (i > 0) {
		sb.append(',');
	    }
	    sb.append(occurrences[i]);
	}
	sb.append(')');
	return sb.toString();
    }
}
