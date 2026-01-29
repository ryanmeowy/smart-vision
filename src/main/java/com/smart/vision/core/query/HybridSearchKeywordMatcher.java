package com.smart.vision.core.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.stereotype.Component;

import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_FIELD_NAME_BOOST;
import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_OCR_BOOST;
import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_TAG_BOOST;

@Component
public class HybridSearchKeywordMatcher implements FieldMatcher {

    @Override
    public Query match(String keyword) {
        return Query.of(q -> q.bool(b -> b
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("ocrContent").query(keyword))).boost(DEFAULT_OCR_BOOST)))
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("tags").query(keyword))).boost(DEFAULT_TAG_BOOST)))
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("fileName").query(keyword))).boost(DEFAULT_FIELD_NAME_BOOST)))
                )
        );
    }
}
