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
import it.unimi.di.big.mg4j.index.DowncaseTermProcessor;
import it.unimi.di.big.mg4j.index.TermProcessor;
import it.unimi.di.big.mg4j.index.snowball.PorterStemmer;

/**
 * A term processor that combines other term processors.
 * 
 */

public class CombinedTermProcessor implements TermProcessor {
    private static final long serialVersionUID = 1L;

    private static CombinedTermProcessor INSTANCE = new CombinedTermProcessor();

    private final static TermProcessor RESOURCE_REF_TERM_PROCESSOR = ResourceRefTermProcessor.getInstance();
    private final static TermProcessor[] TERM_PROCESSORS = { NonWordTermProcessor.getInstance(), DowncaseTermProcessor.getInstance(),
	    StopwordTermProcessor.getInstance(), new PorterStemmer() };

    private CombinedTermProcessor() {
    }

    public final static TermProcessor getInstance() {
	return INSTANCE;
    }

    public boolean processTerm(final MutableString term) {
	// If the term looks like a resource ref accept it.
	if (RESOURCE_REF_TERM_PROCESSOR.processTerm(term)) {
	    return true;
	}
	for (TermProcessor tp : TERM_PROCESSORS) {
	    if (!tp.processTerm(term)) {
		return false;
	    }
	}
	return true;
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

    public CombinedTermProcessor copy() {
	return this;
    }
}
