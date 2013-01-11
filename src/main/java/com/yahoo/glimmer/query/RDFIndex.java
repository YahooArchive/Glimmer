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

import it.unimi.di.mg4j.document.ConcatenatedDocumentCollection;
import it.unimi.di.mg4j.document.Document;
import it.unimi.di.mg4j.document.DocumentCollection;
import it.unimi.di.mg4j.index.BitStreamIndex;
import it.unimi.di.mg4j.index.DiskBasedIndex;
import it.unimi.di.mg4j.index.Index;
import it.unimi.di.mg4j.index.Index.UriKeys;
import it.unimi.di.mg4j.index.IndexIterator;
import it.unimi.di.mg4j.index.QuasiSuccinctIndex;
import it.unimi.di.mg4j.index.TermProcessor;
import it.unimi.di.mg4j.query.QueryEngine;
import it.unimi.di.mg4j.query.SelectedInterval;
import it.unimi.di.mg4j.query.nodes.Query;
import it.unimi.di.mg4j.query.nodes.QueryBuilderVisitorException;
import it.unimi.di.mg4j.search.DocumentIteratorBuilderVisitor;
import it.unimi.di.mg4j.search.score.CountScorer;
import it.unimi.di.mg4j.search.score.DocumentScoreInfo;
import it.unimi.di.mg4j.search.score.Scorer;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.io.FileLinesList;
import it.unimi.dsi.util.SemiExternalGammaList;
import it.unimi.dsi.util.StringMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.yahoo.glimmer.indexing.ResourceRefTermProcessor;
import com.yahoo.glimmer.indexing.TitleListDocumentCollection;
import com.yahoo.glimmer.util.Util;

public class RDFIndex {
    private final static Logger LOGGER = Logger.getLogger(RDFIndex.class);
    private final static String TYPE_INDEX = "http_www_w3_org_1999_02_22_rdf_syntax_ns_type";
    public final static int MAX_STEMMING = 1024;

    private final static String BASENAME_INDEX_PROPERTY_KEY = "basename";

    private final static String ALIGNMENT_INDEX_NAME = "alignment";
    private final static String SUBJECT_INDEX_KEY = "subject";
    private final static String PREDICATE_INDEX_KEY = "predicate";
    private final static String OBJECT_INDEX_KEY = "object";
    private final static String CONTEXT_INDEX_KEY = "context";

    /** The query engine. */
    private QueryEngine queryEngine;
    /** The document collection. */
    private DocumentCollection documentCollection = null;
    /** Term counts in the token index */
    protected SemiExternalGammaList frequencies = null;
    /** Document priors */
    protected HashMap<Integer, Integer> documentPriors = null;
    /** Map used to encode URIs for retrieving from the collection */
    protected Object2LongFunction<CharSequence> allResourcesToIds;
    /** Map used to decode URIs */
    protected FileLinesList allIdsToResources;
    /** The alignment index **/
    protected Index alignmentIndex;

    private Map<String, Integer> predicateDistribution;
    private Map<String, Integer> typeTermDistribution;

    private Set<String> verticalPredicates;

    protected RDFIndexStatistics stats;

    protected RDFQueryParser parser;

