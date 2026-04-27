package com.smart.vision.core.search.domain.port;

import com.smart.vision.core.search.interfaces.rest.dto.GraphTripleDTO;

import java.util.List;

/**
 * Domain port for parsing graph triples from user query text.
 */
public interface QueryGraphParserPort {

    /**
     * Parse text query into graph triples.
     *
     * @param keyword user query keyword
     * @return parsed triples, empty when nothing matched
     */
    List<GraphTripleDTO> parseFromKeyword(String keyword);
}
