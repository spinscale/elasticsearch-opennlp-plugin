/*
 * Copyright 2008-2011 Grant Ingersoll, Thomas Morton and Drew Farris
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * -------------------
 * To purchase or learn more about Taming Text, by Grant Ingersoll, Thomas Morton and Drew Farris, visit
 * http://www.manning.com/ingersoll
 */
package org.elasticsearch.service.opennlp.models;

import opennlp.tools.util.Span;

public class TextAnnotation implements Comparable<TextAnnotation> {
    private Span span;
    private String type;
    private double prob;

    public TextAnnotation(String type, Span span, double prob) {
        this.span = span;
        this.type = type;
        this.prob = prob;
    }

    public Span getSpan() {
        return span;
    }

    public String getType() {
        return type;
    }

    public double getProb() {
        return prob;
    }

    public int compareTo(TextAnnotation a) {
        int c = span.compareTo(a.span);
        if (c == 0) {
            c = Double.compare(prob, a.prob);
            if (c == 0) {
                c = type.compareTo(a.type);
            }
        }
        return c;
    }

    public String toString() {
        return type + " " + span + " " + prob;
    }
}