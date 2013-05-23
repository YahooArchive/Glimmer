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

import it.unimi.di.big.mg4j.document.DocumentFactory.FieldType;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Counters;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLOntology;

import com.yahoo.glimmer.util.Util;

/* Common superclass to HorizontalDocumentFactory and VerticalDocumentFactory.
 * 
 */
public abstract class RDFDocumentFactory {
    private static final Log LOG = LogFactory.getLog(RDFDocumentFactory.class);

    private static final String CONF_FIELDNAMES_KEY = "RdfFieldNames";
    private static final String CONF_INDEX_TYPE_KEY = "IndexType";
    private static final String CONF_WITH_CONTEXTS_KEY = "WithContexts";
    private static final String CONF_RESOURCES_HASH_KEY = "ResourcesFilename";
    private static final String CONF_RESOURCE_ID_PREFIX_KEY = "resourceIdPrefix";

    private static final Collection<String> PREDICATE_BLACKLIST = Arrays.asList("stag", "tagspace", "ctag", "rel", "mm");

    private String[] fields;
    private AbstractObject2LongFunction<CharSequence> resourcesHashFunction;
    private OWLOntology ontology;
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

    public static RDFDocumentFactory buildFactory(Configuration conf) throws IOException {
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
	    Path resourcesHashPath = new Path(resourcesHashFilename);
	    FileSystem fs = FileSystem.get(conf);
	    InputStream resourcesHashInputStream = fs.open(resourcesHashPath);
	    try {
		@SuppressWarnings("unchecked")
		AbstractObject2LongFunction<CharSequence> hash = (AbstractObject2LongFunction<CharSequence>) BinIO.loadObject(resourcesHashInputStream);
		factory.setResourcesHashFunction(hash);
		LOG.info("Loaded resource hash from " + resourcesHashFilename + " with " + hash.size() + " entires.");
	    } catch (Exception e) {
		throw new RuntimeException(e);
	    } finally {
		resourcesHashInputStream.close();
	    }
	} else {
	    LOG.info("No resource hash filename set in conf.  No hash has been loaded.");
	}
	
	OWLOntology ontology = OntologyLoader.load(conf);
	if (ontology != null) {
	    LOG.info("Loaded ontology with " + ontology.getAxiomCount() + " axioms from distrubuted cache.");
	    factory.setOntology(ontology);
	} else {
	    LOG.info("No ontology file found in distrubuted cache.");
	}
	
	return factory;
    }

    public void setResourcesHashFunction(AbstractObject2LongFunction<CharSequence> resourcesHashFunction) {
	this.resourcesHashFunction = resourcesHashFunction;
    }
    
    public void setOntology(OWLOntology ontology) {
	this.ontology = ontology;
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
    
    /**
     * Get all the ancestors of the give class.
     * @param className
     * @return
     */
    public Collection<String> getAncestors(String className) {
	OWLClass owlClass = null;
	// Remove version if the class name contains a version
	// number
	if (ontology.containsClassInSignature(IRI.create(className))) {
	    owlClass = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(className));
	} else {
	    owlClass = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(Util.removeVersion(className)));
	}
	if (owlClass == null) {
	    return Collections.emptySet();
	}

	Set<OWLClassExpression> superClasses = new HashSet<OWLClassExpression>();
	expandOntologyTypesR(superClasses, owlClass);

	if (superClasses.isEmpty()) {
	    return Collections.emptySet();
	}

	ArrayList<String> classAndAncestors = new ArrayList<String>(1 + superClasses.size());
	for (OWLClassExpression superClass : superClasses) {
	    classAndAncestors.add(((OWLClass)superClass).getIRI().toString());
	}
	return classAndAncestors;
    }

    private void expandOntologyTypesR(Set<OWLClassExpression> allSuperClasses, OWLClass owlClass) {
	for (OWLClassExpression owlClassExpression : owlClass.getSuperClasses(ontology)) {
	    if (owlClassExpression instanceof OWLClass && allSuperClasses.add(owlClassExpression)) {
		expandOntologyTypesR(allSuperClasses, (OWLClass) owlClassExpression);
	    }
	}
    }

    public static enum RdfCounters {
	EMPTY_LINES, EMPTY_DOCUMENTS, BLACKLISTED_TRIPLES, UNINDEXED_PREDICATE_TRIPLES, RDF_TYPE_TRIPLES, INDEXED_TRIPLES, PARSE_ERROR, ONTOLOGY_SUPER_TYPE_NOT_IN_HASH
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
