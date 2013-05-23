package com.yahoo.glimmer.web;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Test;

public class PhraseListQueryFilterTest {

    @Test
    public void simpleTest() throws IOException {
	PhraseListQueryFilter queryFilter = new PhraseListQueryFilter();
	queryFilter.load(new StringInputStream("block this query\n"));
	
	assertFalse(queryFilter.filter(null));
	assertFalse(queryFilter.filter(""));
	assertFalse(queryFilter.filter("okay"));
	assertFalse(queryFilter.filter("block"));
	assertFalse(queryFilter.filter("this"));
	assertFalse(queryFilter.filter("query"));
	assertFalse(queryFilter.filter("block this"));
	assertFalse(queryFilter.filter("this query"));
	assertTrue(queryFilter.filter("block this query"));
	assertTrue(queryFilter.filter("also block this query"));
	assertTrue(queryFilter.filter("and block this query too"));
    }
    
    @Test
    public void test() throws IOException {
	PhraseListQueryFilter queryFilter = new PhraseListQueryFilter();
	queryFilter.load(new StringInputStream(
		"block this query\n" + 
		"block this too\n" +
		"block also this\n" +
		"also block this\n" +
		"block this\n"));
	
	assertFalse(queryFilter.filter(null));
	assertFalse(queryFilter.filter(""));
	assertFalse(queryFilter.filter("okay"));
	assertFalse(queryFilter.filter("block"));
	assertFalse(queryFilter.filter("this"));
	assertFalse(queryFilter.filter("query"));
	assertTrue(queryFilter.filter("block this"));
	assertFalse(queryFilter.filter("this query"));
	assertTrue(queryFilter.filter("block this query"));
	assertTrue(queryFilter.filter("also block this query"));
	assertTrue(queryFilter.filter("and block this query too"));
	
	System.out.println(queryFilter.toString());
    }
}
