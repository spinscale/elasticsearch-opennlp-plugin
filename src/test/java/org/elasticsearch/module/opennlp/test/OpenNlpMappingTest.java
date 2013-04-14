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

import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.logging.log4j.LogConfigurator;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.analysis.AnalyzerProviderFactory;
import org.elasticsearch.index.analysis.AnalyzerScope;
import org.elasticsearch.index.analysis.PreBuiltAnalyzerProviderFactory;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.DocumentMapperParser;
import org.elasticsearch.index.mapper.opennlp.OpenNlpMapper;
import org.elasticsearch.service.opennlp.OpenNlpService;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class OpenNlpMappingTest {

    private DocumentMapperParser mapperParser;

    @Before
    public void setupMapperParser() {
        Index index = new Index("test");

        Map<String, AnalyzerProviderFactory> analyzerFactoryFactories = Maps.newHashMap();
        analyzerFactoryFactories.put("keyword", new PreBuiltAnalyzerProviderFactory("keyword", AnalyzerScope.INDEX, new KeywordAnalyzer()));
        AnalysisService analysisService = new AnalysisService(index, ImmutableSettings.Builder.EMPTY_SETTINGS, null, analyzerFactoryFactories, null, null, null);

        mapperParser = new DocumentMapperParser(index, analysisService);
        Settings settings = settingsBuilder()
                .put("opennlp.models.name.file", "src/test/resources/models/en-ner-person.bin")
                .put("opennlp.models.date.file", "src/test/resources/models/en-ner-date.bin")
                .put("opennlp.models.location.file", "src/test/resources/models/en-ner-location.bin")
                .build();

        LogConfigurator.configure(settings);

        OpenNlpService openNlpService = new OpenNlpService(settings);
        openNlpService.start();
        mapperParser.putTypeParser(OpenNlpMapper.CONTENT_TYPE, new OpenNlpMapper.TypeParser(analysisService, openNlpService));
    }

    @Test
    public void testSimpleMappings() throws Exception {
        String mapping = copyToStringFromClasspath("/test-mapping.json");
        DocumentMapper docMapper = mapperParser.parse(mapping);

        String sampleText = copyToStringFromClasspath("/sample-text.txt");
        BytesReference json = jsonBuilder().startObject().field("_id", 1).field("someField", sampleText).endObject().bytes();
        Document doc = docMapper.parse(json).rootDoc();

        assertThat(doc.get(docMapper.mappers().smartName("someField").mapper().names().indexName()), is(sampleText));
        assertThat(doc.getFieldables("someField.name").length, is(2));
        assertThat(doc.getFieldables("someField.name")[0].stringValue(), is("Jack Nicholson"));
        assertThat(doc.getFieldables("someField.name")[1].stringValue(), is("Kobe Bryant"));
        assertThat(doc.get(docMapper.mappers().smartName("someField.date").mapper().names().indexName()), is("tomorrow"));
        assertThat(doc.get(docMapper.mappers().smartName("someField.location").mapper().names().indexName()), is("Munich"));

        // re-parse it
        String builtMapping = docMapper.mappingSource().string();
        docMapper = mapperParser.parse(builtMapping);

        json = jsonBuilder().startObject().field("_id", 1).field("someField", sampleText).endObject().bytes();
        doc = docMapper.parse(json).rootDoc();

        assertThat(doc.get(docMapper.mappers().smartName("someField").mapper().names().indexName()), is(sampleText));
        assertThat(doc.getFieldables("someField.name").length, is(2));
        assertThat(doc.getFieldables("someField.name")[0].stringValue(), is("Jack Nicholson"));
        assertThat(doc.getFieldables("someField.name")[1].stringValue(), is("Kobe Bryant"));
        assertThat(doc.get(docMapper.mappers().smartName("someField.date").mapper().names().indexName()), is("tomorrow"));
        assertThat(doc.get(docMapper.mappers().smartName("someField.location").mapper().names().indexName()), is("Munich"));
    }

    @Test
    public void testAnalyzedOpenNlpFieldMappings() throws IOException {
        String mapping = copyToStringFromClasspath("/test-mapping-keywordanalyzer.json");
        DocumentMapper docMapper = mapperParser.parse(mapping);
        String message = String.format("\"name\":{\"type\":\"string\",\"analyzer\":\"keyword\"}");
        assertThat(docMapper.mappingSource().string(), containsString(message));
    }
}
