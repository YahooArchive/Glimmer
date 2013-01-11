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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;

import com.yahoo.glimmer.query.RDFIndexStatistics.ClassStat;
import com.yahoo.glimmer.vocabulary.OwlUtils;

public class RDFIndexStatisticsBuilder {
    private Map<String, String> sortedPredicates = Collections.emptyMap();
    private Map<String, Integer> typeTermDistribution = Collections.emptyMap();
    private OWLOntology ontology;
    private Map<String, Integer> predicateTermDistribution = Collections.emptyMap();

    public RDFIndexStatisticsBuilder setSortedPredicates(final Map<String, String> sortedPredicates) {
	this.sortedPredicates = sortedPredicates;
	return this;
    }

    public RDFIndexStatisticsBuilder setTypeTermDistribution(final Map<String, Integer> typeTermDistribution) {
	this.typeTermDistribution = typeTermDistribution;
	return this;
    }

    public RDFIndexStatisticsBuilder setOwlOntologyInputStream(InputStream owlOntologgyInputStream) throws IOException {
	OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

	try {
	    ontology = manager.loadOntologyFromOntologyDocument(owlOntologgyInputStream);
	} catch (OWLOntologyCreationException e) {
	    throw new IllegalArgumentException("Ontology failed to load:" + e.getMessage());
	}
	owlOntologgyInputStream.close();

	return this;
    }

    public RDFIndexStatisticsBuilder setPredicateTermDistribution(Map<String, Integer> predicateTermDistribution) {
	this.predicateTermDistribution = new HashMap<String, Integer>();

	for (String key : predicateTermDistribution.keySet()) {
	    Integer count = predicateTermDistribution.get(key);
	    if (key != null && count != null) {
		this.predicateTermDistribution.put(removeVersion(key), count);
	    }
	}
	return this;
    }

    public RDFIndexStatistics build() {
	RDFIndexStatistics stats = new RDFIndexStatistics();
	stats.setFields(sortedPredicates);

	// Capture basic statistics about class frequency
	for (String clazzName : typeTermDistribution.keySet()) {
	    Integer count = typeTermDistribution.get(clazzName);
	    String localName = OwlUtils.getLocalName(IRI.create(clazzName));
	    stats.addClassStat(removeVersion(clazzName), new ClassStat(localName, count));
	}

	if (ontology != null && stats.getClasses() != null) {
	    Map<String, OWLClass> nameToOwlClassMap = new HashMap<String, OWLClass>();

	    // Populate owlToStatClassMap and set labels and properties on
	    // ClassStat instances.
	    for (String clazzName : stats.getClasses().keySet()) {
		OWLClass owlClass = null;
		// Remove version if the class name contains a version
		// number
		if (ontology.containsClassInSignature(IRI.create(clazzName))) {
		    owlClass = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(clazzName));
		} else {
		    owlClass = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(removeVersion(clazzName)));
		}

		if (owlClass != null) {
		    ClassStat stat = stats.getClasses().get(clazzName);
		    stat.setLabel(OwlUtils.getLabel(owlClass, ontology));

		    for (OWLProperty<?, ?> prop : OwlUtils.getPropertiesInDomain(owlClass, ontology)) {
			if (prop instanceof OWLDataProperty) {
			    String name = prop.getIRI().toString();
			    stat.addProperty(name);

			    String encodeName = com.yahoo.glimmer.util.Util.encodeFieldName(removeVersion(name));
			    Integer predicateCount = predicateTermDistribution.get(encodeName);

			    if (predicateCount != null) {
				stats.addPropertyStat(name, predicateCount);
			    }
			}
		    }

		    nameToOwlClassMap.put(owlClass.getIRI().toString(), owlClass);
		} else {
		    System.err.println("Indexed type not in the ontology: " + clazzName);
		}
	    }

