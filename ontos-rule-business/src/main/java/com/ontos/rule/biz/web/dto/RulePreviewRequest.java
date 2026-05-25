package com.ontos.rule.biz.web.dto;

import java.util.List;

/**
 * 规则编辑器实时预览请求 —— 双形态都支持。
 *
 * <p>前端在编辑器里输入时 debounce 调用本接口，看到编译产物 / 检测变量 / 推断维度 / 语法错。
 */
public record RulePreviewRequest(
    /** 表单形态：维度卡片列表 */
    List<CheckSpec> formChecks,
    /** CEL 形态：自由表达式 */
    String expression,
    /** target，影响表单 → CEL 的列名替换 */
    String target,
    /** CEL 模式时用户填的维度（逗号分隔） */
    String dimensions
) {}
