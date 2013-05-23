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

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.yahoo.glimmer.query.RDFIndexStatistics.ClassStat;

public class RDFIndexStatisticsBuilderTest {
    private Map<String, String> sortedPredicates = new HashMap<String, String>();
    private Map<String, Integer> typeTermDistribution = new HashMap<String, Integer>();
    private InputStream ontologyIs;
    private Map<String, Integer> predicateTermDistribution;

    @Before
    public void before() {
	sortedPredicates.put("subject", "subject");
	sortedPredicates.put("predicate", "predicate");
	sortedPredicates.put("object", "object");
	sortedPredicates.put("type", "http_www_w3_org_1999_02_22_rdf_syntax_ns_type");
	sortedPredicates.put("domain", "http_schema_org_Property_domain");
	sortedPredicates.put("range", "http_schema_org_Property_range");
	sortedPredicates.put("subClassOf", "http_schema_org_Type_subClassOf");

	typeTermDistribution.put("http://schema.org/Thing", 100);

	typeTermDistribution.put("http://schema.org/LocalBusiness", 16);
	typeTermDistribution.put("http://schema.org/EntertainmentBusiness", 6);
	typeTermDistribution.put("http://schema.org/ArtGallery", 5);
	typeTermDistribution.put("http://schema.org/MovieTheater", 2);

	typeTermDistribution.put("http://schema.org/CreativeWork", 20);
	typeTermDistribution.put("http://schema.org/Article", 3);

	ontologyIs = RDFIndexStatisticsBuilderTest.class.getClassLoader().getResourceAsStream("schemaDotOrg.owl");

	predicateTermDistribution = new HashMap<String, Integer>();
	predicateTermDistribution.put("http_schema_org_url", 20);
	predicateTermDistribution.put("http_schema_org_description", 10);
	predicateTermDistribution.put("http_schema_org_name", 22);
	predicateTermDistribution.put("http_schema_org_awards", 1);
	predicateTermDistribution.put("http_schema_org_nonsense", 100);
    }

    @Test
    public void test() throws IOException {
	RDFIndexStatisticsBuilder builder = new RDFIndexStatisticsBuilder();
	builder.setSortedPredicates(sortedPredicates);
	builder.setTypeTermDistribution(typeTermDistribution);

	RDFIndexStatistics stats = builder.build();

	assertNotNull(stats.getFields());
	assertEquals(7, stats.getFields().size());
	assertEquals("http_www_w3_org_1999_02_22_rdf_syntax_ns_type", stats.getFields().get("type"));

	assertNotNull(stats.getClasses());
	assertEquals(7, stats.getClasses().size());
	assertTrue(stats.getClasses().containsKey("http://schema.org/CreativeWork"));
	assertEquals(20, stats.getClasses().get("http://schema.org/CreativeWork").getCount());
	assertNull(stats.getClasses().get("http://schema.org/CreativeWork").getLabel());
	assertNull(stats.getClasses().get("http://schema.org/CreativeWork").getChildren());
	assertNull(stats.getClasses().get("http://schema.org/CreativeWork").getProperties());

	assertNull(stats.getProperties());
	assertNull(stats.getRootClasses());

	builder.setOwlOntologyInputStream(ontologyIs);
	builder.setPredicateTermDistribution(predicateTermDistribution);
	stats = builder.build();

	assertNotNull(stats.getRootClasses());
	assertEquals(1, stats.getRootClasses().size());
	assertTrue(stats.getRootClasses().contains("http://schema.org/Thing"));
	ClassStat stat = stats.getClasses().get("http://schema.org/Thing");

	// System.out.println(RDFIndexStatisticsBuilder.toString(stats));

	// Assert ontology tree is correct..
	assertThing(stats, stat, "http://schema.org/description", "http://schema.org/name", "http://schema.org/url");

	assertNotNull(stats.getProperties());
	assertEquals(4, stats.getProperties().size());
	assertEquals((Integer) 20, stats.getProperties().get("http://schema.org/url"));
	assertEquals((Integer) 10, stats.getProperties().get("http://schema.org/description"));
	assertEquals((Integer) 22, stats.getProperties().get("http://schema.org/name"));
	assertEquals((Integer) 1, stats.getProperties().get("http://schema.org/awards"));
    }

    private static void assertThing(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	assertClassStat(stat, "Thing", 152, properties);
	Iterator<String> ci = stat.getChildren().iterator();
	assertCreativeWork(stats, stats.getClasses().get(ci.next()), properties);
	assertOrganization(stats, stats.getClasses().get(ci.next()), properties);
	assertPlace(stats, stats.getClasses().get(ci.next()), properties);
	assertFalse(ci.hasNext());
    }

    private static void assertCreativeWork(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	properties = concat(properties, "http://schema.org/awards", "http://schema.org/contentRating", "http://schema.org/datePublished",
		"http://schema.org/genre", "http://schema.org/headline", "http://schema.org/inLanguage", "http://schema.org/interactionCount",
		"http://schema.org/keywords");
	assertClassStat(stat, "CreativeWork", 23, properties);
	Iterator<String> ci = stat.getChildren().iterator();
	assertArticle(stats, stats.getClasses().get(ci.next()), properties);
	assertFalse(ci.hasNext());
    }

