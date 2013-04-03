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
import it.unimi.di.big.mg4j.index.IndexIterator;
import it.unimi.di.big.mg4j.index.TermProcessor;
import it.unimi.di.big.mg4j.query.nodes.AbstractQueryBuilderVisitor;
import it.unimi.di.big.mg4j.query.nodes.Align;
import it.unimi.di.big.mg4j.query.nodes.And;
import it.unimi.di.big.mg4j.query.nodes.Consecutive;
import it.unimi.di.big.mg4j.query.nodes.Difference;
import it.unimi.di.big.mg4j.query.nodes.False;
import it.unimi.di.big.mg4j.query.nodes.LowPass;
import it.unimi.di.big.mg4j.query.nodes.MultiTerm;
import it.unimi.di.big.mg4j.query.nodes.Not;
import it.unimi.di.big.mg4j.query.nodes.Or;
import it.unimi.di.big.mg4j.query.nodes.OrderedAnd;
import it.unimi.di.big.mg4j.query.nodes.Prefix;
import it.unimi.di.big.mg4j.query.nodes.Query;
import it.unimi.di.big.mg4j.query.nodes.QueryBuilderVisitor;
import it.unimi.di.big.mg4j.query.nodes.QueryBuilderVisitorException;
import it.unimi.di.big.mg4j.query.nodes.Range;
import it.unimi.di.big.mg4j.query.nodes.Remap;
import it.unimi.di.big.mg4j.query.nodes.Select;
import it.unimi.di.big.mg4j.query.nodes.Term;
import it.unimi.di.big.mg4j.query.nodes.True;
import it.unimi.di.big.mg4j.query.nodes.Weight;
import it.unimi.di.big.mg4j.query.parser.QueryParser;
import it.unimi.di.big.mg4j.query.parser.QueryParserException;
import it.unimi.di.big.mg4j.query.parser.SimpleParser;
import it.unimi.di.big.mg4j.search.DocumentIterator;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

public class RDFQueryParser implements QueryParser {
    private final static Logger LOGGER = Logger.getLogger(RDFQueryParser.class);

    private SimpleParser parser;

    private Index alignmentIndex;
    private List<String> properties;
    private Map<String,String> fieldNameSuffixToFieldNameMap;
    private Set<String> fieldNamesSet;
    private String defaultField;
    private Map<String, ? extends TermProcessor> termProcessors;
    private Object2LongFunction<CharSequence> resourcesMap;
    private final static Pattern RESOURCE_PATTERN = Pattern.compile("\\((http://.*)\\)");
    private final static Pattern FIELD_NAME_PATTEN = Pattern.compile("(\\w+):");

    public RDFQueryParser(Index alignmentIndex, List<String> properties, Map<String,String> fieldNameSuffixToFieldNameMap, String defaultField,
	    final Map<String, ? extends TermProcessor> termProcessors, final Object2LongFunction<CharSequence> resourcesMap) {
	init(alignmentIndex, properties, new HashSet<String>(fieldNameSuffixToFieldNameMap.values()), fieldNameSuffixToFieldNameMap, defaultField, termProcessors, resourcesMap);
    }

    protected void init(Index alignmentIndex, List<String> properties, Set<String> fieldNamesSet, Map<String,String> fieldNameSuffixToFieldNameMap, String defaultField,
	    final Map<String, ? extends TermProcessor> termProcessors, final Object2LongFunction<CharSequence> resourcesMap) {
	this.alignmentIndex = alignmentIndex;
	this.properties = properties;
	this.fieldNameSuffixToFieldNameMap = fieldNameSuffixToFieldNameMap;
	this.fieldNamesSet = fieldNamesSet;
	this.defaultField = defaultField;
	this.termProcessors = termProcessors;
	this.resourcesMap = resourcesMap;
	parser = new SimpleParser(fieldNamesSet, defaultField, termProcessors);
    }

