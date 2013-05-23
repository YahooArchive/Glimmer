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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Jave Bean wire object containing RDFIndex stats.
 * 
 * @author tep
 */
public class RDFIndexStatistics {
    public static class ClassStat {
	private final String localName;
	private final int count;
	private String label;
	private Set<String> properties;
	private Set<String> children;

	public ClassStat(String localName, int count) {
	    this.localName = localName;
	    this.count = count;
	}

	public String getLocalName() {
	    return localName;
	}

	public int getCount() {
	    return count;
	}

	public String getLabel() {
	    return label;
	}

	public void setLabel(String label) {
	    this.label = label;
	}

	public Set<String> getProperties() {
	    return properties;
	}

	public void addProperty(String property) {
	    if (properties == null) {
		properties = new TreeSet<String>();
	    }
	    properties.add(property);
	}

	public void addProperties(Set<String> propertiesToAdd) {
	    if (propertiesToAdd != null) {
		if (properties == null) {
		    properties = new TreeSet<String>();
		}
		properties.addAll(propertiesToAdd);
	    }
	}

	public Set<String> getChildren() {
	    return children;
	}

	public boolean addChild(String child) {
	    if (children == null) {
		children = new TreeSet<String>();
	    }
	    return children.add(child);
	}
    }

    private String nameSpace;
    private Map<String, String> fields;
    private Collection<String> rootClasses;
    private Map<String, ClassStat> classes;
    private Map<String, Integer> properties;

    public String getNameSpace() {
	return nameSpace;
    }

    public void setNameSpace(String nameSpace) {
	this.nameSpace = nameSpace;
    }

    public Map<String, String> getFields() {
	return fields;
    }

    public void setFields(Map<String, String> fields) {
	this.fields = fields;
    }

    public Collection<String> getRootClasses() {
	return rootClasses;
    }

    public void addRootClass(String rootClass) {
	if (rootClasses == null) {
	    rootClasses = new ArrayList<String>();
	}
	rootClasses.add(rootClass);
    }

    public Map<String, ClassStat> getClasses() {
	return classes;
    }

    public void addClassStat(String name, ClassStat stat) {
	if (classes == null) {
	    classes = new HashMap<String, ClassStat>();
	}
	classes.put(name, stat);
    }

    public Map<String, Integer> getProperties() {
	return properties;
    }

    public void addPropertyStat(String name, Integer count) {
	if (properties == null) {
	    properties = new HashMap<String, Integer>();
	}
	properties.put(name, count);
    }
}