    private static void assertArticle(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	properties = concat(properties, "http://schema.org/articleBody", "http://schema.org/articleSection");
	assertClassStat(stat, "Article", 3, properties);
	assertNull(stat.getChildren());
    }

    private static void assertOrganization(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	properties = concat(properties, "http://schema.org/email", "http://schema.org/faxNumber", "http://schema.org/foundingDate",
		"http://schema.org/interactionCount", "http://schema.org/telephone");
	assertClassStat(stat, "Organization", 29, properties);
	Iterator<String> ci = stat.getChildren().iterator();
	assertLocalBusiness(stats, stats.getClasses().get(ci.next()), properties);
	assertFalse(ci.hasNext());
    }

    private static void assertLocalBusiness(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	// Has this inherits from more than one class explicitly define the
	// properties.
	String[] explicitProperties = new String[] { "http://schema.org/currenciesAccepted", "http://schema.org/description", "http://schema.org/email",
		"http://schema.org/faxNumber", "http://schema.org/foundingDate", "http://schema.org/interactionCount", "http://schema.org/maps",
		"http://schema.org/name", "http://schema.org/paymentAccepted", "http://schema.org/priceRange", "http://schema.org/telephone",
		"http://schema.org/url" };

	assertTrue(Arrays.asList(explicitProperties).containsAll(Arrays.asList(properties)));
	assertClassStat(stat, "LocalBusiness", 29, explicitProperties);
	Iterator<String> ci = stat.getChildren().iterator();
	assertEntertainmentBusiness(stats, stats.getClasses().get(ci.next()), explicitProperties);
	assertFalse(ci.hasNext());
    }

    private static void assertEntertainmentBusiness(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	assertClassStat(stat, "EntertainmentBusiness", 13, properties);
	Iterator<String> ci = stat.getChildren().iterator();
	assertArtGallery(stats, stats.getClasses().get(ci.next()), properties);
	assertMovieTheater(stats, stats.getClasses().get(ci.next()), properties);
	assertFalse(ci.hasNext());
    }

    private static void assertArtGallery(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	assertNull(stat.getChildren());
	assertClassStat(stat, "ArtGallery", 5, properties);
    }

    private static void assertMovieTheater(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	assertNull(stat.getChildren());
	// Has this inherits from more than one class explicitly define the
	// properties.
	String[] explicitProperties = new String[] { "http://schema.org/currenciesAccepted", "http://schema.org/description", "http://schema.org/email",
		"http://schema.org/faxNumber", "http://schema.org/foundingDate", "http://schema.org/interactionCount", "http://schema.org/maps",
		"http://schema.org/name", "http://schema.org/paymentAccepted", "http://schema.org/priceRange", "http://schema.org/telephone",
		"http://schema.org/url" };

	assertTrue(Arrays.asList(explicitProperties).containsAll(Arrays.asList(properties)));
	assertClassStat(stat, "MovieTheater", 2, explicitProperties);
    }

    private static void assertPlace(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	properties = concat(properties, "http://schema.org/maps", "http://schema.org/faxNumber", "http://schema.org/interactionCount",
		"http://schema.org/telephone");
	assertClassStat(stat, "Place", 29, properties);
	Iterator<String> ci = stat.getChildren().iterator();
	assertCivicStructure(stats, stats.getClasses().get(ci.next()), properties);
	assertLocalBusiness(stats, stats.getClasses().get(ci.next()), properties);
	assertFalse(ci.hasNext());
    }

    private static void assertCivicStructure(RDFIndexStatistics stats, ClassStat stat, String... properties) {
	assertClassStat(stat, "CivicStructure", 2, properties);
	Iterator<String> ci = stat.getChildren().iterator();
	assertMovieTheater(stats, stats.getClasses().get(ci.next()), properties);
	assertFalse(ci.hasNext());
    }

    private static void assertClassStat(ClassStat stat, String label, int count, String... properties) {
	assertNotNull(stat);

	// System.out.println(stat.getLabel() + ':' + stat.getCount() + '/' +
	// stat.getInheritedCount() + " (" + count + ")");
	assertEquals(label, stat.getLabel());
	// assertEquals(count, stat.getCount());
	if (stat.getProperties() != null) {
	    assertArrayEquals(properties, stat.getProperties().toArray());
	} else {
	    assertEquals(0, properties.length);
	}
    }

    private static String[] concat(String[] a, String... values) {
	String[] newA = new String[a.length + values.length];
	for (int i = 0; i < a.length; i++) {
	    newA[i] = a[i];
	}
	for (int i = 0; i < values.length; i++) {
	    newA[a.length + i] = values[i];
	}
	Arrays.sort(newA);
	return newA;
    }
}
