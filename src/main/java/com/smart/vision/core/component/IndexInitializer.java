package com.smart.vision.core.component;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.smart.vision.core.config.VectorConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexInitializer {

    private final ElasticsearchClient esClient;
    private final VectorConfig vectorConfig;

    private static final String SETTINGS_PATH = "elasticsearch/es-settings.json";
    private static final String MAPPING_PATH = "elasticsearch/es-mapping.json";

    @PostConstruct
    public void init() {
        String indexName = vectorConfig.getIndexName();
        int dims = vectorConfig.getDimension();

        try {
            BooleanResponse exists = esClient.indices().exists(e -> e.index(indexName));
            if (exists.value()) {
                log.info("Index [{}] already exists, skipping initialization", indexName);
                return;
            }

            log.info("Starting index initialization [{}], vector dimension: {}, loading configuration file...", indexName, dims);

            InputStream settingsStream = new ClassPathResource(SETTINGS_PATH).getInputStream();
            
            String mappingJson = loadAndProcessMapping(dims);
            
            esClient.indices().create(c -> c
                .index(indexName)
                .settings(IndexSettings.of(s -> s.withJson(settingsStream)))
                .mappings(TypeMapping.of(m -> m.withJson(new StringReader(mappingJson))))
            );

            log.info("Index [{}] created successfully!", indexName);

        } catch (IOException e) {
            log.error("Index initialization failed", e);
            throw new RuntimeException("ES Index Init Failed");
        }
    }

    /**
     * Read the mapping file and replace the @DIMS@ placeholder.
     */
    private String loadAndProcessMapping(int dims) throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPING_PATH);
        String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return json.replace("\"@DIMS@\"", String.valueOf(dims));
    }
}