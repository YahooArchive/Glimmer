package com.yahoo.glimmer.util;

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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Util {
    public static String encodeFieldName(String name) {
	return name.replaceAll("[^a-zA-Z0-9]+", "_");
    }

    public static List<String> generateShortNames(List<String> names, Set<String> exclude, char delimiter) {
	if (names == null || names.isEmpty()) {
	    return Collections.emptyList();
	}
	if (exclude == null) {
	    exclude = Collections.emptySet();
	}

	ArrayList<String> shortNames = new ArrayList<String>(names.size());
	HashSet<String> used = new HashSet<String>(names.size() + exclude.size());
	used.addAll(exclude);

	for (String name : names) {
	    int i = name.length();
	    String shortName;
	    do {
		i = name.lastIndexOf(delimiter, i);
		if (i == -1) {
		    shortName = name;
		    break;
		}
		shortName = name.substring(i + 1);
		i--;
	    } while (used.contains(shortName));
	    if (used.contains(shortName)) {
		throw new IllegalArgumentException("None unique name " + name);
	    }
	    shortNames.add(shortName);
	    used.add(shortName);
	}

	return shortNames;
    }

    public static String removeVersion(String uri) {
	// HACK: second part we shouldn't need
	uri = uri.replaceFirst("[0-9]+\\.[0-9]+\\.[0-9]+\\/", "");
	uri = uri.replaceFirst("[0-9]+_[0-9]_+[0-9]+_", "");
	return uri;
    }
}