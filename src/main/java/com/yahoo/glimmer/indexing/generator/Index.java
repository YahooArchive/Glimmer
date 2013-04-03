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

import it.unimi.di.big.mg4j.index.BitStreamIndexWriter;
import it.unimi.di.big.mg4j.index.CompressionFlags;
import it.unimi.di.big.mg4j.index.CompressionFlags.Coding;
import it.unimi.di.big.mg4j.index.CompressionFlags.Component;
import it.unimi.di.big.mg4j.index.DiskBasedIndex;
import it.unimi.di.big.mg4j.index.IndexWriter;
import it.unimi.di.big.mg4j.io.HadoopFileSystemIOFactory;
import it.unimi.di.big.mg4j.io.IOFactory;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.util.Properties;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.yahoo.glimmer.indexing.CombinedTermProcessor;
import com.yahoo.glimmer.indexing.ResourceRefTermProcessor;

public class Index {
    private PrintWriter terms;
    private OutputStream properties;
    private IndexWriter indexWriter;

    private FileSystem fs;
    private Path outputDir;
    private String name;
    private long numDocs;
//    private int indexWriterCacheSize = QuasiSuccinctIndexWriter.DEFAULT_CACHE_SIZE;

    private boolean positions;
    private String hashValuePrefix;

    public Index(FileSystem fs, Path outputDir, String indexName, long numDocs, boolean positions, String hashValuePrefix, int indexWriterCacheSize) {
	this.fs = fs;
	this.outputDir = outputDir;
	// It seems like MG4J doesn't like index names with the '-' char
	this.name = indexName.replaceAll("\\-", "_");
	this.numDocs = numDocs;
	this.positions = positions;
	this.hashValuePrefix = hashValuePrefix;
//	if (indexWriterCacheSize != 0) {
//	    this.indexWriterCacheSize = indexWriterCacheSize;
//	}
    }

    public void open() throws IOException {
	String basename = new Path(outputDir, name).toString();
	

	Path termsPath = new Path(outputDir, name + DiskBasedIndex.TERMS_EXTENSION);
	terms = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fs.create(termsPath, true), "UTF-8")));// overwrite

	Path propertiesPath = new Path(outputDir, name + DiskBasedIndex.PROPERTIES_EXTENSION);
	properties = fs.create(propertiesPath, true);// overwrite

	Map<Component, Coding> defaultStandardIndexFlags = new Object2ObjectOpenHashMap<Component, Coding>(CompressionFlags.DEFAULT_STANDARD_INDEX);
	if (!positions) {
	    defaultStandardIndexFlags.remove(CompressionFlags.Component.POSITIONS);
	    defaultStandardIndexFlags.remove(CompressionFlags.Component.COUNTS); // Quasi Succinct Indexes can't not have counts.
	}
	
	IOFactory ioFactory = new HadoopFileSystemIOFactory(fs);
	
//	indexWriter = new QuasiSuccinctIndexWriter(ioFactory, basename, numDocs, Fast.mostSignificantBit(QuasiSuccinctIndex.DEFAULT_QUANTUM), indexWriterCacheSize, defaultStandardIndexFlags, ByteOrder.nativeOrder());
	indexWriter = new BitStreamIndexWriter(ioFactory, basename, numDocs, true, defaultStandardIndexFlags);
    }

    public PrintWriter getTermsWriter() {
	return terms;
    }

    public boolean hasPositions() {
	return positions;
    }

    public IndexWriter getIndexWriter() {
	return indexWriter;
    }

    public OutputStream getPropertiesStream() {
	return properties;
    }

    public void close(long writtenOccurrences) throws IOException {
	try {
	    Properties props = indexWriter.properties();
	    System.out.println("Closing index " + name + " which has " + props.getProperty(it.unimi.di.big.mg4j.index.Index.PropertyKeys.TERMS) + " terms ");
	    if (positions) {
		props.setProperty(it.unimi.di.big.mg4j.index.Index.PropertyKeys.OCCURRENCES, writtenOccurrences);
	    }
	    props.setProperty(it.unimi.di.big.mg4j.index.Index.PropertyKeys.MAXCOUNT, -1);
	    props.setProperty(it.unimi.di.big.mg4j.index.Index.PropertyKeys.FIELD, name);
	    props.setProperty(it.unimi.di.big.mg4j.index.Index.PropertyKeys.TERMPROCESSOR, CombinedTermProcessor.getInstance());
	    props.addProperty(ResourceRefTermProcessor.PropertyKeys.REF_PREFIX, hashValuePrefix);

	    props.save(properties);
	} catch (ConfigurationException e) {
	    throw new IOException(e.getMessage());
	}

	properties.close();
	terms.close();
	indexWriter.close();
    }
    
    public String getName() {
	return name;
    }
}