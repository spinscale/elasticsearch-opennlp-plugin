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

import org.elasticsearch.common.logging.log4j.LogConfigurator;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import java.io.IOException;

public class NodeTestHelper {

    public static Node createNode(String clusterName) throws IOException {
        ImmutableSettings.Builder settingsBuilder = ImmutableSettings.settingsBuilder();

        settingsBuilder.put("gateway.type", "none");
        settingsBuilder.put("cluster.name", clusterName);
        settingsBuilder.put("index.number_of_shards", 1);
        settingsBuilder.put("index.number_of_replicas", 1);
        settingsBuilder.put("opennlp.models.name.file", "src/test/resources/models/en-ner-person.bin");
        settingsBuilder.put("opennlp.models.date.file", "src/test/resources/models/en-ner-date.bin");
        settingsBuilder.put("opennlp.models.location.file", "src/test/resources/models/en-ner-location.bin");

        LogConfigurator.configure(settingsBuilder.build());

        return NodeBuilder.nodeBuilder().settings(settingsBuilder.build()).node();
    }
}