	    // Build tree and get root classes.
	    Set<String> rootClassNames = new HashSet<String>();
	    Set<ClassStat> ancestorStats = new HashSet<ClassStat>();
	    HashSet<String> classNamesInIndex = new HashSet<String>(stats.getClasses().keySet());
	    for (String className : classNamesInIndex) {
		buildGraph(ontology, stats, nameToOwlClassMap, rootClassNames, ancestorStats, className);

		int count = stats.getClasses().get(className).getCount();
		for (ClassStat stat : ancestorStats) {
		    stat.addToInheritedCount(count);
		}
		ancestorStats.clear();
	    }

	    for (String rootClassName : rootClassNames) {
		stats.addRootClass(rootClassName);
	    }
	}

	return stats;
    }

    /**
     * For the given OWLClass traverse the Ontology graph to it's roots creating
     * intermediate ClassStat objects for missing super classes and adding
     * ClassStat child names. Roots here refers to super classes that aren't sub
     * classes of any other class.
     * 
     * @param ontology2
     * 
     * @param onto
     * @param stats
     * @param rootClassNames
     * @param owlToStatClassMap
     * @param rootClasses
     * @param owlClass
     * 
     *            TODO cyclic detection.
     */
    private static void buildGraph(OWLOntology ontology, RDFIndexStatistics stats, Map<String, OWLClass> nameToOwlClassMap, Set<String> rootClassNames,
	    Set<ClassStat> ancestorStats, String owlClassName) {
	int superClassCount = 0;
	OWLClass owlClass = nameToOwlClassMap.get(owlClassName);
	for (OWLClassExpression superOwlExpression : owlClass.getSuperClasses(ontology)) {
	    if (superOwlExpression instanceof OWLClass) {
		OWLClass superOwlClass = (OWLClass) superOwlExpression;
		String superOwlClassName = superOwlClass.getIRI().toString();

		ClassStat superStat = stats.getClasses().get(superOwlClassName);
		if (superStat == null) {
		    // Is is possible the the super class doesn't have a
		    // ClassStat object as we start with only
		    // ClassStat objects for things that are index.
		    String superLocalName = OwlUtils.getLocalName(superOwlClass.getIRI());
		    superStat = new ClassStat(superLocalName,0);
		    superStat.setLabel(OwlUtils.getLabel(superOwlClass, ontology));
		    stats.addClassStat(superOwlClassName, superStat);
		    nameToOwlClassMap.put(superOwlClassName, superOwlClass);
		}

		// Add this owlClass as a child of the superOwlClass
		superStat.addChild(owlClass.getIRI().toString());

		buildGraph(ontology, stats, nameToOwlClassMap, rootClassNames, ancestorStats, superOwlClassName);

		ancestorStats.add(superStat);

		superClassCount++;
	    }
	}
	if (superClassCount == 0) {
	    rootClassNames.add(owlClassName);
	}
    }

    public static String toString(RDFIndexStatistics stats) {
	StringBuilder sb = new StringBuilder();
	for (String rootClassName : stats.getRootClasses()) {
	    print(0, stats, rootClassName, sb);
	}
	return sb.toString();
    }

    private static void print(int depth, RDFIndexStatistics stats, String owlClassName, StringBuilder sb) {
	ClassStat stat = stats.getClasses().get(owlClassName);

	for (int i = 0; i < depth; i++) {
	    sb.append('\t');
	}
	sb.append(owlClassName);
	sb.append(':');
	sb.append(stat.getCount());
	sb.append('/');
	sb.append(stat.getInheritedCount());
	sb.append('\n');

	if (stat.getChildren() != null) {
	    for (String childClassName : stat.getChildren()) {
		print(depth + 1, stats, childClassName, sb);
	    }
	}
    }

    private static String removeVersion(String uri) {
	// HACK: second part we shouldn't need
	uri = uri.replaceFirst("[0-9]+\\.[0-9]+\\.[0-9]+\\/", "");
	uri = uri.replaceFirst("[0-9]+_[0-9]_+[0-9]+_", "");
	return uri;
    }
}