    private static DocumentCollection loadDocumentCollection(File collectionFile) throws RDFIndexException {
	try {
	    DocumentCollection documentCollection = (DocumentCollection) BinIO.loadObject(collectionFile);
	    documentCollection.filename(collectionFile.getPath());
	    return documentCollection;
	} catch (Exception e) {
	    throw new RDFIndexException(e);
	}
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadObjectOfType(File file) throws RDFIndexException {
	if (file == null) {
	    return null;
	}
	try {
	    return (T) BinIO.loadObject(file);
	} catch (Exception e) {
	    throw new RDFIndexException("While loading from " + file.getPath(), e);
	}
    }

    public RDFIndex(Context context) throws RDFIndexException {
	File kbRootPath = context.getKbRootPath();
	if (kbRootPath == null) {
	    throw new IllegalArgumentException("path to knowledge base root is not set.");
	}
	if (!kbRootPath.isDirectory()) {
	    throw new IllegalArgumentException("path to knowledge base root is not a directory.");
	}

	File verticalIndexDir = context.getVerticalIndexDir();
	if (verticalIndexDir == null) {
	    throw new IllegalArgumentException("path to vertical indexes is not set.");
	}
	if (!verticalIndexDir.isDirectory()) {
	    throw new IllegalArgumentException("path to vertical indexes is not a directory.");
	}

	File horizontalIndexDir = context.getHorizontalIndexDir();
	if (horizontalIndexDir == null) {
	    throw new IllegalArgumentException("path to horizontal indexes is not set.");
	}
	if (!horizontalIndexDir.isDirectory()) {
	    throw new IllegalArgumentException("path to horizontal indexes is not a directory.");
	}

	// Load the collection or titlelist
	File collectionFile = context.getCollectionFile();
	if (collectionFile != null) {
	    LOGGER.info("Loading collection from " + collectionFile);

	    if (collectionFile.isFile()) {
		documentCollection = loadDocumentCollection(collectionFile);
	    } else if (collectionFile.isDirectory()) {
		// A directory of collections.  fileNames will hold the names of the files without their path.
		String[] fileNames = collectionFile.list(new FilenameFilter() {
		    @Override
		    public boolean accept(File dir, String name) {
			return name.endsWith(".collection") || name.endsWith(".sos");
		    }
		});

		if (fileNames.length == 0) {
		    throw new RuntimeException("No .collection files found in directory " + collectionFile.getPath());
		}

		if (fileNames.length == 1) {
		    File file = new File(collectionFile, fileNames[0]);
		    documentCollection = loadDocumentCollection(file);
		} else {
		    // Sort names to get the collection order right
		    Arrays.sort(fileNames);
		    documentCollection = new ConcatenatedDocumentCollection(fileNames);
		    try {
			documentCollection.filename(collectionFile + "/.");
		    } catch (IOException e) {
			throw new RDFIndexException(e);
		    }
		}
	    } else {
		throw new RuntimeException("Expected " + collectionFile.getPath() + " to be a collection file or directory containing collection files.");
	    }
	}

	if (documentCollection == null) {
	    LOGGER.info("No collection specified, we will try to use a title list...");
	    File titleListFile = context.getTitleListFile();
	    if (titleListFile != null) {
		LOGGER.info("Loading titlelist from " + titleListFile.getPath());
		List<MutableString> titleList;
		try {
		    titleList = new FileLinesList(titleListFile.getPath(), "ASCII");
		    LOGGER.info("Loaded titlelist of size " + titleList.size() + ".");
		    documentCollection = new TitleListDocumentCollection(titleList);
		} catch (Exception e) {
		    throw new IllegalArgumentException("Failed to open TitleListDocumentCollection.", e);
		}
	    }
	}

	// Load all resources hash function
	allResourcesToIds = loadObjectOfType(context.getAllResourcesMapFile());
	if (allResourcesToIds == null) {
	    LOGGER.warn("Warning, no resources map specified!");
	} else {
	    LOGGER.info("Loaded resourses map " + context.getAllResourcesMapFile().getPath() + " with " + allResourcesToIds.size() + " entries.");
	}

	// Load the reverse all resource function.
	File allResourcesFile = context.getAllResourcesFile();
	if (!allResourcesFile.exists()) {
	    throw new RDFIndexException("All resources file " + allResourcesFile.getPath() + " does not exist.");
	}
	try {
	    allIdsToResources = new FileLinesList(allResourcesFile.getPath(), "UTF-8");
	} catch (IOException e) {
	    throw new RDFIndexException("Couldn't open all resources file " + allResourcesFile.getPath() + " as a FileLinesList.", e);
	}

	// Load vertical indexes
	Object2ReferenceMap<String, Index> indexMap = loadIndexesFromDir(verticalIndexDir, context.getLoadDocumentSizes(), context.getLoadIndexesInMemory());
	LOGGER.info("Loaded " + indexMap.size() + " vertical indices.");
	
	verticalPredicates = Collections.unmodifiableSet(new HashSet<String>(indexMap.keySet()));

	try {
	    LOGGER.info("Loading alignment index..");
	    String alignmentUri = new File(verticalIndexDir, ALIGNMENT_INDEX_NAME).getPath();
	    alignmentIndex = Index.getInstance( alignmentUri + "?mapped=1" );
	} catch( Exception e ) {
	    LOGGER.error("Failed to load alignment index", e);
	}

	// Load horizontal indexes
	indexMap.putAll(loadIndexesFromDir(horizontalIndexDir, true, context.getLoadIndexesInMemory()));

	if (!indexMap.containsKey(SUBJECT_INDEX_KEY)) {
	    throw new IllegalStateException("No subjects index found.");
	}
	if (!indexMap.containsKey(PREDICATE_INDEX_KEY)) {
	    throw new IllegalStateException("No predicates index found.");
	}
	if (!indexMap.containsKey(OBJECT_INDEX_KEY)) {
	    throw new IllegalStateException("No objects index found.");
	}
	if (!indexMap.containsKey(CONTEXT_INDEX_KEY)) {
	    LOGGER.info("No context index found.");
	}

	// Loading frequencies
	Index subjectIndex = indexMap.get(SUBJECT_INDEX_KEY);
	String filename = (String) subjectIndex.properties.getProperty(BASENAME_INDEX_PROPERTY_KEY);
	filename += DiskBasedIndex.FREQUENCIES_EXTENSION;
	try {
	    LOGGER.info("Loading frequencies from " + filename);
	    frequencies = new SemiExternalGammaList(new InputBitStream(filename), 1, subjectIndex.numberOfTerms);
	    if (frequencies.size() != subjectIndex.numberOfDocuments) {
		LOGGER.warn("Loaded " + frequencies.size() + " frequency values but subject.numberOfDocuments is " + subjectIndex.numberOfDocuments);
	    }
	} catch (Exception e) {
	    throw new IllegalArgumentException("Failed to load frequences for subject index from " + filename, e);
	}

	try {
	    predicateDistribution = Collections.unmodifiableMap(getTermDistribution(indexMap.get(PREDICATE_INDEX_KEY), true));
	    Index typeField = indexMap.get(TYPE_INDEX);
	    if (typeField == null) {
		typeTermDistribution = Collections.emptyMap();
	    } else {
		typeTermDistribution = Collections.unmodifiableMap(getTermDistribution(typeField, true));
	    }
	} catch (IOException e) {
	    throw new RDFIndexException(e);
	}

	List<String> indexedPredicatesOrdered = new ArrayList<String>();
	try {
	    LOGGER.info("Loading indexed predicates list..");
	    for (MutableString line : new FileLinesList(context.getIndexedPredicatesFile().getPath(), "UTF-8")) {
	        indexedPredicatesOrdered.add(Util.encodeFieldName(line.toString()));
	    }
	} catch (IOException e1) {
	    throw new RDFIndexException("Failed to load indexed predicated list from file:" + context.getIndexedPredicatesFile().getPath());
	}

	// We need to maintain insertion order and test inclusion.
	Map<String, String> fieldNameSuffixToFieldNameOrderedMap = new LinkedHashMap<String, String>();
	fieldNameSuffixToFieldNameOrderedMap.put(SUBJECT_INDEX_KEY, SUBJECT_INDEX_KEY);
	fieldNameSuffixToFieldNameOrderedMap.put(PREDICATE_INDEX_KEY, PREDICATE_INDEX_KEY);
	fieldNameSuffixToFieldNameOrderedMap.put(OBJECT_INDEX_KEY, OBJECT_INDEX_KEY);

	List<String> shortNames = Util.generateShortNames(indexedPredicatesOrdered, fieldNameSuffixToFieldNameOrderedMap.keySet(), '_');
	for (int i = 0 ; i < shortNames.size() ; i++) {
	    fieldNameSuffixToFieldNameOrderedMap.put(shortNames.get(i), indexedPredicatesOrdered.get(i));
	    LOGGER.info("Predicate short name: " + shortNames.get(i) + " -> " + indexedPredicatesOrdered.get(i));
	}
	
	RDFIndexStatisticsBuilder statsBuilder = new RDFIndexStatisticsBuilder();
	statsBuilder.setSortedPredicates(fieldNameSuffixToFieldNameOrderedMap);
	statsBuilder.setTypeTermDistribution(typeTermDistribution);

	// Load the ontology if provided
	if (context.getOntoPath() != null) {
	    try {
		InputStream owlOntologgyInputStream = RDFIndexStatisticsBuilder.class.getClassLoader().getResourceAsStream(context.getOntoPath().getPath());
		if (owlOntologgyInputStream == null) {
		    throw new FileNotFoundException("Can open ontology file " + owlOntologgyInputStream);
		}
		statsBuilder.setOwlOntologyInputStream(owlOntologgyInputStream);
		statsBuilder.setPredicateTermDistribution(predicateDistribution);
	    } catch (FileNotFoundException e) {
		throw new RDFIndexException("Ontology file not found:" + context.getOntoPath());
	    } catch (IOException e) {
		throw new RDFIndexException("Reading file " + context.getOntoPath(), e);
	    }
	}
	stats = statsBuilder.build();

	// This is empty for non-payload indices
	Reference2ReferenceMap<Index, Object> index2Parser = new Reference2ReferenceOpenHashMap<Index, Object>();
	DocumentIteratorBuilderVisitor builderVisitor = new DocumentIteratorBuilderVisitor(indexMap, index2Parser, subjectIndex, MAX_STEMMING);
	// QueryParser is null as we will only pass in parsed queries
	queryEngine = new QueryEngine(null, builderVisitor, indexMap);

	// We set up an interval selector only if there is a collection for
	// snippeting
	// queryEngine.intervalSelector = documentCollection != null ? new
	// IntervalSelector(4, 40): new IntervalSelector();
	queryEngine.multiplex = false;
	queryEngine.intervalSelector = null;

	// Load priors
	documentPriors = loadObjectOfType(context.getDocumentPriorsFile());
	if (documentPriors != null) {
	    LOGGER.info("Loaded priors from " + context.getDocumentPriorsFile());
	} else {
	    LOGGER.info("Path to priors is null. None loaded.");
	}

	// Sets field weight and scorer
	reconfigure(context);

	// Init query parser
	final Object2ObjectOpenHashMap<String, TermProcessor> termProcessors = new Object2ObjectOpenHashMap<String, TermProcessor>(getIndexedFields().size());
	for (String alias : getIndexedFields())
	    termProcessors.put(alias, getField(alias).termProcessor);
	parser = new RDFQueryParser(getAlignmentIndex(), indexedPredicatesOrdered, fieldNameSuffixToFieldNameOrderedMap, SUBJECT_INDEX_KEY, termProcessors,
		allResourcesToIds);
    }

    private Object2ReferenceMap<String, Index> loadIndexesFromDir(File indexDir, boolean loadDocSizes, boolean inMemory) throws RDFIndexException {
	EnumMap<UriKeys, String> indexOptionsmap = new EnumMap<UriKeys, String>(UriKeys.class);
	if (inMemory) {
	    indexOptionsmap.put(UriKeys.INMEMORY, "true");
	} else {
	    indexOptionsmap.put(UriKeys.MAPPED, "true");
	}

	// List .properties files in index directory
	File[] propertiesFiles = indexDir.listFiles(new FilenameFilter() {
	    public boolean accept(File dir, String name) {
		return name.endsWith(".properties");
	    }
	});

	List<String> indexBasenames = new ArrayList<String>();

	for (int i = 0; i < propertiesFiles.length; i++) {
	    String baseName = propertiesFiles[i].getName();
	    baseName = baseName.substring(0, baseName.lastIndexOf('.'));
	    if (ALIGNMENT_INDEX_NAME.equals(baseName)) {
		continue;
	    }
	    LOGGER.info("Loading vertical index: '" + baseName + "'");
	    indexBasenames.add(new File(indexDir, baseName).getPath());
	}

	Reference2DoubleOpenHashMap<Index> index2Weight = new Reference2DoubleOpenHashMap<Index>();
	return loadIndicesFromSpec(indexBasenames, documentCollection, index2Weight, loadDocSizes, indexOptionsmap);
    }

    /**
     * Parses a given array of index URIs/weights, loading the correspoding
     * indices and writing the result of parsing in the given maps.
     * 
     * @param indexBasenames
     *            an array of index URIs of the form
     *            <samp><var>uri</var>[:<var>weight</var>]</samp>, specifying
     *            the URI of an index and the weight for the index (1, if
     *            missing).
     * @param loadSizes
     *            forces size loading.
     * @param documentCollection
     *            an optional document collection, or <code>null</code>.
     * @param name2Index
     *            an empty, writable map that will be filled with pairs given by
     *            an index basename (or field name, if available) and an
     *            {@link Index}.
     * @param index2Weight
     *            an empty, writable map that will be filled with a map from
     *            indices to respective weights.
     */
    protected Object2ReferenceMap<String, Index> loadIndicesFromSpec(final List<String> indexBasenames, final DocumentCollection documentCollection,
	    final Reference2DoubleMap<Index> index2Weight, boolean documentSizes, EnumMap<UriKeys, String> map) throws RDFIndexException {

	Object2ReferenceLinkedOpenHashMap<String, Index> name2Index = new Object2ReferenceLinkedOpenHashMap<String, Index>(Hash.DEFAULT_INITIAL_SIZE, .5f);

	for (String indexBasename : indexBasenames) {
	    // We must be careful, as ":" is used by Windows to separate the
	    // device from the path.
	    final int split = indexBasename.lastIndexOf(':');
	    double weight = 1;

	    if (split != -1) {
		try {
		    weight = Double.parseDouble(indexBasename.substring(split + 1));
		} catch (NumberFormatException e) {
		}
	    }

	    final Index index;

	    if (split == -1 || indexBasename.startsWith("mg4j://")) {
		// index = Index.getInstance(basenameWeight[i], true,
		// loadSizes);

		// System.out.println("BASENAME: " + basenameWeight[i]);
		try {
		    index = DiskBasedIndex.getInstance(indexBasename, true, documentSizes, true, map);
		    index2Weight.put(index, 1);
		} catch (ArrayIndexOutOfBoundsException e) {
		    // Empty index
		    System.err.println("Failed to open index: " + indexBasename);
		    continue;
		} catch (Exception e) {
		    throw new RDFIndexException(e);
		}
		index.properties.setProperty(BASENAME_INDEX_PROPERTY_KEY, indexBasename);
	    } else {
		try {
		    index = DiskBasedIndex.getInstance(indexBasename, true, documentSizes, true, map);
		} catch (Exception e) {
		    throw new RDFIndexException(e);
		}
		// index = Index.getInstance(basenameWeight[i].substring(0,
		// split));
		index2Weight.put(index, weight);
	    }
	    /*
	     * if (documentCollection != null && index.numberOfDocuments !=
	     * documentCollection.size()) throw new
	     * IllegalArgumentException("Index " + index + " has " +
	     * index.numberOfDocuments +
	     * " documents, but the document collection has size " +
	     * documentCollection.size());
	     */
	    name2Index.put(index.field != null ? index.field : indexBasename, index);
	}
	return name2Index;
    }

    private Reference2DoubleOpenHashMap<Index> loadB(Context context) {
	Reference2DoubleOpenHashMap<Index> b = new Reference2DoubleOpenHashMap<Index>();

	double db = context.getB();

	for (String indexName : getIndexedFields()) {
	    // TODO load from file if needed
	    b.put(getField(indexName), db);
	}
	b.put(queryEngine.indexMap.get(SUBJECT_INDEX_KEY), db);
	return b;
    }

    /**
     * Compute index weights from context
     * 
     * 
     * @param context
     * @return
     */
    private Reference2DoubleOpenHashMap<Index> loadWeights(Context context) {
	Reference2DoubleOpenHashMap<Index> index2Weight = new Reference2DoubleOpenHashMap<Index>();

	ObjectSet<String> indexNames = queryEngine.indexMap.keySet();
	for (String indexName : indexNames) {
	    Index index = queryEngine.indexMap.get(indexName);
	    String w = context.getString("w." + indexName);
	    if (w == null) { // unimportant
		index2Weight.put(index, context.getWfUnimportant() * indexNames.size());
	    } else {
		if (w.equals(SetDocumentPriors.IMPORTANT))
		    index2Weight.put(index, context.getWfImportant() * indexNames.size());
		else if (w.equals(SetDocumentPriors.UNIMPORTANT))
		    index2Weight.put(index, context.getWfUnimportant() * indexNames.size());
		else if (w.equals(SetDocumentPriors.NEUTRAL))
		    index2Weight.put(index, context.getWfNeutral() * indexNames.size());
	    }
	}

	// System.out.println("Final weights:"+index2Weight);
	return index2Weight;
    }

    protected Scorer configureScorer(Context context) throws FileNotFoundException, IOException {

	Reference2DoubleOpenHashMap<Index> bByIndex = loadB(context);

	double[] documentWeights = new double[3];
	documentWeights[Integer.parseInt(SetDocumentPriors.IMPORTANT)] = context.getWsImportant();
	documentWeights[Integer.parseInt(SetDocumentPriors.UNIMPORTANT)] = context.getWsUnimportant();
	documentWeights[Integer.parseInt(SetDocumentPriors.NEUTRAL)] = context.getWsNeutral();

	StringMap<? extends CharSequence> subjectTermMap;
	Index subjectIndex = getSubjectIndex();
	if (subjectIndex instanceof BitStreamIndex) {
	    subjectTermMap = ((BitStreamIndex) subjectIndex).termMap;
	} else if (subjectIndex instanceof QuasiSuccinctIndex) {
		subjectTermMap = ((QuasiSuccinctIndex) subjectIndex).termMap;
	} else {
	    throw new IllegalStateException("Subject index is not a BitStreamIndex. Don't know how to get its termMap.");
	}
	return new WOOScorer(context.getK1(), bByIndex, subjectTermMap, frequencies, subjectIndex.sizes, (double) subjectIndex.numberOfOccurrences
		/ subjectIndex.numberOfDocuments, subjectIndex.numberOfDocuments, context.getWMatches(), documentWeights, context.getDlCutoff(),
		documentPriors, context.getMaxNumberOfDieldsNorm());
    }

    /**
     * We partially reinitialize the index: we reload the weights and the scorer
     * 
     * @param context
     */
    public void reconfigure(Context context) {
	// Recomputes index weights
	queryEngine.setWeights(loadWeights(context));

	// Configure scorer
	try {

	    // Configure scorer
	    Scorer scorer = configureScorer(context);
	    queryEngine.score(scorer);
	    // Only valid if we have a scorer
	    // ALERT WTF
	    // queryEngine.equalize( context.SIZE_TOP_K );
	} catch (Exception e) {
	    e.printStackTrace();
	    System.err.println("WOO Scorer failed to configure, using default scorer");
	    queryEngine.score(new CountScorer());
	    System.exit(-1);
	}

    }

    private Index getSubjectIndex() {
	return queryEngine.indexMap.get(SUBJECT_INDEX_KEY);
    }

    /**
     * The indexed fields, including the token and uri fields of the horizontal
     * index.
     * 
     * @return
     */
    public Set<String> getIndexedFields() {
	return queryEngine.indexMap.keySet();
    }

    public Index getField(String alias) {
	return queryEngine.indexMap.get(alias);
    }

    /**
     * 
     * @param uri
     *            - Resource or BNode
     * @return the doc id if the given uri is a valid doc uri.
     * @throws IOException
     * @throws RDFIndexException 
     */
    public Integer getSubjectId(String uri) throws IOException {
	Long id = allResourcesToIds.get(uri);
	if (id != null) {
	    // Check that the doc is a valid doc(has contents).. TODO could use
	    // the subjects signed hash here..
	    Document doc = documentCollection.document(id.intValue());
	    Object content = doc.content(0);
	    if (content instanceof Reader) {
		Reader contentReader = (Reader)content;
		if (contentReader.read() == -1) {
		    id = null;
		}
		contentReader.close();
	    } else {
		throw new IllegalStateException("doc.content(0) for doc id " + id.intValue() + " didn't return a Reader!");
	    }
	    doc.close();
	}
	return id == null ? null : id.intValue();
    }

    public Integer getObjectID(String uri) {
	Long id = allResourcesToIds.get(uri);
	return id == null ? null : id.intValue();
    }

    public DocumentCollection getCollection() {
	return documentCollection;
    }

    public Index getAlignmentIndex() {
	return alignmentIndex;
    }

    @Deprecated
    public Long lookupResourceId(CharSequence key) {
	return allResourcesToIds.get(key);
    }

    public synchronized String lookupResourceById(long id) {
	MutableString value = allIdsToResources.get((int) id);
	if (value != null) {
	    return value.toString();
	}
	return null;
    }

    public String getDefaultField() {
	return SUBJECT_INDEX_KEY;
    }

    public String getTitle(int id) throws IOException {
	String title;
	Document d = documentCollection.document(id);
	title = ((MutableString) d.title()).toString();
	d.close();
	return title;
    }

    public RDFIndexStatistics getStatistics() {
	return stats;
    }

    public RDFQueryParser getParser() {
	return parser;
    }

    public int process(final int offset, final int length, final ObjectArrayList<DocumentScoreInfo<Reference2ObjectMap<Index, SelectedInterval[]>>> results,
	    final Query... queries) throws QueryBuilderVisitorException, IOException {
	return queryEngine.copy().process(queries, offset, length, results);
    }

    public void destroy() {
	try {
	    if (documentCollection != null)
		documentCollection.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public Map<String, Integer> getPredicateTermDistribution() throws IOException {
	return predicateDistribution;
    }

    public Map<String, Integer> getTypeTermDistribution() throws IOException {
	return typeTermDistribution;
    }

    private Map<String, Integer> getTermDistribution(Index index, boolean termsAreResourceIds) throws IOException {
	StringMap<? extends CharSequence> termMap = null;
	if (index instanceof BitStreamIndex) {
	    termMap = ((BitStreamIndex) index).termMap;
	} else if (index instanceof QuasiSuccinctIndex) {
	    termMap = ((QuasiSuccinctIndex) index).termMap;
	}
	
	if (termMap == null) {
	    throw new IllegalArgumentException("termMap is null. Index is for field:" + index.field + ". Index class is:" + index.getClass().getSimpleName());
	}

	Map<String, Integer> histogram = new HashMap<String, Integer>();

	String hashValuePrefix = index.properties.getString(ResourceRefTermProcessor.PropertyKeys.REF_PREFIX, "@");
	
	for (CharSequence term : termMap.list()) {
	    long docId = termMap.get(term);
	    IndexIterator it = index.documents(((int) docId));
	    if (termsAreResourceIds) {
		String termString = term.toString();
		if (!termString.startsWith(hashValuePrefix)) {
		    throw new RuntimeException("Expected resource id " + termString + " to be prefix with " + hashValuePrefix);
		}
		int termAsId = Integer.parseInt(termString.substring(hashValuePrefix.length()));
		histogram.put(lookupResourceById(termAsId), it.frequency());
	    } else {
		histogram.put(term.toString(), it.frequency());
	    }
	    it.dispose();
	}
	return histogram;
    }

    public static class RDFIndexException extends Exception {
	private static final long serialVersionUID = -6825941506094477867L;

	public RDFIndexException(Exception e) {
	    super(e);
	}

	public RDFIndexException(String message) {
	    super(message);
	}

	public RDFIndexException(String message, Exception e) {
	    super(message, e);
	}
    }

    public InputStream getDocumentInputStream(int docId) throws IOException {
	return documentCollection.stream(docId);
    }

    public Integer getDocumentSize(int docId) {
	return getSubjectIndex().sizes.get(docId);
    }

    public Set<String> getIndexedPredicates() {
	return verticalPredicates;
    }
}
