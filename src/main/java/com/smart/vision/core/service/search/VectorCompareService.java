package com.smart.vision.core.service.search;

import com.smart.vision.core.model.dto.VectorCompareResultDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for pairwise vector comparison.
 */
public interface VectorCompareService {

    VectorCompareResultDTO compare(String leftType,
                                   String leftText,
                                   MultipartFile leftFile,
                                   String rightType,
                                   String rightText,
                                   MultipartFile rightFile);
}
