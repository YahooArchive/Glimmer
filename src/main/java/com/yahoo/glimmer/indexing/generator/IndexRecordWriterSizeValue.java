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

public class IndexRecordWriterSizeValue implements IndexRecordWriterValue {
    private long document;
    private int size;
    
    public long getDocument() {
	return document;
    }
    public void setDocument(long document) {
	this.document = document;
    }
    
    public int getSize() {
	return size;
    }
    public void setSize(int size) {
	this.size = size;
    }
    
    @Override
    public void write(DataOutput out) throws IOException {
	throw new UnsupportedOperationException();
    }

    @Override
    public void readFields(DataInput in) throws IOException {
	throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
	if (o instanceof IndexRecordWriterSizeValue) {
	    IndexRecordWriterSizeValue that = (IndexRecordWriterSizeValue) o;
	    return document == that.document && size == that.size;
	}
	return false;
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 31 * hash + (int)(document ^ (document >>> 32));
	hash = 31 * hash + size;
	return hash;
    }
    
    @Override
    public int compareTo(IndexRecordWriterValue o) {
	throw new UnsupportedOperationException();
    }
    
    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append(document);
	sb.append("(");
	sb.append(size);
	sb.append(')');
	return sb.toString();
    }
}
