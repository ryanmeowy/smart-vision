package com.smart.vision.core.ingestion.domain.model;

/**
 * Status of a single item inside batch task.
 */
public enum BatchTaskItemStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}

