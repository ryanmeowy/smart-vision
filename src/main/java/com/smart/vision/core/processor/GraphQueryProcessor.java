package com.smart.vision.core.processor;

import cn.hutool.core.collection.CollectionUtil;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;
import com.smart.vision.core.model.dto.GraphTripleDTO;
import org.springframework.stereotype.Component;

@Component
public class GraphQueryProcessor implements QueryProcessor {

    @Override
    public void process(QueryContext context, SearchRequest.Builder builder) {
        invokeIfPresent(context::getGraphTriples, CollectionUtil::isNotEmpty, list -> {
            BoolQuery.Builder graphBool = new BoolQuery.Builder();
            for (GraphTripleDTO t : list) {
                graphBool.should(g -> g.nested(n -> n
                        .path("relations")
                        .query(nq -> nq.bool(nb -> nb
                                .must(m -> m.term(trm -> trm.field("relations.s").value(t.getS())))
                                .must(m -> m.term(trm -> trm.field("relations.p").value(t.getP())))
                                .must(m -> m.term(trm -> trm.field("relations.o").value(t.getO())))
                        ))
                ));
            }
            builder.query(sh -> sh.constantScore(c -> c.filter(graphBool.build()._toQuery()).boost(2.0f)));
        });
    }
}
