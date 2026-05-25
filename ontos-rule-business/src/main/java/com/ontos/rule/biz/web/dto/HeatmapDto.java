package com.ontos.rule.biz.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 热力图：dimensions × objects 二维数据。
 *
 * <pre>{@code
 * {
 *   "dimensions": ["completeness","uniqueness",...],
 *   "objects": ["Lot","WorkOrder",...],
 *   "cells": [
 *     ["Lot", "completeness", 95],
 *     ["Lot", "uniqueness", 78],
 *     ...
 *   ]
 * }
 * }</pre>
 */
public record HeatmapDto(
    List<String> dimensions,
    List<String> objects,
    List<Cell> cells
) {
    public record Cell(String objectId, String dimension, BigDecimal score) {}
}
