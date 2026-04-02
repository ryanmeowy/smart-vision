package com.smart.vision.core.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_FIELD_NAME_BOOST;
import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_OCR_BOOST;
import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_RELATION_O_BOOST;
import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_RELATION_P_BOOST;
import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_RELATION_S_BOOST;
import static com.smart.vision.core.constant.EmbeddingConstant.DEFAULT_TAG_BOOST;

@Component
public class HybridSearchKeywordMatcher implements FieldMatcher {

    @Override
    public Optional<Query> match(String keyword) {
        return match(keyword, true);
    }

    public Optional<Query> match(String keyword, boolean enableOcr) {
        return Optional.of(Query.of(q -> q.bool(b -> {
            if (enableOcr) {
                b.should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("ocrContent").query(keyword))).boost(DEFAULT_OCR_BOOST)));
            }
            b.should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("tags").query(keyword))).boost(DEFAULT_TAG_BOOST)));
            b.should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("fileName").query(keyword))).boost(DEFAULT_FIELD_NAME_BOOST)));
            b.should(relationNestedQuery("relations.o", keyword, DEFAULT_RELATION_O_BOOST));
            b.should(relationNestedQuery("relations.s", keyword, DEFAULT_RELATION_S_BOOST));
            b.should(relationNestedQuery("relations.p", keyword, DEFAULT_RELATION_P_BOOST));
            return b;
        })));
    }

    private Query relationNestedQuery(String field, String keyword, float boost) {
        return Query.of(sh -> sh.constantScore(x -> x
                .filter(s -> s.nested(n -> n
                        .path("relations")
                        .query(nq -> nq.match(m -> m.field(field).query(keyword)))
                ))
                .boost(boost)
        ));
    }
}
