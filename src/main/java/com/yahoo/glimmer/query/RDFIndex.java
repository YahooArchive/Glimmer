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

import it.unimi.di.big.mg4j.document.DocumentCollection;
import it.unimi.di.big.mg4j.document.IdentityDocumentFactory;
import it.unimi.di.big.mg4j.index.BitStreamIndex;
import it.unimi.di.big.mg4j.index.DiskBasedIndex;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.Index.UriKeys;
import it.unimi.di.big.mg4j.index.IndexIterator;
import it.unimi.di.big.mg4j.index.QuasiSuccinctIndex;
import it.unimi.di.big.mg4j.index.TermProcessor;
import it.unimi.di.big.mg4j.query.QueryEngine;
import it.unimi.di.big.mg4j.query.SelectedInterval;
import it.unimi.di.big.mg4j.query.nodes.Query;
import it.unimi.di.big.mg4j.query.nodes.QueryBuilderVisitorException;
import it.unimi.di.big.mg4j.search.DocumentIteratorBuilderVisitor;
import it.unimi.di.big.mg4j.search.score.CountScorer;
import it.unimi.di.big.mg4j.search.score.DocumentScoreInfo;
import it.unimi.di.big.mg4j.search.score.Scorer;
import it.unimi.dsi.big.util.ImmutableExternalPrefixMap;
import it.unimi.dsi.big.util.LongBigListSignedStringMap;
import it.unimi.dsi.big.util.SemiExternalGammaBigList;
import it.unimi.dsi.big.util.StringMap;
import it.unimi.dsi.fastutil.BigList;
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
import it.unimi.dsi.sux4j.io.FileLinesBigList;
import it.unimi.dsi.sux4j.io.FileLinesList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.semanticweb.yars.nx.namespace.RDF;

import com.yahoo.glimmer.indexing.TitleListDocumentCollection;
import com.yahoo.glimmer.util.BlockCompressedDocumentCollection;
import com.yahoo.glimmer.util.Util;

public class RDFIndex {
    private final static Logger LOGGER = Logger.getLogger(RDFIndex.class);
    public final static int MAX_STEMMING = 1024;

    private final static String BASENAME_INDEX_PROPERTY_KEY = "basename";

    private final static String ALIGNMENT_INDEX_NAME = "alignment";
    private final static String SUBJECT_INDEX_KEY = "subject";
    private final static String SUBJECT_TEXT_INDEX_KEY = "subjectText";
    private final static String PREDICATE_INDEX_KEY = "predicate";
    private final static String OBJECT_INDEX_KEY = "object";
    private final static String CONTEXT_INDEX_KEY = "context";
    private static final String[] HORIZONTAL_INDECIES = new String[] {SUBJECT_INDEX_KEY, SUBJECT_TEXT_INDEX_KEY, PREDICATE_INDEX_KEY, OBJECT_INDEX_KEY, CONTEXT_INDEX_KEY};
    private static final String[] MANDITORY_HORIZONTAL_INDECIES = new String[] {PREDICATE_INDEX_KEY, OBJECT_INDEX_KEY};

    private final String indexName;
    /** The query engine. */
    private QueryEngine queryEngine;
    /** The document collection. */
    private DocumentCollection documentCollection = null;
    /** Term counts in the token index */
    protected SemiExternalGammaBigList frequencies = null;
    /** Document priors */
    protected HashMap<Integer, Integer> documentPriors = null;
    /** Map used to encode URIs for retrieving from the collection */
    protected Object2LongFunction<CharSequence> allResourcesToIds;
    /** Map used to decode URIs */
    protected FileLinesList allIdsToResources;
    /** The alignment index **/
    protected Index alignmentIndex;

    private String resourceIdPrefix = "@";

    private Map<String, Integer> predicateDistribution;
    private Map<String, Integer> typeTermDistribution;

    private Set<String> verticalPredicates;

    protected RDFIndexStatistics stats;

    protected RDFQueryParser parser;

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

