package com.smart.vision.core.processor;

import cn.hutool.core.collection.CollectionUtil;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBase;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_FIELD_NAME_BOOST;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_OCR_BOOST;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_TAG_BOOST;

@Component
public class FieldQueryProcessor implements QueryProcessor {

    @Override
    public void process(QueryContext context, SearchRequest.Builder builder) {
        invokeIfPresent(context::getKeyword, StringUtils::hasText, k -> builder.query(q -> q
                .bool(b -> b
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("ocrContent").query(k))).boost(DEFAULT_OCR_BOOST)))
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("tags").query(k))).boost(DEFAULT_TAG_BOOST)))
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("fileName").query(k))).boost(DEFAULT_FIELD_NAME_BOOST)))
                )
        ));
        invokeIfPresent(context::getFieldQuery, CollectionUtil::isNotEmpty, fq -> {
            BoolQuery.Builder query = QueryBuilders.bool();
            for (QueryContext.FieldQuery fieldQuery : fq) {
                Query.Kind kind = fieldQuery.getKind();
                String field = fieldQuery.getField();
                Object value = fieldQuery.getValue();
                QueryBase.AbstractBuilder<?> queryBuilder = fieldQuery.getQueryBuilder();
            }
            builder.query(query.build());
        });

    }
}
