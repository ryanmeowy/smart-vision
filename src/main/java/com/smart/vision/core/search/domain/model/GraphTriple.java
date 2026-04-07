package com.smart.vision.core.search.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model for graph triples stored in search documents.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphTriple {
    private String s;
    private String p;
    private String o;
}
