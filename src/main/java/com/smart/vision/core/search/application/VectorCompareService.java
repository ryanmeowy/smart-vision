package com.smart.vision.core.search.application;

import com.smart.vision.core.search.interfaces.rest.dto.VectorCompareResultDTO;
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