    public final static String cleanQuery(String query) {
	// Replace a dotted word such as www.yahoo.com with a phrase query
	// without dots
	String[] parts = query.split("[ ]+");
	String yahooQuery = "";
	for (String part : parts) {
	    if (part.contains(".")) {
		if (part.contains("\"")) {
		    // The query is already a phrase query
		    part = part.replaceAll("\\.", " ");
		} else {
		    // Make it a phrase query
		    part = part.replaceAll("\\.", " ");
		    if (!part.trim().equals(""))
			part = "\"" + part + "\"";
		}
	    }
	    yahooQuery += part + " ";
	}

	StringBuffer result = new StringBuffer();
	for (int i = 0; i < yahooQuery.length(); i++) {
	    if (!Character.isLetterOrDigit(yahooQuery.charAt(i)) && !(yahooQuery.charAt(i) == '"') && !(yahooQuery.charAt(i) == ':')) {
		result.append(" ");
	    } else {
		result.append(yahooQuery.charAt(i));
	    }
	}

	// Normalize whitespace
	return result.toString().trim().toLowerCase().replaceAll("[\\s]+", " ");
    }

    @Override
    public Query parse(String unparsed) throws QueryParserException {

	if (unparsed == null || unparsed.equals("")) {
	    throw new QueryParserException("Empty query");
	}

	Query query = null;

	try {
	    LOGGER.info("Unparsed query:" + unparsed);
	    // Replace the short field names there corresponding field name.
	    // name:tad -> http_xmlns_com_foaf_0_1_name:tad
	    Matcher m = FIELD_NAME_PATTEN.matcher(unparsed);
	    if (m.find()) {
		StringBuffer sb = new StringBuffer();
		do {
		    String fieldName = m.group(1);
		    String fullFieldName = fieldNameSuffixToFieldNameMap.get(fieldName);
		    if (fullFieldName != null) {
			m.appendReplacement(sb, fullFieldName);
		    } else {
			m.appendReplacement(sb, fieldName);
		    }
		    sb.append(':');
		} while (m.find());
		m.appendTail(sb);
		unparsed = sb.toString();
	    }
	    LOGGER.info("Query after feild name expansion:" + unparsed);
	    m = RESOURCE_PATTERN.matcher(unparsed);
	    if (m.find()) {
		StringBuffer sb = new StringBuffer();
		do {
		    m.appendReplacement(sb, Long.toString(resourcesMap.get(m.group(1))));
		} while (m.find());
		m.appendTail(sb);
		unparsed = sb.toString();
	    }
	    LOGGER.info("Query after resource encoding:" + unparsed);
	    query = parser.parse(unparsed);
	    LOGGER.info("Query as parsed by MG4J:" + query);
	    query = query.accept(new MyVisitor());
	    LOGGER.info("Query after expansion:" + query);

	} catch (QueryBuilderVisitorException e) {
	    throw new QueryParserException(e);
	}

	return query;
    }

    @Override
    public String escape(String token) {
	return parser.escape(token);
    }

    @Override
    public MutableString escape(MutableString token) {
	return parser.escape(token);
    }

    @Override
    public Query parse(MutableString query) throws QueryParserException {
	return parse(query.toString());
    }

    @Override
    public QueryParser copy() {
	return new RDFQueryParser(alignmentIndex, properties, fieldNameSuffixToFieldNameMap, defaultField, termProcessors, resourcesMap);
    }

    public class MyVisitor extends AbstractQueryBuilderVisitor<Query> {
	private boolean insideConsecutive = false;
	private boolean insideSelect = false;

	public Query[] newArray(int len) {
	    return new Query[len];
	}

	public QueryBuilderVisitor<Query> prepare() {
	    return this;
	}

	public boolean visitPre(Consecutive node) throws QueryBuilderVisitorException {
	    insideConsecutive = true;
	    return true;
	}

	public boolean visitPre(Select node) throws QueryBuilderVisitorException {
	    insideSelect = true;
	    return true;
	}

