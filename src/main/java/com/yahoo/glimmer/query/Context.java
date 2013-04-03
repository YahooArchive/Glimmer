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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class Context extends Properties {
    private static final long serialVersionUID = 8416864704849882837L;

    private static final String RESOURCES_ID_PREFIX_KEY = "resourceIdPrefix";
    private static final String ALL_RESOURCES_MAP_KEY = "allResorcesMap";
    private static final String ALL_RESOURCES_SIGNATURE_KEY = "allResorcesSignature";
    private static final String ALL_RESOURCES_KEY = "allResorces";

    private static final String MULTIINDEX_DIR_PREFIX_KEY = "multiindex.dirprefix";
    private static final String MULTIINDEX_PATH_KEY = "multiindex.path";

    private static final String COLLECTION_BASENAME_KEY = "bySubject";
    private static final String VERTICAL_DIR_KEY = "vertical.dir";
    private static final String HORIZONTAL_DIR_KEY = "horizontal.dir";
    private static final String TITLE_LIST_KEY = "title.list";

    private static final String ONTOLOGY_PATH_KEY = "ontology.path";

    private static final String LOAD_INDEXES_IN_MEMORY_TAG = "indexes.in.memory";
    private static final String LOAD_DOC_SIZES_TAG = "load.doc.sizes";

    private static final String DOCUMENT_PRIOR_FIELD_KEY = "prior.field";
    private static final String DOCUMENT_PRIOR_RULES_KEY = "prior.rules";
    private static final String DOCUMENT_PRIORS_KEY = "document.priors";

    private static final String DL_CUTOFF_TAG = "dl.cutoff";
    private static final String KB_ROOT_PATH_KEY = "kb.root";
    private static final String K1_TAG = "k1";
    private static final String B_TAG = "b";
    private static final String MAX_NORM_TAG = "max.norm";

    private static final String W_MATCHES_TAG = "w.matches";
    private static final String WF_IMPORTANT_TAG = "wf.important";
    private static final String WF_NEUTRAL_TAG = "wf.neutral";
    private static final String WF_UNIMPORTANT_TAG = "wf.unimportant";
    private static final String WS_IMPORTANT_TAG = "ws.important";
    private static final String WS_NEUTRAL_TAG = "ws.neutral";
    private static final String WS_UNIMPORTANT_TAG = "ws.unimportant";

    public Context(Context that) {
	super(that);
    }

    public Context(String filename) throws FileNotFoundException, IOException {
	super();
	InputStream fs;
	try {
	    fs = new FileInputStream(filename);
	} catch (FileNotFoundException fnfe) {
	    URL resource = Context.class.getClassLoader().getResource(filename);
	    fs = new FileInputStream(new File(resource.getFile()));
	}
	super.load(fs);
    }
    
    public String getResourceIdPrefix() {
	return getString(RESOURCES_ID_PREFIX_KEY, "@");
    }

    public File getAllResourcesMapFile() {
	return getKbRootRelativeFile(getProperty(ALL_RESOURCES_MAP_KEY, "all.map"));
    }
    
    public File getAllResourcesSignatureFile() {
	return getKbRootRelativeFile(getProperty(ALL_RESOURCES_SIGNATURE_KEY, "all.smap"));
    }

    public File getAllResourcesFile() {
	return getKbRootRelativeFile(getProperty(ALL_RESOURCES_KEY, "all.txt"));
    }
    
    public double getB() {
	return getDouble(B_TAG, 0.75);
    }

    public File getCollectionBasenameFile() {
	return getKbRootRelativeFile(getProperty(COLLECTION_BASENAME_KEY, "bySubject"));
    }

    public File getVerticalIndexDir() {
	return getKbRootRelativeFile(getProperty(VERTICAL_DIR_KEY, "vertical"));
    }

    public File getHorizontalIndexDir() {
	return getKbRootRelativeFile(getProperty(HORIZONTAL_DIR_KEY, "horizontal"));
    }
    
    public File getIndexedPredicatesFile() {
	return getKbRootRelativeFile(getProperty(HORIZONTAL_DIR_KEY, "topPredicates"));
    }

    public double getDlCutoff() {
	return getDouble(DL_CUTOFF_TAG, 10);
    }

    public String getDocumentPriorsField() {
	return getProperty(DOCUMENT_PRIOR_FIELD_KEY);
    }

    public File getDocumentPriorsFile() {
	return getKbRootRelativeFile(getProperty(DOCUMENT_PRIORS_KEY));
    }

    public String getDocumentPriorsRules() {
	return getProperty(DOCUMENT_PRIOR_RULES_KEY);
    }

    public double getK1() {
	return getDouble(K1_TAG, 1.2);
    }

    public boolean getLoadDocumentSizes() {
	return getBoolean(LOAD_DOC_SIZES_TAG, false);
    }

    public boolean getLoadIndexesInMemory() {
	return getBoolean(LOAD_INDEXES_IN_MEMORY_TAG, false);
    }

    public int getMaxNumberOfDieldsNorm() {
	return getInt(MAX_NORM_TAG, 5);
    }

    public String getMultiIndexDirPrefix() {
	return getProperty(MULTIINDEX_DIR_PREFIX_KEY, "index-");
    }

    public File getMultiIndexPath() {
	String pathName = getProperty(MULTIINDEX_PATH_KEY);
	if (pathName == null) {
	    return null;
	}
	return new File(pathName);
    }

    public File getOntoPath() {
	return new File(getProperty(ONTOLOGY_PATH_KEY));
    }

    public File getKbRootPath() {
	String kbRootPath = getProperty(KB_ROOT_PATH_KEY);
	if (kbRootPath == null || kbRootPath.isEmpty()) {
	    return null;
	}
	return new File(kbRootPath);
    }

    public void setKbRootPath(File root) {
	setProperty(KB_ROOT_PATH_KEY, root.getPath());
    }

    public File getTitleListFile() {
	return getKbRootRelativeFile(getProperty(TITLE_LIST_KEY, "subjects"));
    }

    public double getWMatches() {
	return getDouble(W_MATCHES_TAG, 1);
    }

    public double getWfImportant() {
	return getDouble(WF_IMPORTANT_TAG, 1.4);
    }

    public double getWfNeutral() {
	return getDouble(WF_NEUTRAL_TAG, 1);
    }

    public double getWfUnimportant() {
	return getDouble(WF_UNIMPORTANT_TAG, 0.6);
    }

    public double getWsImportant() {
	return getDouble(WS_IMPORTANT_TAG, 1.08);
    }

    public double getWsNeutral() {
	return getDouble(WS_NEUTRAL_TAG, 1);
    }

    public double getWsUnimportant() {
	return getDouble(WS_UNIMPORTANT_TAG, 1);
    }

    private File getKbRootRelativeFile(String filename) {
	if (filename == null) {
	    return null;
	}
	File kbRootPath = getKbRootPath();
	if (kbRootPath == null) {
	    return null;
	}
	return new File(kbRootPath, filename);
    }

    /*
     * Generic getters only below..
     */
    public boolean getBoolean(String key, boolean defaultValue) {
	String value = getProperty(key);
	if (value == null) {
	    return defaultValue;
	}
	return Boolean.parseBoolean(value);
    }

    public Boolean getBoolean(String key) {
	String value = getProperty(key);
	if (value == null) {
	    return null;
	}
	return Boolean.parseBoolean(value);
    }

    public double getDouble(String key, double defaultValue) {
	String value = getProperty(key);
	if (value == null) {
	    return defaultValue;
	}
	return Double.parseDouble(value);
    }

    public Double getDouble(String key) {
	String value = getProperty(key);
	if (value == null) {
	    return null;
	}
	return Double.parseDouble(value);
    }

    private int getInt(String key, int defaultValue) {
	String value = getProperty(key);
	if (value == null) {
	    return defaultValue;
	}
	return Integer.parseInt(value);
    }

    public Integer getInteger(String key) {
	String value = getProperty(key);
	if (value == null) {
	    return null;
	}
	return Integer.parseInt(value);
    }

    public String getString(String key, String defaultValue) {
	return getProperty(key, defaultValue);
    }

    public String getString(String key) {
	return getProperty(key);
    }
}