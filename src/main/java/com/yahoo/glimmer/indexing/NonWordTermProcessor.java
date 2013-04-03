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

/** A term processor that excludes words with non-alphanumeric characters. */

public class NonWordTermProcessor implements TermProcessor {
    private static final long serialVersionUID = 1L;

    private final static NonWordTermProcessor INSTANCE = new NonWordTermProcessor();

    public final static TermProcessor getInstance() {
	return INSTANCE;
    }

    private NonWordTermProcessor() {
    }

    public boolean processTerm(final MutableString term) {
	if (term == null)
	    return false;
	if (!term.toString().matches("[a-zA-Z0-9_]+"))
	    return false;
	return true;
    }

    public boolean processPrefix(final MutableString prefix) {
	return processTerm(prefix);
    }

    private Object readResolve() {
	return INSTANCE;
    }

    public String toString() {
	return this.getClass().getName();
    }

    public NonWordTermProcessor copy() {
	return this;
    }
}
