package com.yahoo.glimmer.web;

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

import it.unimi.di.big.mg4j.query.nodes.Query;
import it.unimi.di.big.mg4j.query.nodes.QueryBuilderVisitorException;
import it.unimi.di.big.mg4j.query.parser.QueryParserException;
import it.unimi.di.big.mg4j.query.parser.SimpleParser;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.log4j.Logger;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.yahoo.glimmer.query.RDFIndex;
import com.yahoo.glimmer.query.RDFIndexStatistics;

@Controller()
public class QueryController {
    private final static Logger LOGGER = Logger.getLogger(QueryController.class);
    
    public final static String INDEX_KEY = "index";
    public final static String OBJECT_KEY = "object";
    
    private static final String DOC_PSEUDO_FIELD = "doc:";
    // This defines how resources are written in the command objects query string.
    private static final Pattern RESOURCE_PATTERN = Pattern.compile("(\\{(?:https?://[^}]+|_:[A-Za-z][A-Za-z0-9]*)\\})");

    private static final Integer DEFAULT_OBJECT_LENGTH_LIMIT = 300;

    private IndexMap indexMap;
    private Querier querier;
    private QueryFilter queryFilter;
    
    private Integer defaultObjectLengthLimit = DEFAULT_OBJECT_LENGTH_LIMIT;

    // / For every request populate the dataset model attribute from the request
    // parameter.
    @ModelAttribute(INDEX_KEY)
    public RDFIndex getIndex(@RequestParam(required = false) String index) {
	if (index != null) {
	    RDFIndex rdfIndex = indexMap.get(index);
	    if (rdfIndex == null) {
		throw new HttpMessageConversionException("No index found with name:" + index);
	    }
	    return rdfIndex;
	} else {
	    return null;
	}
    }

    @RequestMapping(value = "/dataSetList", method = RequestMethod.GET)
    public Map<String, ?> getDataSetList(@RequestParam(required = false) String callback) {
	return Collections.singletonMap(OBJECT_KEY, indexMap.keySet());
    }

    @RequestMapping(value = "/indexStatistics", method = RequestMethod.GET)
    public Map<String, ?> getIndexStatistics(@ModelAttribute(INDEX_KEY) RDFIndex index, @RequestParam(required = false) String callback) {
	if (index == null) {
	    throw new HttpMessageConversionException("No index given");
	}

	RDFIndexStatistics statistics = index.getStatistics();
	return Collections.singletonMap(OBJECT_KEY, statistics);
    }

    @RequestMapping(value = {"/query", "/v1/search"} , method = RequestMethod.GET)
    public Map<String, ?> query(@ModelAttribute(INDEX_KEY) RDFIndex index, @Valid QueryCommand command, HttpServletRequest httpServletRequest) throws QueryParserException,
	    QueryBuilderVisitorException, IOException {
	if (index == null) {
	    throw new HttpMessageConversionException("No index given.");
	}

	String query = command.getQuery();
	if (query == null || query.isEmpty()) {
	    throw new HttpMessageConversionException("No query given.");
	}
	
	QueryResult result;
	if (queryFilter != null && queryFilter.filter(query)) {
	    LOGGER.info("Blocking query:" + query + " from address:" + httpServletRequest.getRemoteAddr());
	    throw new HttpMessageConversionException("Bad query given.");
	}

	query = decodeEntities(command.getQuery()).trim();
	query = encodeResources(index, query);
	
	Query parsedQuery;
	switch (command.getType()) {
	case MG4J:
	    parsedQuery = new SimpleParser().parse(query);
	    result = querier.doQuery(index, parsedQuery, command.getPageStart(), command.getPageSize(), command.isDeref(), defaultObjectLengthLimit);
	    break;
	case YAHOO:
	    if (query.startsWith(DOC_PSEUDO_FIELD)) {
		String idOrSubject = query.substring(DOC_PSEUDO_FIELD.length());
		Long id;
		if (Character.isDigit(idOrSubject.charAt(0))) {
		    try {
			id = Long.parseLong(idOrSubject);
		    } catch (NumberFormatException e) {
			throw new IllegalArgumentException("Query " + query + " failed to parse as a numeric subject ID(int)");
		    }
		} else {
		    id = index.getSubjectId(idOrSubject);
		    if (id == null) {
			throw new IllegalArgumentException("subject " + idOrSubject + " is not in collection.");
		    }
		}
		result = querier.doQueryForDocId(index, id, command.isDeref(), null);
	    } else {
		try {
		    parsedQuery = index.getParser().parse(query);
		} catch (QueryParserException e) {
		    throw new IllegalArgumentException("Query failed to parse:" + query, e);
		}
		result = querier.doQuery(index, parsedQuery, command.getPageStart(), command.getPageSize(), command.isDeref(), defaultObjectLengthLimit);
	    }
	    break;
	default:
	    throw new IllegalArgumentException("No query type given.");
	}

	return Collections.singletonMap(OBJECT_KEY, result);
    }

    @ExceptionHandler(Exception.class)
    public Map<String, ?> handleException(Exception ex,  HttpServletRequest request, HttpServletResponse response) {
	if (!(ex instanceof HttpMessageConversionException)) {
	    LOGGER.error("Exception when processing:" + request.getQueryString(), ex);
	}
	response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
	return Collections.singletonMap(OBJECT_KEY, ex.getMessage());
    }

    private static String decodeEntities(String query) {
	if (query == null || query.equals("")) {
	    return "";
	}

	String result = query.replaceAll("&quot;", "\"");
	result = result.replaceAll("&#39;", "'");
	result = result.replaceAll("&#92;", "\\\\");
	return result;
    }
    
    /**
     * Replaces '{' + resourceString + '}' with '@' + resourceId.
     * 
     * @param index
     * @param query
     * @return re-written query.
     * @throws IllegalArgumentException when a resource that is not in the data set is found in the given query.
     */
    public static String encodeResources(RDFIndex index, String query) {
	Matcher resourceMatcher = RESOURCE_PATTERN.matcher(query);
	StringBuffer sb = new StringBuffer();
	while (resourceMatcher.find()) {
	    String resource = resourceMatcher.group(0);
	    // Remove { and }
	    resource = resource.substring(1, resource.length() - 1);
	    
	    // TODO. Objects that are Resources aren't converted to lower case during indexing but predicates are!
	    // (in PredicatePrefixTupleFilter)  Needs to be consistent.
	    String resourceId = index.lookupIdByResourceId(resource);
	    if (resourceId == null) {
		resourceId = index.lookupIdByResourceId(resource.toLowerCase());
		if (resourceId == null) {
		    throw new IllegalArgumentException("The resource " + resource + " isn't in the data set.");
		}
	    }
	    resourceMatcher.appendReplacement(sb, resourceId);
	 }
	resourceMatcher.appendTail(sb);
	
	return sb.toString();
    }

    @Resource
    public void setQuerier(Querier querier) {
	this.querier = querier;
    }

    @Resource
    public void setIndexMap(IndexMap indexMap) {
	this.indexMap = indexMap;
    }
    
    @Resource
    public void setQueryFilter(QueryFilter queryFilter) {
	this.queryFilter = queryFilter;
    }
    
    public void setDefaultObjectLengthLimit(Integer defaultObjectLengthLimit) {
	this.defaultObjectLengthLimit = defaultObjectLengthLimit;
    }
}

