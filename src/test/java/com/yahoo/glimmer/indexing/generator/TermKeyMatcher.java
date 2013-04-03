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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import com.yahoo.glimmer.indexing.generator.TermValue.Type;

class TermKeyMatcher extends BaseMatcher<TermKey> {
    private TermKey pair;
    
    public TermKeyMatcher(int index, String term) {
        pair = new TermKey(term, index, null);
    }
    public TermKeyMatcher(int index, String term, Type type, long v1) {
	pair = new TermKey(term, index, new TermValue(type, v1));
    }
    public TermKeyMatcher(int index, String term, Type type, long v1, int v2) {
	pair = new TermKey(term, index, new TermValue(type, v1, v2));
    }
    
    @Override
    public boolean matches(Object object) {
        return pair.equals(object);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(pair.toString());
    }
}