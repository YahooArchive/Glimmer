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

/**
 * A term processor that returns true if the term looks like a Resource
 * Reference. Eg, The prefix string concatenated with a number.
 * 
 */

public class ResourceRefTermProcessor implements TermProcessor {
    private static final long serialVersionUID = 1L;

    public static enum PropertyKeys {
	REF_PREFIX
    };

    private String refPrefix = "@";

    // TODO How to set the Ref Prefix when loading the index?
    public void setRefPrefix(String refPrefix) {
	this.refPrefix = refPrefix;
    }

    private static ResourceRefTermProcessor INSTANCE = new ResourceRefTermProcessor();

    private ResourceRefTermProcessor() {
    }

    public final static TermProcessor getInstance() {
	return INSTANCE;
    }

    public boolean processTerm(final MutableString term) {
	if (term.length() > refPrefix.length()) {
	    char[] chars = term.array();
	    int i = 0;
	    while (i < refPrefix.length()) {
		if (chars[i] != refPrefix.charAt(i)) {
		    return false;
		}
		i++;
	    }
	    while (i < term.length()) {
		if (!Character.isDigit(chars[i++])) {
		    return false;
		}
	    }
	    return true;
	}
	return false;
    }

    public boolean processPrefix(final MutableString prefix) {
	return processTerm(prefix);
    }

    private Object readResolve() {
	return this;
    }

    public String toString() {
	return this.getClass().getName();
    }

    public String toSpec() {
	return toString();
    }

    public ResourceRefTermProcessor copy() {
	return this;
    }
}
