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
package org.elasticsearch.index.mapper.opennlp;

import org.elasticsearch.common.base.Joiner;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.mapper.*;
import org.elasticsearch.index.mapper.core.StringFieldMapper;
import org.elasticsearch.service.opennlp.OpenNlpService;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.index.mapper.MapperBuilders.stringField;

public class OpenNlpMapper implements Mapper {

    public static final String CONTENT_TYPE = "opennlp";

    public static class Builder extends Mapper.Builder<Builder, OpenNlpMapper> {

        private StringFieldMapper.Builder contentBuilder;
        private StringFieldMapper.Builder nameBuilder = stringField("name");
        private StringFieldMapper.Builder dateBuilder = stringField("date");
        private StringFieldMapper.Builder locationBuilder = stringField("location");
        private OpenNlpService openNlpService;


        public Builder(String name, OpenNlpService openNlpService) {
            super(name);
            this.openNlpService = openNlpService;
            this.contentBuilder = stringField(name);
            this.builder = this;
        }

        public Builder content(StringFieldMapper.Builder content) {
            this.contentBuilder = content;
            return this;
        }

        public Builder names(StringFieldMapper.Builder namesBuilder) {
            this.nameBuilder = namesBuilder;
            return this;
        }

        public Builder dates(StringFieldMapper.Builder datesBuilder) {
            this.dateBuilder = datesBuilder;
            return this;
        }

        public Builder locations(StringFieldMapper.Builder locationsBuilder) {
            this.locationBuilder = locationsBuilder;
            return this;
        }

        @Override
        public OpenNlpMapper build(BuilderContext context) {
            context.path().add(name);
            StringFieldMapper contentMapper = contentBuilder.build(context);
            StringFieldMapper nameMapper = nameBuilder.build(context);
            StringFieldMapper dateMapper = dateBuilder.build(context);
            StringFieldMapper locationMapper = locationBuilder.build(context);
            context.path().remove();

            return new OpenNlpMapper(name, openNlpService, contentMapper, nameMapper, dateMapper, locationMapper);
        }
    }

    public static class TypeParser implements Mapper.TypeParser {

        private AnalysisService analysisService;
        private OpenNlpService openNlpService;

        public TypeParser(AnalysisService analysisService, OpenNlpService openNlpService) {
            this.analysisService = analysisService;
            this.openNlpService = openNlpService;
        }

        @Override
        public Mapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            OpenNlpMapper.Builder builder = new Builder(name, openNlpService);

            for (Map.Entry<String, Object> entry : node.entrySet()) {
                String fieldName = entry.getKey();
                Object fieldNode = entry.getValue();

                if (fieldName.equals("fields")) {
                    Map<String, Object> fieldsNode = (Map<String, Object>) fieldNode;
                    for (Map.Entry<String, Object> fieldsEntry : fieldsNode.entrySet()) {
                        String propName = fieldsEntry.getKey();
                        Object propNode = fieldsEntry.getValue();

                        if (name.equals(propName)) {
                            builder.content((StringFieldMapper.Builder) parserContext.typeParser("string").parse(name, (Map<String, Object>) propNode, parserContext));
                        } else if ("name".equals(propName)) {
                            builder.names((StringFieldMapper.Builder) parserContext.typeParser("string").parse("name", (Map<String, Object>) propNode, parserContext));
                        } else if ("date".equals(propName)) {
                            builder.dates((StringFieldMapper.Builder) parserContext.typeParser("string").parse("date", (Map<String, Object>) propNode, parserContext));
                        } else if ("location".equals(propName)) {
                            builder.locations((StringFieldMapper.Builder) parserContext.typeParser("string").parse("location", (Map<String, Object>) propNode, parserContext));
                        }
                    }
                }

                if (fieldName.equals("person_analyzer")) {
                    builder.nameBuilder.searchAnalyzer(analysisService.analyzer(fieldNode.toString()));
                    builder.nameBuilder.indexAnalyzer(analysisService.analyzer(fieldNode.toString()));
                }

                if (fieldName.equals("date_analyzer")) {
                    builder.dateBuilder.searchAnalyzer(analysisService.analyzer(fieldNode.toString()));
                    builder.dateBuilder.indexAnalyzer(analysisService.analyzer(fieldNode.toString()));
                }

                if (fieldName.equals("location_analyzer")) {
                    builder.locationBuilder.searchAnalyzer(analysisService.analyzer(fieldNode.toString()));
                    builder.locationBuilder.indexAnalyzer(analysisService.analyzer(fieldNode.toString()));
                }
            }

            return builder;
        }
    }

    private final String name;
    private OpenNlpService openNlpService;
    private final StringFieldMapper contentMapper;
    private final StringFieldMapper nameMapper;
    private final StringFieldMapper dateMapper;
    private final StringFieldMapper locationMapper;

    public OpenNlpMapper(String name, OpenNlpService openNlpService, StringFieldMapper contentMapper, StringFieldMapper nameMapper,
                         StringFieldMapper dateMapper, StringFieldMapper locationMapper) {
        this.name = name;
        this.openNlpService = openNlpService;
        this.contentMapper = contentMapper;
        this.nameMapper = nameMapper;
        this.dateMapper = dateMapper;
        this.locationMapper = locationMapper;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void parse(ParseContext context) throws IOException {
        String content = null;

        XContentParser parser = context.parser();
        XContentParser.Token token = parser.currentToken();

        if (token == XContentParser.Token.VALUE_STRING) {
            content = parser.text();
        }

        context.externalValue(content);
        contentMapper.parse(context);

        Map<String, Set<String>> namedEntities = openNlpService.tokenize(content);

        Set<String> names = namedEntities.get("name");
        if (names != null && names.size() > 0) {
            String nameString = Joiner.on(" ").join(names);
            context.externalValue(nameString);
            nameMapper.parse(context);
        }

        Set<String> dates = namedEntities.get("date");
        if (dates != null && dates.size() > 0) {
            String dateString = Joiner.on(" ").join(dates);
            context.externalValue(dateString);
            dateMapper.parse(context);
        }

        Set<String> locations = namedEntities.get("location");
        if (locations != null && locations.size() > 0) {
            String locationString = Joiner.on(" ").join(locations);
            context.externalValue(locationString);
            locationMapper.parse(context);
        }
    }

    @Override
    public void merge(Mapper mergeWith, MergeContext mergeContext) throws MergeMappingException {
    }

    @Override
    public void traverse(FieldMapperListener fieldMapperListener) {
        contentMapper.traverse(fieldMapperListener);
        nameMapper.traverse(fieldMapperListener);
        dateMapper.traverse(fieldMapperListener);
        locationMapper.traverse(fieldMapperListener);
    }

    @Override
    public void traverse(ObjectMapperListener objectMapperListener) {
    }

    @Override
    public void close() {
        contentMapper.close();
        nameMapper.close();
        dateMapper.close();
        locationMapper.close();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(name);
        builder.field("type", CONTENT_TYPE);

        builder.startObject("fields");
        contentMapper.toXContent(builder, params);
        nameMapper.toXContent(builder, params);
        dateMapper.toXContent(builder, params);
        locationMapper.toXContent(builder, params);
        builder.endObject();

        builder.endObject();
        return builder;
    }
}
