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

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.service.opennlp.OpenNlpService;

import java.util.Collection;

import static org.elasticsearch.common.collect.Lists.newArrayList;

public class OpenNlpPlugin extends AbstractPlugin {

    public String name() {
        return "opennlp";
    }

    public String description() {
        return "OpenNLP Type Plugin";
    }

    @SuppressWarnings("rawtypes")
    @Override public Collection<Class<? extends LifecycleComponent>> services() {
        Collection<Class<? extends LifecycleComponent>> services = newArrayList();
        services.add(OpenNlpService.class);
        return services;
    }

    public Collection<Class<? extends Module>> indexModules() {
        Collection<Class<? extends Module>> modules = newArrayList();
        modules.add(OpenNlpIndexModule.class);
        return modules;
    }
}
