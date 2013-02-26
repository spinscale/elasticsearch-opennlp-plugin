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

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.base.Joiner;
import org.elasticsearch.common.collect.Maps;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.service.opennlp.models.PooledTokenNameFinderModel;
import org.elasticsearch.service.opennlp.models.TextAnnotation;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

/*
 * Most of the stuff here is taken from https://github.com/tamingtext/book
 * Thanks for this book
 */
public class SimpleNlpTest {

    String[] sentences = {
            "Former first lady Nancy Reagan was taken to a " +
                    "suburban Los Angeles " +
                    "hospital as a precaution Sunday after a " +
            "fall at her home, an " +
            "aide said. ",
            "The 86-year-old Reagan will remain overnight for " +
                    "observation at a hospital in Santa Monica, California, " +
                    "said Joanne " +
                    "Drake, chief of staff for the Reagan Foundation."};

    Tokenizer tokenizer = SimpleTokenizer.INSTANCE;
    String[] names = {"person","location","date"};
    NameFinderME[] finders;

    @Test
    public void testThatMultipleFindersWork() throws Exception {
        loadFinders();
        Map<String, Set<String>> namedEntities = Maps.newHashMap();

        for (int si = 0; si < sentences.length; si++) {
            List<TextAnnotation> allTextAnnotations = new ArrayList<TextAnnotation>();
            String[] tokens = tokenizer.tokenize(sentences[si]);
            for (int fi = 0; fi < finders.length; fi++) {
                Span[] spans = finders[fi].find(tokens);
                double[] probs = finders[fi].probs(spans);
                for (int ni = 0; ni < spans.length; ni++) {
                    allTextAnnotations.add(new TextAnnotation(names[fi], spans[ni], probs[ni]));
                }
            }
            removeConflicts(allTextAnnotations);
            convertTextAnnotationsToNamedEntities(tokens, allTextAnnotations, namedEntities);
        }

        assertThat(namedEntities.get("person"), hasSize(3));
        assertThat(namedEntities.get("person"), containsInAnyOrder("Nancy Reagan", "Reagan", "Joanne Drake"));

        assertThat(namedEntities.get("location"), hasSize(3));
        assertThat(namedEntities.get("location"), containsInAnyOrder("Los Angeles", "Santa Monica", "California"));

        assertThat(namedEntities.get("date"), hasSize(1));
        assertThat(namedEntities.get("date"), containsInAnyOrder("Sunday"));
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




    public void loadFinders() throws Exception {
        finders = new NameFinderME[names.length];
        StopWatch sw = new StopWatch("Loading models").start();
        for (int mi = 0; mi < names.length; mi++) {
            finders[mi] = new NameFinderME(
                new PooledTokenNameFinderModel(
                    new FileInputStream(
                        new File("src/test/resources/models", "en-ner-"
                        + names[mi] + ".bin"))));
        }
        sw.stop();
    }

    private void removeConflicts(List<TextAnnotation> allTextAnnotations) {
        java.util.Collections.sort(allTextAnnotations);
        List<TextAnnotation> stack = new ArrayList<TextAnnotation>();
        stack.add(allTextAnnotations.get(0));
        for (int ai = 1; ai < allTextAnnotations.size(); ai++) {
            TextAnnotation curr = (TextAnnotation) allTextAnnotations.get(ai);
            boolean deleteCurr = false;
            for (int ki = stack.size() - 1; ki >= 0; ki--) {
                TextAnnotation prev = (TextAnnotation) stack.get(ki);
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
