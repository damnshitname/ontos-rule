package com.ontos.rule.core.compiler;

/**
 * 规则编译失败时抛出。
 *
 * <p>常见原因：
 * <ul>
 *   <li>CEL 语法错误</li>
 *   <li>类型不匹配（如 <code>value > "abc"</code> 中 value 是数字）</li>
 *   <li>引用了未声明的变量</li>
 * </ul>
 */
public class CompilationException extends RuntimeException {
    public CompilationException(String message) {
        super(message);
    }

    public CompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
