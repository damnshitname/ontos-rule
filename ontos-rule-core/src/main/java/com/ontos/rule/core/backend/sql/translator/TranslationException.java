package com.ontos.rule.core.backend.sql.translator;

/**
 * CEL AST → SQL 翻译失败时抛出。
 *
 * <p>典型原因：
 * <ul>
 *   <li>CEL 节点类型不支持 SQL 翻译（如 comprehension、map 字面量）</li>
 *   <li>方言不支持某个函数</li>
 *   <li>表达式引用了未声明的字段</li>
 * </ul>
 */
public class TranslationException extends RuntimeException {
    public TranslationException(String message) {
        super(message);
    }

    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
