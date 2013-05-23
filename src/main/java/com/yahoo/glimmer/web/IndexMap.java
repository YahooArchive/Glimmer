package com.yahoo.glimmer.web;

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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.yahoo.glimmer.query.Context;
import com.yahoo.glimmer.query.RDFIndex;
import com.yahoo.glimmer.query.RDFIndex.RDFIndexException;

public class IndexMap extends HashMap<String, RDFIndex> {
    private static final long serialVersionUID = -2657141430471199765L;

    private String configFilename;

    public String getConfigFilename() {
	return configFilename;
    }

    public void setConfigFilename(String configFilename) {
	this.configFilename = configFilename;
    }

    @PostConstruct
    public void load() throws IOException, RDFIndexException {
	Context context = new Context(configFilename);

	File multiIndexPath = context.getMultiIndexPath();
	if (multiIndexPath == null) {
	    // Single index, index.path property must be present
	    String indexName = context.getKbRootPath().getName();
	    RDFIndex index = new RDFIndex(indexName, context);
	    put(indexName, index);
	} else {
	    // Multiple indices under a root directory
	    // In this case the config file is a template that we copy and configure for
	    // each index
	    if (!multiIndexPath.exists()) {
		throw new RuntimeException("The multiindex path " + multiIndexPath + " does not exist.");
	    }
	    if (!multiIndexPath.isDirectory()) {
		throw new RuntimeException("The multiindex path " + multiIndexPath + " is not a directory.");
	    }
	    String multiIndexDirPrefix = context.getMultiIndexDirPrefix();
	    for (File file : multiIndexPath.listFiles()) {
		String filename = file.getName();
		if (file.isDirectory() && filename.matches(multiIndexDirPrefix + "\\w+")) {
		    String indexName = filename.substring(multiIndexDirPrefix.length());
		    Context contextCopy = new Context(context);
		    contextCopy.setKbRootPath(file);
		    RDFIndex index = new RDFIndex(indexName, contextCopy);
		    put(indexName, index);
		}
	    }
	    if (isEmpty()) {
		throw new RuntimeException("No indexed directories found in " + multiIndexPath.getPath() + " with prefixes of " + multiIndexDirPrefix);
	    }
	}
    }

    @PreDestroy
    protected void unload() throws Throwable {
	for (RDFIndex index : values()) {
	    if (index != null) {
		index.destroy();
	    }
	}
    }
}
