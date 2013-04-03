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

import it.unimi.dsi.lang.MutableString;
import it.unimi.di.big.mg4j.index.TermProcessor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * A term processor that excludes words on a stop list from being indexed.
 * 
 */

public class StopwordTermProcessor implements TermProcessor {
    private static final long serialVersionUID = 1L;
    private String fileName;
    /** Blacklisted words **/
    private static final String DEFAULT_BLACKLIST_FILENAME = "blacklist.txt";

    private transient Set<String> blacklist = new HashSet<String>();

    private static StopwordTermProcessor INSTANCE = null;

    static {
	try {
	    INSTANCE = new StopwordTermProcessor(DEFAULT_BLACKLIST_FILENAME);
	} catch (IOException e) {
	    throw new RuntimeException(e);
	} catch (ClassNotFoundException e) {
	    throw new RuntimeException(e);
	}
    }

    public StopwordTermProcessor(final String fileName) throws IOException, ClassNotFoundException {
	this.fileName = fileName;
	// Load blacklist
	try {
	    // Loading from JAR
	    BufferedReader reader = new BufferedReader(new InputStreamReader(StopwordTermProcessor.class.getClassLoader().getResourceAsStream(fileName)));
	    String nextLine = "";
	    while ((nextLine = reader.readLine()) != null) {
		blacklist.add(nextLine.trim());
	    }
	    reader.close();
	} catch (Exception e) {
	    // Loading from file system
	    BufferedReader reader;

	    reader = new BufferedReader(new FileReader(fileName));
	    String nextLine = "";
	    while ((nextLine = reader.readLine()) != null) {
		blacklist.add(nextLine.trim());
	    }
	    reader.close();

	}
    }

    public final static TermProcessor getInstance() {
	return INSTANCE;
    }

    public boolean processTerm(final MutableString term) {
	if (term == null)
	    return false;
	if (blacklist.contains(term.toLowerCase()))
	    return false;
	return true;
    }

    public boolean processPrefix(final MutableString prefix) {
	return processTerm(prefix);
    }

    private Object readResolve() {
	return this;
    }

    public String toString() {
	if (fileName == null) {
	    return this.getClass().getName();
	} else {
	    return this.getClass().getName() + "(" + fileName + ")";
	}
    }

    public String toSpec() {
	return toString();
    }

    public StopwordTermProcessor copy() {
	return this;
    }
}
