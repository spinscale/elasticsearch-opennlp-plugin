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
package org.elasticsearch.service.opennlp;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.base.Joiner;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.service.opennlp.models.PooledTokenNameFinderModel;
import org.elasticsearch.service.opennlp.models.TextAnnotation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OpenNlpService extends AbstractLifecycleComponent<OpenNlpService> {

    private static Map<String, NameFinderME> finders = Maps.newHashMap();

    @Inject public OpenNlpService(Settings settings) {
        super(settings);
    }

    @Override
    protected void doStart() throws ElasticSearchException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        new Thread(new LoaderRunnable(settings, "opennlp.models.name.file", "name",  countDownLatch)).start();
        new Thread(new LoaderRunnable(settings, "opennlp.models.date.file", "date",  countDownLatch)).start();
        new Thread(new LoaderRunnable(settings, "opennlp.models.location.file", "location",  countDownLatch)).start();
        try {
            countDownLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {}
    }

    @Override
    protected void doStop() throws ElasticSearchException {}

    @Override
    protected void doClose() throws ElasticSearchException {}

    class LoaderRunnable implements Runnable {

        private Settings settings;
        private String configParameter;
        private String type;
        private CountDownLatch countDownLatch;

        public LoaderRunnable(Settings settings, String configParameter, String type, CountDownLatch countDownLatch) {
            this.settings = settings;
            this.configParameter = configParameter;
            this.type = type;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            String filePath = settings.get(configParameter, "");
            if (filePath.length() == 0) {
                logger.error("OpenNLP property [{}] is not set.", configParameter);
                return;
            }

            File modelFile = new File(filePath);
            if (!modelFile.exists() || !modelFile.canRead()) {
                logger.error("Model file {} does not exist.", modelFile);
                return;
            }

            StopWatch sw = new StopWatch("Loading model " + filePath).start();
            try {
                finders.put(type, new NameFinderME(
                        new PooledTokenNameFinderModel(
                                new FileInputStream(modelFile))));
            } catch (IOException e) {
                logger.error("Error loading model file {}: {}", e, modelFile, e.getMessage());
            } finally {
                sw.stop();
            }
            logger.info("Loaded file {} in {}", modelFile, sw.totalTime());
            countDownLatch.countDown();
        }
    }

    public Map<String, Set<String>> tokenize(String content) {
        Map<String, Set<String>> namedEntities = Maps.newHashMap();

        List<TextAnnotation> allTextAnnotations = new ArrayList<TextAnnotation>();
        String[] tokens = SimpleTokenizer.INSTANCE.tokenize(content);
        for (Map.Entry<String, NameFinderME> finderEntry : finders.entrySet()) {
            String type = finderEntry.getKey();
            NameFinderME finder = finderEntry.getValue();

            Span[] spans = finder.find(tokens);
            double[] probs = finder.probs(spans);

            for (int ni = 0; ni < spans.length; ni++) {
                allTextAnnotations.add(new TextAnnotation(type, spans[ni], probs[ni]));
            }
        }

        if (allTextAnnotations.size() > 0 ) {
            removeConflicts(allTextAnnotations);
        }
        convertTextAnnotationsToNamedEntities(tokens, allTextAnnotations, namedEntities);

        return namedEntities;
    }

    public void convertTextAnnotationsToNamedEntities(String[] tokens, List<TextAnnotation> TextAnnotations, Map<String, Set<String>> namedEntities) {
        for (TextAnnotation TextAnnotation : TextAnnotations) {
            int start = TextAnnotation.getSpan().getStart();
            int end = TextAnnotation.getSpan().getEnd();
            String[] TextAnnotationData = Arrays.copyOfRange(tokens, start, end);
            String content = Joiner.on(" ").join(TextAnnotationData);

            String type = TextAnnotation.getType();
            if (!namedEntities.containsKey(type)) {
                Set<String> typeList = Sets.newHashSet();
                namedEntities.put(type, typeList);
            }

            namedEntities.get(type).add(content);
        }
    }

    /* Copied from https://github.com/tamingtext/book/blob/master/src/test/java/com/tamingtext/opennlp/NameFinderTest.java */
    private void removeConflicts(List<TextAnnotation> allTextAnnotations) {
        java.util.Collections.sort(allTextAnnotations);
        List<TextAnnotation> stack = new ArrayList<TextAnnotation>();
        stack.add(allTextAnnotations.get(0));
        for (int ai = 1; ai < allTextAnnotations.size(); ai++) {
            TextAnnotation curr = allTextAnnotations.get(ai);
            boolean deleteCurr = false;
            for (int ki = stack.size() - 1; ki >= 0; ki--) {
                TextAnnotation prev = stack.get(ki);
                if (prev.getSpan().equals(curr.getSpan())) {
                    if (prev.getProb() > curr.getProb()) {
                        deleteCurr = true;
                        break;
                    } else {
                        allTextAnnotations.remove(stack.remove(ki));
                        ai--;
                    }
                } else if (prev.getSpan().intersects(curr.getSpan())) {
                    if (prev.getProb() > curr.getProb()) {
                        deleteCurr = true;
                        break;
                    } else {
                        allTextAnnotations.remove(stack.remove(ki));
                        ai--;
                    }
                } else if (prev.getSpan().contains(curr.getSpan())) {
                    break;
                } else {
                    stack.remove(ki);
                }
            }
            if (deleteCurr) {
                allTextAnnotations.remove(ai);
                ai--;
                deleteCurr = false;
            } else {
                stack.add(curr);
            }
        }
    }

}
