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

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.di.big.mg4j.document.DocumentFactory.FieldType;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Counters;

/* Common superclass to HorizontalDocumentFactory and VerticalDocumentFactory.
 * 
 */
public abstract class RDFDocumentFactory {
    private static final Log LOG = LogFactory.getLog(RDFDocumentFactory.class);

    private static final String CONF_FIELDNAMES_KEY = "RdfFieldNames";
    private static final String CONF_INDEX_TYPE_KEY = "IndexType";
    private static final String CONF_WITH_CONTEXTS_KEY = "WithContexts";
    private static final String CONF_RESOURCE_ID_PREFIX_KEY = "resourceIdPrefix";
    private static final String CONF_RESOURCES_HASH_KEY = "ResourcesFilename";

    private static final Collection<String> PREDICATE_BLACKLIST = Arrays.asList("stag", "tagspace", "ctag", "rel", "mm");

    private String[] fields;
    private AbstractObject2LongFunction<CharSequence> resourcesHashFunction;
    private String resourceIdPrefix = "";

    // TODO How to read these?
    private Counters counters = new Counters();

    // Include NQuad contexts in processing.
    private boolean withContexts;

    public static enum IndexType {
	VERTICAL(VerticalDocumentFactory.class), HORIZONTAL(HorizontalDocumentFactory.class), UNDEFINED(null);

	private final Class<?> factoryClass;

	private IndexType(Class<?> factoryClass) {
	    this.factoryClass = factoryClass;
	}

	public Class<?> getFactoryClass() {
	    return factoryClass;
	}
    }

    public abstract RDFDocument getDocument();

    protected static void setupConf(Configuration conf, IndexType type, boolean withContexts, String resourcesHash, String resourceIdPrefix, String... fields) {
	conf.setEnum(CONF_INDEX_TYPE_KEY, type);
	conf.setBoolean(CONF_WITH_CONTEXTS_KEY, withContexts);
	if (resourcesHash != null) {
	    conf.set(CONF_RESOURCES_HASH_KEY, resourcesHash);
	}
	conf.set(CONF_RESOURCE_ID_PREFIX_KEY, resourceIdPrefix);
	conf.setStrings(CONF_FIELDNAMES_KEY, fields);
    }

    public static String[] getFieldsFromConf(Configuration conf) {
	String[] fields = conf.getStrings(CONF_FIELDNAMES_KEY);
	if (fields == null) {
	    throw new IllegalStateException("Fields not set set in the config.");
	}
	return fields;
    }

    public static IndexType getIndexType(Configuration conf) {
	IndexType indexType = conf.getEnum(CONF_INDEX_TYPE_KEY, IndexType.UNDEFINED);
	if (indexType == IndexType.UNDEFINED) {
	    throw new IllegalStateException("Index type not set in config.");
	}
	return indexType;
    }
    
    public static boolean getWithContexts(Configuration conf) {
	return conf.getBoolean(CONF_WITH_CONTEXTS_KEY, true);
    }
    
    public static String getHashValuePrefix(Configuration conf) {
	return conf.get(CONF_RESOURCE_ID_PREFIX_KEY, "");
    }

    public static RDFDocumentFactory buildFactory(Configuration conf) {
	IndexType indexType = getIndexType(conf);

	RDFDocumentFactory factory;
	try {
	    Constructor<?> factoryConstructor = indexType.factoryClass.getConstructor();
	    factory = ((RDFDocumentFactory) factoryConstructor.newInstance());
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	factory.setFields(getFieldsFromConf(conf));
	factory.setWithContexts(getWithContexts(conf));
	factory.setResourceIdPrefix(getHashValuePrefix(conf));
	String resourcesHashFilename = conf.get(CONF_RESOURCES_HASH_KEY);
	if (resourcesHashFilename != null) {
	    // Load the hash func.
	    try {
		Path resourcesHashPath = new Path(resourcesHashFilename);
		InputStream resourcesHashInputStream = CompressionCodecHelper.openInputStream(conf, resourcesHashPath);

		@SuppressWarnings("unchecked")
		AbstractObject2LongFunction<CharSequence> hash = (AbstractObject2LongFunction<CharSequence>) BinIO.loadObject(resourcesHashInputStream);
		factory.setResourcesHashFunction(hash);
		LOG.info("Loaded resource hash from " + resourcesHashFilename + " with " + hash.size() + " entires.");
	    } catch (Exception e) {
		throw new RuntimeException(e);
	    }
	} else {
	    LOG.info("No resource hash filename set in conf.  No hash has been loaded.");
	}
	return factory;
    }

    public void setResourcesHashFunction(AbstractObject2LongFunction<CharSequence> resourcesHashFunction) {
	this.resourcesHashFunction = resourcesHashFunction;
    }

    public String getResourceIdPrefix() {
	return resourceIdPrefix;
    }

    public void setResourceIdPrefix(String resourceIdPrefix) {
	this.resourceIdPrefix = resourceIdPrefix;
    }

    /**
     * @param url
     * @return The hash value for the given URL/BNode or null. The exact behavior depends on the implementation of the hash function used.
     *  
     */
    public Long lookupResource(String key) {
	Long value = resourcesHashFunction.get(key);
	if (value != null && value < 0) {
	    throw new RuntimeException("Negative hash value for " + key);
	}
	return value;
    }

    public String lookupResource(String key, boolean prefixed) {
	Long value = lookupResource(key);
	if (value != null) {
	    if (prefixed) {
		return resourceIdPrefix + value.toString();
	    } else {
		return value.toString();
	    }
	}
	return null;
    }

    public boolean isWithContexts() {
	return withContexts;
    }

    public void setWithContexts(Boolean withContexts) {
	this.withContexts = withContexts;
    }

    public static boolean isOnPredicateBlacklist(final String predicate) {
	return PREDICATE_BLACKLIST.contains(predicate);
    }

    public void setFields(String[] fields) {
	this.fields = fields;
    }

    public int getFieldCount() {
	ensureFieldIndex(0);
	return fields.length;
    }

    public String getFieldName(final int index) {
	ensureFieldIndex(index);
	return fields[index];
    }

    public int getFieldIndex(final String fieldName) {
	ensureFieldIndex(0);
	for (int i = 0; i < fields.length; i++) {
	    if (fields[i].equals(fieldName)) {
		return i;
	    }
	}
	return -1;
    }

    public FieldType getFieldType(final int index) {
	ensureFieldIndex(index);
	return FieldType.TEXT;
    }

    public void ensureFieldIndex(final int index) {
	if (fields == null) {
	    throw new IllegalStateException("Fields not loaded.");
	}
	if (index < 0 || index >= fields.length) {
	    throw new IndexOutOfBoundsException("For field index " + index + ". There are only " + fields.length + " fields.");
	}
    }

    public static enum RdfCounters {
	EMPTY_LINES, EMPTY_DOCUMENTS, BLACKLISTED_TRIPLES, UNINDEXED_PREDICATE_TRIPLES, RDF_TYPE_TRIPLES, INDEXED_TRIPLES, PARSE_ERROR
    }

    public void incrementCounter(RdfCounters counter, int by) {
	counters.findCounter(counter).increment(by);
    }

    public Counters getCounters() {
	return counters;
    }

    public String getInputStreamEncodeing() {
	return "UTF-8";
    }
}
