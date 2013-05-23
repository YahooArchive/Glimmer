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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/** 
 * Load an ontology from the distributed cache.
 * @author tep
 *
 */
public class OntologyLoader {
    public static final String ONTOLOGY_SYMBOLIC_NAME = "Ontology";

    public static OWLOntology load(Configuration conf) throws IOException {
	FileSystem fs = FileSystem.get(conf);
	@SuppressWarnings("deprecation")
	// TODO How to do this in hadoop 0.23??
	URI[] cacheFiles = DistributedCache.getCacheFiles(conf);
	if (cacheFiles != null) {
	    for (URI cacheFile : cacheFiles) {
		if (ONTOLOGY_SYMBOLIC_NAME.equals(cacheFile.getFragment())) {
		    Path filterPath = new Path(cacheFile);

		    FSDataInputStream filterIs = fs.open(filterPath);
		    return load(filterIs);
		}
	    }
	}
	return null;
    }

    public static OWLOntology load(InputStream is) throws IOException {
	// Load the ontology
	OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
	try {
	    return manager.loadOntologyFromOntologyDocument(is);
	} catch (OWLOntologyCreationException e) {
	    throw new IllegalArgumentException("Ontology failed to load:" + e.getMessage());
	} finally {
	    is.close();
	}
    }
}
