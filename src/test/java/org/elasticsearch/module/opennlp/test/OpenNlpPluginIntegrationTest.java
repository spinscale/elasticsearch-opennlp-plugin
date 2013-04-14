/**
 * Copyright (C) 2013 Alexander Reelsen <alr@spinscale.de>
 *
 * This file is part of elasticsearch-plugin-opennlp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.elasticsearch.module.opennlp.test;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.RandomStringGenerator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.elasticsearch.module.opennlp.test.NodeTestHelper.createNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class OpenNlpPluginIntegrationTest {

    private String clusterName = RandomStringGenerator.randomAlphabetic(10);
    private String index = RandomStringGenerator.randomAlphabetic(6).toLowerCase();
    private String type = "someType"; // as defined in the sample mapping files
    private Node node;

    @Before
    public void startNode() throws Exception {
        node = createNode(clusterName).start();

        CreateIndexResponse createIndexResponse = new CreateIndexRequestBuilder(node.client().admin().indices())
                .setIndex(index).execute().actionGet();
        assertThat(createIndexResponse.isAcknowledged(), is(true));
    }

    @After
    public void stopNode() throws Exception {
        if (!node.isClosed()) {
            node.close();
        }
    }

    @Test
    public void testThatIndexingMakesNlpFieldsSearchable() throws Exception {
        putMapping("/test-mapping.json");

        String sampleText = copyToStringFromClasspath("/sample-text.txt");
        IndexResponse indexResponse = indexElement(sampleText);

        SearchResponse searchResponse = query(QueryBuilders.termQuery("someField.name", "jack"));
        assertThat(searchResponse.getHits().totalHits(), is(1L));
        assertThat(searchResponse.getHits().getAt(0).id(), is(indexResponse.getId()));

        searchResponse = query(QueryBuilders.termQuery("someFieldAnalyzed.name", "jack"));
        assertThat(searchResponse.getHits().totalHits(), is(1L));
        assertThat(searchResponse.getHits().getAt(0).id(), is(indexResponse.getId()));
    }

    @Test
    public void testThatOwnAnalyzersCanBeDefinedPerNlpMappedField() throws IOException {
        putMapping("/test-mapping-analyzers.json");

        String sampleText = copyToStringFromClasspath("/sample-text.txt");
        IndexResponse indexResponse = indexElement(sampleText);

        SearchResponse nonAnalyzedFieldSearchResponse = query(QueryBuilders.termQuery("someField.name", "jack"));
        assertThat(nonAnalyzedFieldSearchResponse.getHits().totalHits(), is(1L));

        // analyzed, therefore not resulting anything like the above query
        SearchResponse analyzedFieldSearchResponse = query(QueryBuilders.termQuery("someFieldAnalyzed.name", "jack"));
        assertThat(analyzedFieldSearchResponse.getHits().totalHits(), is(0L));

        SearchResponse searchResponse = query(QueryBuilders.prefixQuery("someFieldAnalyzed.name", "Jack"));
        assertThat(searchResponse.getHits().totalHits(), is(1L));

        searchResponse = query(QueryBuilders.matchQuery("someFieldAnalyzed.name", "Jack Nicholson"));
        assertThat(searchResponse.getHits().totalHits(), is(1L));
        assertThat(searchResponse.getHits().getAt(0).id(), is(indexResponse.getId()));
    }

    @Test
    public void testThatFacetingIsWorking() throws Exception {
        putMapping("/test-mapping-analyzers.json");

        String sampleText = copyToStringFromClasspath("/sample-text.txt");
        IndexResponse indexResponse = indexElement(sampleText);

        SearchResponse searchResponse = new SearchRequestBuilder(node.client()).setIndices(index).setTypes(type)
                .setQuery(QueryBuilders.matchAllQuery())
                .addFacet(new TermsFacetBuilder("names").field("someFieldAnalyzed.name").order(TermsFacet.ComparatorType.TERM))
                .execute().actionGet();
        assertThat(searchResponse.getHits().totalHits(), is(1L));
        assertThat(searchResponse.getHits().getAt(0).id(), is(indexResponse.getId()));
        TermsFacet termsFacet = searchResponse.getFacets().facet(TermsFacet.class, "names");
        assertThat(termsFacet.getTotalCount(), is(2L));
        assertThat(termsFacet.getEntries().get(0).getTerm().string(), is("Jack Nicholson"));
        assertThat(termsFacet.getEntries().get(1).getTerm().string(), is("Kobe Bryant"));
    }

    private SearchResponse query(QueryBuilder queryBuilder) {
        return new SearchRequestBuilder(node.client()).setIndices(index).setTypes(type).setQuery(queryBuilder).execute().actionGet();
    }

    private void putMapping(String mappingFile) throws IOException {
        String mapping = copyToStringFromClasspath(mappingFile);
        PutMappingRequestBuilder putMappingRequestBuilder = new PutMappingRequestBuilder(node.client().admin().indices());
        PutMappingResponse putMappingResponse = putMappingRequestBuilder.setIndices(index).setType(type)
                .setSource(mapping).execute().actionGet();
        assertThat(putMappingResponse.isAcknowledged(), is(true));
    }

    private IndexResponse indexElement(String value) {
        IndexResponse indexResponse = node.client().prepareIndex(index, type)
                .setSource("someFieldAnalyzed", value, "someField", value)
                .setRefresh(true)
                .execute().actionGet();
        assertThat(indexResponse.getId(), is(notNullValue()));
        return indexResponse;
    }
}