	@Override
	public Query visit(Term term) throws QueryBuilderVisitorException {

	    // Don't rewrite terms inside Consecutive
	    if (insideConsecutive || insideSelect) {
		return term;
	    }
	    // NOTE: this Term node might be already inside a Select
	    final ObjectArrayList<Query> disjuncts = new ObjectArrayList<Query>();

	    IndexIterator ii;
	    try {
		if (alignmentIndex != null) {
		    ii = alignmentIndex.documents(term.term);
		    if (ii.mayHaveNext()) {
			long f = ii.nextDocument();
			while (f != DocumentIterator.END_OF_LIST) {
			    if (fieldNamesSet.contains(properties.get((int)f))) {
				// System.err.println( "From vertical index: " +
				// properties.get( f ) );
				disjuncts.add(new Select(properties.get((int)f), term));
			    }
			    f = ii.nextDocument();
			}
		    }
		    ii.dispose();
		} else {
		    // No alignment index: we look in all fields
		    for (String field : fieldNamesSet) {
			disjuncts.add(new Select(field, term));
		    }

		}
		disjuncts.add(new Select(defaultField, term));
	    } catch (IOException e) {
		throw new QueryBuilderVisitorException(e);
	    }
	    if (disjuncts.size() > 1) {
		return new Or(disjuncts.toArray(new Query[disjuncts.size()]));
	    } else {
		return disjuncts.get(0);
	    }
	}

	@Override
	public Query visit(Prefix node) throws QueryBuilderVisitorException {
	    return node;
	}

	@Override
	public Query visit(Range node) throws QueryBuilderVisitorException {
	    return node;
	}

	@Override
	public Query visit(True node) throws QueryBuilderVisitorException {
	    return node;
	}

	@Override
	public Query visit(False node) throws QueryBuilderVisitorException {
	    return node;
	}

	public Query visitPost(And node, Query[] subNode) throws QueryBuilderVisitorException {
	    return new And(subNode);
	}

	public Query visitPost(Consecutive node, Query[] subNode) throws QueryBuilderVisitorException {
	    insideConsecutive = false;

	    if (insideSelect) {
		return new Consecutive(subNode);
	    }
	    // Create a disjunct of selects on all fields
	    final ObjectArrayList<Query> disjuncts = new ObjectArrayList<Query>();
	    for (String property : properties) {
		if (fieldNamesSet.contains(property)) {
		    disjuncts.add(new Select(property, new Consecutive(subNode)));
		}
	    }
	    disjuncts.add(new Select(defaultField, new Consecutive(subNode)));

	    return new Or(disjuncts.toArray(new Query[disjuncts.size()]));

	}

	public Query visitPost(OrderedAnd node, Query[] subNode) throws QueryBuilderVisitorException {
	    return new OrderedAnd(subNode);
	}

	public Query visitPost(Difference node, Query[] subNode) throws QueryBuilderVisitorException {
	    return new Difference(subNode[0], subNode[1]);
	}

	public Query visitPost(LowPass node, Query subNode) throws QueryBuilderVisitorException {
	    return new LowPass(subNode, node.k);
	}

	public Query visitPost(Not node, Query subNode) throws QueryBuilderVisitorException {
	    return new Not(subNode);
	}

	public Query visitPost(Or node, Query[] subNode) throws QueryBuilderVisitorException {
	    return new Or(subNode);
	}

	public Query visitPost(Align node, Query[] subNode) throws QueryBuilderVisitorException {
	    return new Align(subNode[0], subNode[1]);
	}

	public Query visitPost(MultiTerm node, Query[] subNode) throws QueryBuilderVisitorException {
	    return new Or(subNode);
	}

	public Query visitPost(Select node, Query subNode) throws QueryBuilderVisitorException {
	    insideSelect = false;
	    return new Select(node.index, subNode);
	}

	public Query visitPost(Remap node, Query subNode) throws QueryBuilderVisitorException {
	    return new Remap(subNode, node.indexRemapping);
	}

	public Query visitPost(Weight node, Query subNode) throws QueryBuilderVisitorException {
	    return new Weight(node.weight, subNode);
	}

	@Override
	public MyVisitor copy() {
	    return new MyVisitor();
	}
    }
}
