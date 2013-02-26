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
package org.elasticsearch.plugin.opennlp;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.opennlp.OpenNlpMapper;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.service.opennlp.OpenNlpService;


public class RegisterOpenNlpType extends AbstractIndexComponent {

    @Inject
    public RegisterOpenNlpType(Index index, @IndexSettings Settings indexSettings, MapperService mapperService,
                                  AnalysisService analysisService, OpenNlpService openNlpService) {
        super(index, indexSettings);
        mapperService.documentMapperParser().putTypeParser("opennlp",
                new OpenNlpMapper.TypeParser(analysisService, openNlpService));
    }
}