    public RDFIndex(String indexName, Context context) throws RDFIndexException {
	this.indexName = indexName;
	
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
	String indexBasename = new File(kbRootPath, "bySubject").getAbsolutePath();
	try {
	    BlockCompressedDocumentCollection collection = new BlockCompressedDocumentCollection("bySubject", new IdentityDocumentFactory(), 100000);
	    collection.filename(indexBasename);
	    documentCollection = collection;
	} catch (IOException e) {
	    LOGGER.info("Couldn't open Bz2BlockIndexedDocumentCollection from " + indexBasename, e);
	}

	if (documentCollection == null) {
	    LOGGER.info("No collection specified, we will try to use a title list...");
	    File titleListFile = context.getTitleListFile();
	    if (titleListFile != null) {
		LOGGER.info("Loading titlelist from " + titleListFile.getPath());
		BigList<MutableString> titleList;
		try {
		    titleList = new FileLinesBigList(titleListFile.getPath(), "ASCII");
		    LOGGER.info("Loaded titlelist of size " + titleList.size() + ".");
		    documentCollection = new TitleListDocumentCollection(titleList);
		} catch (Exception e) {
		    throw new IllegalArgumentException("Failed to open TitleListDocumentCollection.", e);
		}
	    }
	}

	resourceIdPrefix = context.getResourceIdPrefix();

	// Load all resources hash function
	allResourcesToIds = loadObjectOfType(context.getAllResourcesMapFile());
	if (allResourcesToIds == null) {
	    LOGGER.warn("Warning, no resources map specified!");
	} else {
	    LOGGER.info("Loaded resourses map " + context.getAllResourcesMapFile().getPath() + " with " + allResourcesToIds.size() + " entries.");
	}

	try {
	    allResourcesToIds = new LongBigListSignedStringMap(allResourcesToIds, context.getAllResourcesSignatureFile().getPath());
	} catch (Exception e) {
	    throw new RDFIndexException("Exception while creating 'all' resources signed map", e);
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
	    String alignmentBasename = new File(verticalIndexDir, ALIGNMENT_INDEX_NAME).getPath();
	    alignmentIndex = Index.getInstance(alignmentBasename + "?mapped=1");
	    setTermMapDumpFile(alignmentIndex, alignmentBasename);
	} catch (Exception e) {
	    LOGGER.error("Failed to load alignment index", e);
	}

	// Load horizontal indexes
	indexMap.putAll(loadIndexesFromDir(horizontalIndexDir, true, context.getLoadIndexesInMemory()));

	for (String indexKey : MANDITORY_HORIZONTAL_INDECIES) {
	    if (!indexMap.containsKey(indexKey)) {
		throw new IllegalStateException("No " + indexKey + " index found.");
	    }
	}
	
	if (!indexMap.containsKey(CONTEXT_INDEX_KEY)) {
	    LOGGER.info("No context index found.");
	}

	// Loading frequencies
	Index objectIndex = indexMap.get(OBJECT_INDEX_KEY);
	String filename = (String) objectIndex.properties.getProperty(BASENAME_INDEX_PROPERTY_KEY);
	filename += DiskBasedIndex.FREQUENCIES_EXTENSION;
	try {
	    LOGGER.info("Loading frequencies from " + filename);
	    frequencies = new SemiExternalGammaBigList(new InputBitStream(filename), 1, objectIndex.numberOfTerms);
	    if (frequencies.size64() != objectIndex.numberOfDocuments) {
		LOGGER.warn("Loaded " + frequencies.size64() + " frequency values but objectIndex.numberOfDocuments is "
			+ objectIndex.numberOfDocuments);
	    }
	} catch (Exception e) {
	    throw new IllegalArgumentException("Failed to load frequences for objectText index from " + filename, e);
	}

	try {
	    predicateDistribution = Collections.unmodifiableMap(getTermDistribution(indexMap.get(PREDICATE_INDEX_KEY), true));
	    Index typeField = indexMap.get(Util.encodeFieldName(RDF.TYPE.toString()));
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
	
	for (String indexKey : HORIZONTAL_INDECIES) {
	    if (indexMap.containsKey(indexKey)) {
		fieldNameSuffixToFieldNameOrderedMap.put(indexKey, indexKey);
	    }
	}

	List<String> shortNames = Util.generateShortNames(indexedPredicatesOrdered, fieldNameSuffixToFieldNameOrderedMap.keySet(), '_');
	for (int i = 0; i < shortNames.size(); i++) {
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
	DocumentIteratorBuilderVisitor builderVisitor = new DocumentIteratorBuilderVisitor(indexMap, index2Parser, objectIndex, MAX_STEMMING);
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
	parser = new RDFQueryParser(getAlignmentIndex(), indexedPredicatesOrdered, fieldNameSuffixToFieldNameOrderedMap, OBJECT_INDEX_KEY,
		termProcessors, allResourcesToIds);
    }
    
    public String getIndexName() {
	return indexName;
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
	return loadIndicesFromSpec(indexBasenames, documentCollection.size(), index2Weight, loadDocSizes, indexOptionsmap);
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
    protected Object2ReferenceMap<String, Index> loadIndicesFromSpec(final List<String> indexBasenames, final long documentCollectionSize,
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

	    if (index.numberOfDocuments != documentCollectionSize) {
		throw new IllegalArgumentException("Index " + index + " has " + index.numberOfDocuments + " documents, but the document collection has size "
			+ documentCollectionSize);
	    }
	    
	    setTermMapDumpFile(index, indexBasename);

	    name2Index.put(index.field != null ? index.field : indexBasename, index);
	}
	return name2Index;
    }

    private void setTermMapDumpFile(final Index index, final String indexBasename) throws RDFIndexException {
	// See the section of the MG4J Manual entitled 'Setup Time'
	if (index.termMap instanceof ImmutableExternalPrefixMap) {
	    ImmutableExternalPrefixMap termMap = (ImmutableExternalPrefixMap) index.termMap;
	    try {
		termMap.setDumpStream(indexBasename + DiskBasedIndex.TERMMAP_EXTENSION + ".dump");
	    } catch (FileNotFoundException e) {
		throw new RDFIndexException("Failed to set dump file for index " + indexBasename, e);
	    }
	}
    }

    private Reference2DoubleOpenHashMap<Index> loadB(Context context) {
	Reference2DoubleOpenHashMap<Index> b = new Reference2DoubleOpenHashMap<Index>();

	double db = context.getB();

	for (String indexName : getIndexedFields()) {
	    // TODO load from file if needed
	    b.put(getField(indexName), db);
	}
	b.put(queryEngine.indexMap.get(OBJECT_INDEX_KEY), db);
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

	StringMap<? extends CharSequence> objectTermMap;
	Index objectIndex = getObjectIndex();
	if (objectIndex instanceof BitStreamIndex) {
	    objectTermMap = ((BitStreamIndex) objectIndex).termMap;
	} else if (objectIndex instanceof QuasiSuccinctIndex) {
	    objectTermMap = ((QuasiSuccinctIndex) objectIndex).termMap;
	} else {
	    throw new IllegalStateException("Subject index is not a BitStreamIndex. Don't know how to get its termMap.");
	}
	return new WOOScorer(context.getK1(), bByIndex, objectTermMap, frequencies, objectIndex.sizes, (double) objectIndex.numberOfOccurrences
		/ objectIndex.numberOfDocuments, objectIndex.numberOfDocuments, context.getWMatches(), documentWeights, context.getDlCutoff(),
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

    private Index getObjectIndex() {
	return queryEngine.indexMap.get(OBJECT_INDEX_KEY);
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
    public Long getSubjectId(String uri) throws IOException {
	Long id = allResourcesToIds.get(uri);
	if (id != null) {
	    // Check that the doc is a valid doc(has contents).. TODO could use
	    // the subjects signed hash here..
	    InputStream docStream = documentCollection.stream(id);
	    if (docStream.read() == -1) {
		id = null;
	    }
	    docStream.close();
	}
	return id;
    }

    public DocumentCollection getCollection() {
	return documentCollection;
    }

    public Index getAlignmentIndex() {
	return alignmentIndex;
    }

    public String lookupIdByResourceId(String key) {
	if (key.startsWith("_:")) {
	    key = key.substring(2);
	}
	Long id = allResourcesToIds.get(key);
	return id == null ? null : resourceIdPrefix + id.intValue();
    }

    public synchronized String lookupResourceById(long id) {
	MutableString value = allIdsToResources.get((int) id);
	if (value != null) {
	    return value.toString();
	}
	return null;
    }

    public String getDefaultField() {
	return OBJECT_INDEX_KEY;
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

	for (CharSequence term : termMap.list()) {
	    long docId = termMap.get(term);
	    IndexIterator it = index.documents(((int) docId));
	    int frequency = it.frequency() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) it.frequency();
	    if (termsAreResourceIds) {
		String termString = term.toString();
		if (!termString.startsWith(resourceIdPrefix)) {
		    throw new RuntimeException("Expected resource id " + termString + " to be prefix with " + resourceIdPrefix);
		}
		int termAsId = Integer.parseInt(termString.substring(resourceIdPrefix.length()));
		histogram.put(lookupResourceById(termAsId), frequency);
	    } else {
		histogram.put(term.toString(), frequency);
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

    public InputStream getDocumentInputStream(long docId) throws IOException {
	return documentCollection.stream(docId);
    }

    public Integer getDocumentSize(int docId) {
	return getObjectIndex().sizes.get(docId);
    }

    public Set<String> getIndexedPredicates() {
	return verticalPredicates;
    }
}
