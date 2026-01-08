package com.smart.vision.core.processor;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

import static com.smart.vision.core.constant.CommonConstant.*;

@Component
public class KeywordProcessor implements QueryProcessor {

    @Override
    public void process(QueryContext context, SearchRequest.Builder builder) {
        invokeIfPresent(context::getKeyword, StringUtils::hasText, keywordQuery(builder));

    }

    private Consumer<String> keywordQuery(SearchRequest.Builder builder) {
        return k -> builder.query(q -> q
                .bool(b -> b
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("ocrContent").query(k))).boost(DEFAULT_OCR_BOOST)))
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("tags").query(k))).boost(DEFAULT_TAG_BOOST)))
                        .should(sh -> sh.constantScore(x -> x.filter(s -> s.match(m -> m.field("fileName").query(k))).boost(DEFAULT_FIELD_NAME_BOOST)))
                )
        );
    }
}
