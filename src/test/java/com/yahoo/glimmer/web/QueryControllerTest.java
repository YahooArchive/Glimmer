package com.yahoo.glimmer.web;

import static org.junit.Assert.*;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.glimmer.query.RDFIndex;

public class QueryControllerTest {
    private Mockery context;
    private RDFIndex index;
    
    @Before
    public void before() {
	context = new Mockery();
	context.setImposteriser(ClassImposteriser.INSTANCE);
	index = context.mock(RDFIndex.class);
    }
   
    
    @Test
    public void encodeResourcesTest() {
	context.checking(new Expectations(){{
	    allowing(index).lookupIdByResourceId("http://schema.org/Blog");
	    will(returnValue("@1"));
	    allowing(index).lookupIdByResourceId("https://somesite/path?p1=a&p2=b");
	    will(returnValue("@22"));
	    allowing(index).lookupIdByResourceId("_:node1234");
	    will(returnValue("@333"));
	}});
	String query = "";
	assertEquals("", QueryController.encodeResources(index, query));
	query = "not a resource";
	assertEquals("not a resource", QueryController.encodeResources(index, query));
	query = "type:not_a_resource";
	assertEquals("type:not_a_resource", QueryController.encodeResources(index, query));
	query = "http://schema.org/Blog";
	assertEquals("http://schema.org/Blog", QueryController.encodeResources(index, query));
	query = "type:<https://somesite/path?p1=a&p2=b>";
	assertEquals("type:@22", QueryController.encodeResources(index, query));
	query = "type:<_:node1234>";
	assertEquals("type:@333", QueryController.encodeResources(index, query));
	query = "type:<http://schema.org/Blog> <_:node1234>";
	assertEquals("type:@1 @333", QueryController.encodeResources(index, query));
    }
}
