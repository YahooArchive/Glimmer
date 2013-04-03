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

import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.di.big.mg4j.search.score.AbstractWeightedScorer;
import it.unimi.di.big.mg4j.search.score.BM25FScorer;
import it.unimi.di.big.mg4j.search.score.DelegatingScorer;
import it.unimi.di.big.mg4j.search.visitor.CounterCollectionVisitor;
import it.unimi.di.big.mg4j.search.visitor.CounterSetupVisitor;
import it.unimi.di.big.mg4j.search.visitor.TermCollectionVisitor;
import it.unimi.dsi.big.util.StringMap;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class WOOScorer extends AbstractWeightedScorer implements DelegatingScorer {
    private static final Logger LOGGER = Logger.getLogger(BM25FScorer.class);
    private static final boolean DEBUG = true;
    /** The default value used for the parameter <var>k</var><sub>1</sub>. */
    public final static double DEFAULT_K1 = 1.2;
    /** The default value used for the parameter <var>b</var>. */
    public final static double DEFAULT_B = 0.5;
    /**
     * The value of the document-frequency part for terms appearing in more than
     * half of the documents.
     */
    public final static double EPSILON_SCORE = 1E-6;

    /** The counter collection visitor used to estimate counts. */
    private final CounterCollectionVisitor counterCollectionVisitor;
    /** The counter setup visitor used to estimate counts. */
    private final CounterSetupVisitor setupVisitor;
    /** The term collection visitor used to estimate counts. */
    private final TermCollectionVisitor termVisitor;

    /** The parameter <var>k</var><sub>1</sub>. */
    public final double k1;
    /** The parameter <var>b</var>; you must provide one value for each index. */
    public final Reference2DoubleMap<Index> bByIndex;

    /** The parameter {@link #k1} plus one, precomputed. */
    private final double k1Plus1;
    /**
     * An array indexed by offsets that caches the inverse document-frequency
     * part of the formula, multiplied by the index weight.
     */
    private double[] idfPart;
    /**
     * An array indexed by offsets that caches the weight corresponding to each
     * pair.
     */
    private double[] offset2Weight;
    /**
     * An array indexed by offsets that gives the unique id of each term in the
     * query.
     */
    private int[] offset2TermId;
    /** A term map to index {@link #frequencies}. */
    private final StringMap<? extends CharSequence> termMap;
    /**
     * The list of virtual frequencies (possibly approximated using just the
     * frequencies of the main field).
     */
    private final LongBigList frequencies;
    /**
     * An array indexed by offsets mapping each offset to the corresponding
     * index number.
     */
    private int[] offset2Index;
    /**
     * An array indexed by term ids used by {@link #score()} to compute virtual
     * counts.
     */
    private double[] virtualCount;
    /**
     * For expected IDF runs, an array indexed by term ids used by
     * {@link #score()} to compute virtual counts combined with IDF scoring.
     */
    // private double[] virtualIdfCount;

    private long N;
    /**
     * An array (parallel to {@link #currIndex}) used by {@link #score()} to
     * cache the current document sizes.
     */
    // private int[] size;
    /** The weight of each index. */
    private double[] weight;
    /**
     * An array indexed by offsets mapping each offset to the parameter
     * <var>b</var> of the corresponding index.
     */
    private double[] index2B;
    private Object2DoubleMap<String> bByName;

    private double w_numberOfFieldsMatched;
    private IntBigList defaultSizes;
    private double averageDocLength;
    private double[] documentWeights;
    private final double dl_cutoff;
    private HashMap<Integer, Integer> documentPriors;
    private int max_number_of_fields;
    private int NEUTRAL = Integer.parseInt(SetDocumentPriors.NEUTRAL);

    /**
     * 
     * @param k1
     * @param b
     * @param termMap
     * @param frequencies
     * @param N
     *            number of documents
     * @param w_numberOfFieldsMatched
     */
    public WOOScorer(final double k1, final Reference2DoubleMap<Index> b, final StringMap<? extends CharSequence> termMap, final LongBigList frequencies,
	    final IntBigList defaultSizes, double averageDocLength, long N, double w_numberOfFieldsMatched, double[] documentWeights, double dl_cutoff,
	    HashMap<Integer, Integer> documentPriors, int max_number_of_fields) {
	this.termMap = termMap;
	termVisitor = new TermCollectionVisitor();
	setupVisitor = new CounterSetupVisitor(termVisitor);
	counterCollectionVisitor = new CounterCollectionVisitor(setupVisitor);
	this.k1 = k1;
	this.bByIndex = b;
	this.frequencies = frequencies;
	this.k1Plus1 = k1 + 1;
	this.bByName = null;
	this.w_numberOfFieldsMatched = w_numberOfFieldsMatched;
	this.N = N;
	this.defaultSizes = defaultSizes;
	this.averageDocLength = averageDocLength;
	this.documentWeights = documentWeights;
	this.dl_cutoff = dl_cutoff;
	this.max_number_of_fields = max_number_of_fields;
	this.documentPriors = documentPriors;
    }

    public DelegatingScorer copy() {
	final WOOScorer scorer = new WOOScorer(k1, bByIndex, termMap, frequencies, defaultSizes, averageDocLength, N, w_numberOfFieldsMatched, documentWeights,
		dl_cutoff, documentPriors, max_number_of_fields);
	scorer.setWeights(index2Weight);
	return scorer;
    }

    public double score() throws IOException {
	setupVisitor.clear();
	documentIterator.acceptOnTruePaths(counterCollectionVisitor);

	final long document = documentIterator.document();
	final int[] count = setupVisitor.count;
	final double[] offset2Weight = this.offset2Weight;
	final int[] offset2TermId = this.offset2TermId;
	final double[] idfPart = this.idfPart;
	final double[] virtualCount = this.virtualCount;
	// final double[] virtualIdfCount = this.virtualIdfCount;
	final double[] index2B = this.index2B;

	final double[] idf = new double[virtualCount.length];

	double docLen = defaultSizes.getInt(document);
	if (docLen < dl_cutoff)
	    docLen = dl_cutoff;

	// Compute virtual size
	int term2Index;
	// int termId;
	DoubleArrays.fill(virtualCount, 0);
	double score = 0, v;

	// System.out.println("Using b of "+index2B[ 0 ]);
	// if ( termMap != null ) {
	// int tmpTfCount = 0;
	for (int i = offset2TermId.length; i-- != 0;) {
	    if (offset2TermId[i] == -1)
		continue;
	    idf[offset2TermId[i]] = idfPart[i];
	    term2Index = offset2Index[i];
	    // virtualCount[ offset2TermId[ i ] ] += count[ i ] * offset2Weight[
	    // i ] / ( ( 1 - index2B[ term2Index ] ) + index2B[ term2Index ] *
	    // size[ term2Index ] / avgDocumentSize[ term2Index ] );

	    if (index2B[term2Index] != 1) {

		virtualCount[offset2TermId[i]] += (count[i] * offset2Weight[i]) / ((1 - index2B[term2Index]) + index2B[term2Index] * docLen / averageDocLength);
	    } else {
		virtualCount[offset2TermId[i]] += (count[i] * offset2Weight[i]);
	    }
	    // tmpTfCount += count[i];
	    // if(count[i]>0)
	    // System.out.println("docid="+documentIterator.document()+" idf="+idfPart[i]+" docLen="+defaultSizes.getInt(document)+" docLenUsed="+docLen+" avDocLen="+averageDocLength+" virtualCount="+virtualCount[
	    // offset2TermId[ i ]
	    // ]+" tf="+count[i]+" w="+offset2Weight[i]+" b="+index2B[
	    // term2Index ] +" offsetToTermId="+offset2TermId[ i ] );
	    // if(count[i]>0)
	    // System.out.println("docid="+documentIterator.document()+" idf="+idfPart[offset2TermId[
	    // i
	    // ]]+" docLen="+defaultSizes.getInt(document)+" docLenUsed="+docLen+" avDocLen="+averageDocLength+" virtualCount="+virtualCount[
	    // offset2TermId[ i ]
	    // ]+" tf="+count[i]+" w="+offset2Weight[i]+" b="+index2B[
	    // term2Index ] +" offsetToTermId="+offset2TermId[ i ] );
	}

	double numberOfFieldsMatched = 0;
	for (int i = virtualCount.length; i-- != 0;) {
	    v = virtualCount[i];
	    double idft = idf[i];
	    // System.out.println("i="+i+" k1plus1="+k1Plus1+" v="+v+" k1="+k1+" idfPart="+idft);
	    // System.out.println( (( k1Plus1 * v ) / ( v + k1 )) * idfPart[ i
	    // ]+" oldScore "+score);
	    score += (k1Plus1 * v) / (v + k1) * idft;
	    // System.out.println("new score="+score);
	    if (virtualCount[i] > 0)
		numberOfFieldsMatched++;
	}

	numberOfFieldsMatched = (numberOfFieldsMatched > max_number_of_fields) ? max_number_of_fields : numberOfFieldsMatched;
	score *= w_numberOfFieldsMatched * numberOfFieldsMatched / max_number_of_fields;
	// System.out.println("w_match="+w_numberOfFieldsMatched+" number_matched="+numberOfFieldsMatched+" max fields "+max_number_of_fields);

	// score += w_numberOfFieldsMatched * numberOfFieldsMatched;

	// System.out.println("Adding "+w_numberOfFieldsMatched+"*"+numberOfFieldsMatched);
	// documentPriors
	if (documentPriors != null) {
	    Integer doccategory = documentPriors.get(document);
	    if (doccategory != null)
		score *= documentWeights[doccategory];
	    else
		score *= documentWeights[NEUTRAL];
	    /*
	     * if (doccategory != null) {
	     * System.out.println("Adding (found) prior of category "
	     * +doccategory+" weight is "+ documentWeights[doccategory]);
	     * System.out.println("The weights are "+documentWeights);
	     * if(doccategory == Integer.parseInt(SetDocumentPriors.IMPORTANT))
	     * System.exit(-1); } //else
	     * System.out.println("Adding (not found) prior of "
	     * +documentWeights[NEUTRAL]);
	     */
	}

	// System.out.println("docid="+documentIterator.document()+" docLen="+defaultSizes.getInt(document)+" final score="+score+" tf="+tmpTfCount);
	return score;
    }

    public double score(final Index index) {
	throw new UnsupportedOperationException();
    }

    public void wrap(DocumentIterator documentIterator) throws IOException {
	super.wrap(documentIterator);

	if (index2Weight.keySet().contains(null)) {
	    throw new IllegalStateException("index2Weights contains a null key!!");
	}

	termVisitor.prepare(index2Weight.keySet());
	if (DEBUG)
	    LOGGER.debug("Weight map: " + index2Weight);

	documentIterator.accept(termVisitor);
	if (DEBUG)
	    LOGGER.debug("Term Visitor found " + termVisitor.numberOfPairs() + " leaves");

	final Index[] index = termVisitor.indices();
	if (DEBUG)
	    LOGGER.debug("Indices: " + Arrays.toString(index));
	if (!index2Weight.keySet().containsAll(Arrays.asList(index)))
	    throw new IllegalArgumentException("A WOOScorer scorer must have a weight for all indices involved in a query");

	for (Index i : index) {
	    if (bByIndex != null && !bByIndex.containsKey(i) || bByName != null && !bByName.containsKey(i.field)) {
		throw new IllegalArgumentException("A WOOScorer scorer must have a b parameter for all indices involved in a query " + i);
	    }
	}
	setupVisitor.prepare();
	documentIterator.accept(setupVisitor);

	weight = new double[index.length];
	for (int i = weight.length; i-- != 0;) {
	    weight[i] = index2Weight.getDouble(index[i]);
	}
	offset2TermId = setupVisitor.offset2TermId;
	offset2Index = setupVisitor.indexNumber;

	offset2Weight = new double[offset2Index.length];
	index2B = new double[index.length];
	for (int i = 0; i < index2B.length; i++)
	    index2B[i] = bByIndex != null ? bByIndex.getDouble(index[i]) : bByName.getDouble(index[i].field);

	for (int i = offset2Weight.length; i-- != 0;) {
	    offset2Weight[i] = index2Weight.getDouble(index[offset2Index[i]]) * index2Weight.size();
	}

	// We do all logs here
	idfPart = new double[termVisitor.numberOfPairs()];

	for (int i = idfPart.length; i-- != 0;) {
	    if (setupVisitor.offset2TermId[i] == -1)
		continue;
	    // TODO CAUTION ATOMIC BOMB
	    final int id = (int) termMap.getLong(setupVisitor.termId2Term[setupVisitor.offset2TermId[i]]);
	    /*
	     * if ( id == -1 ) throw new IllegalStateException(
	     * "The term map passed to a WOOScorer scorer must contain all terms appearing in all indices"
	     * ); final long f = frequencies.getLong( id ); idfPart[ i ] =
	     * Math.max( EPSILON_SCORE, Math.log( ( N - f + 0.5 ) / ( f + 0.5 )
	     * ) );
	     */
	    if (id == -1) {
		idfPart[i] = 0; // if the final score is not a X * idf the score
				// for unseen terms will not be zero!!!!!!!!!
	    } else {
		final long f = frequencies.getLong(id);
		idfPart[i] = Math.max(EPSILON_SCORE, Math.log((N - f + 0.5) / (f + 0.5)));
	    }
	    // System.out.println("i="+i+", frequency is "+f+" N is "+N);
	    // System.out.println("idf for term "+setupVisitor.termId2Term[
	    // setupVisitor.offset2TermId[ i ] ]+" is "+idfPart[i]);
	}
	virtualCount = new double[setupVisitor.termId2Term.length];
	// if (termMap == null) {
	// virtualIdfCount = new double[setupVisitor.termId2Term.length];
	// }
    }

    public boolean usesIntervals() {
	return false;
    }
}
