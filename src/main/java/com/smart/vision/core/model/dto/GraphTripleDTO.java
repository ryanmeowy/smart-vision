package com.smart.vision.core.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * GraphTripleDTO class: represents a triple in a graph.
 * s (subject) represents the subject.
 * p (predicate) represents the predicate.
 * o (object) represents the object.
 */

@Data
@AllArgsConstructor
public class GraphTripleDTO {
    // Subject
    private String s;
    // Predicate
    private String p;
    // Object
    private String o;
}
