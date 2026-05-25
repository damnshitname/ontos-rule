package com.ontos.rule.core.backend.sql.translator;

import com.ontos.rule.core.backend.sql.dialect.SqlDialect;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CEL AST → SQL WHERE 子句翻译器。
 *
 * <p>遍历 dev.cel 的 AST 树，把每个节点翻译成对应 SQL 片段。
 * 不同方言通过 {@link SqlDialect} 接口注入，保持翻译逻辑跟方言解耦。
 *
 * <p>支持的 CEL 节点：
 * <ul>
 *   <li>Constant: int / double / string / bool / null</li>
 *   <li>Ident: 变量引用（翻译为字段名）</li>
 *   <li>Select: obj.field（翻译为 obj_field 或带引号字段）</li>
 *   <li>Call: 算术 / 比较 / 逻辑 / in / 三目 / size / matches / startsWith 等</li>
 *   <li>List: 字面量列表（用于 in 右侧）</li>
 * </ul>
 *
 * <p>不支持的节点（抛 TranslationException）：
 * <ul>
 *   <li>Comprehension（for 循环）</li>
 *   <li>Map / Struct 字面量</li>
 *   <li>用户自定义函数</li>
 * </ul>
 */
public class CelToSqlTranslator {

    private final SqlDialect dialect;

    public CelToSqlTranslator(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public String translate(CelAbstractSyntaxTree ast) {
        return translateExpr(ast.getExpr());
    }

    private String translateExpr(CelExpr expr) {
        CelExpr.ExprKind.Kind kind = expr.exprKind().getKind();
        return switch (kind) {
            case CONSTANT -> translateConstant(expr.constant());
            case IDENT -> dialect.quoteIdentifier(expr.ident().name());
            case SELECT -> translateSelect(expr);
            case CALL -> translateCall(expr);
            case LIST -> translateList(expr);
            default -> throw new TranslationException(
                "不支持的 CEL 节点类型: " + kind +
                " · SQL Backend 仅支持: CONSTANT/IDENT/SELECT/CALL/LIST。" +
                " 复杂逻辑请用 backend_hint=JVM 或写 expression_raw_sql 逃生通道。"
            );
        };
    }

    private String translateConstant(CelConstant c) {
        CelConstant.Kind ck = c.getKind();
        return switch (ck) {
            case NULL_VALUE -> "NULL";
            case BOOLEAN_VALUE -> c.booleanValue() ? "TRUE" : "FALSE";
            case INT64_VALUE -> String.valueOf(c.int64Value());
            case UINT64_VALUE -> c.uint64Value().toString();
            case DOUBLE_VALUE -> String.valueOf(c.doubleValue());
            case STRING_VALUE -> dialect.quoteString(c.stringValue());
            case BYTES_VALUE -> throw new TranslationException("BYTES 字面量暂不支持 SQL 翻译");
            default -> throw new TranslationException("未知常量类型: " + ck);
        };
    }

    private String translateSelect(CelExpr expr) {
        // obj.field → 用方言引号包字段名。简化处理：直接用 field 名（不展开 obj 前缀）
        // 真实场景应根据 schema mapping 处理
        CelExpr.CelSelect select = expr.select();
        return dialect.quoteIdentifier(select.field());
    }

    private String translateList(CelExpr expr) {
        CelExpr.CelList list = expr.list();
        return "(" + list.elements().stream()
            .map(this::translateExpr)
            .collect(Collectors.joining(", ")) + ")";
    }

    private String translateCall(CelExpr expr) {
        CelExpr.CelCall call = expr.call();
        String func = call.function();
        List<CelExpr> args = call.args();

        return switch (func) {
            // 比较
            case "_==_" -> binaryOp("=", args);
            case "_!=_" -> binaryOp("<>", args);
            case "_<_" -> binaryOp("<", args);
            case "_<=_" -> binaryOp("<=", args);
            case "_>_" -> binaryOp(">", args);
            case "_>=_" -> binaryOp(">=", args);

            // 算术
            case "_+_" -> binaryOp("+", args);
            case "_-_" -> binaryOp("-", args);
            case "_*_" -> binaryOp("*", args);
            case "_/_" -> binaryOp("/", args);
            case "_%_" -> binaryOp("%", args);

            // 逻辑
            case "_&&_" -> binaryOp("AND", args);
            case "_||_" -> binaryOp("OR", args);
            case "!_" -> "(NOT " + translateExpr(args.get(0)) + ")";

            // 一元负号
            case "-_" -> "(-" + translateExpr(args.get(0)) + ")";

            // in
            case "@in" -> translateInList(args);

            // 三目
            case "_?_:_" -> translateTernary(args);

            // 字符串/集合函数
            case "size" -> {
                String operand = call.target().isPresent()
                    ? translateExpr(call.target().get())
                    : translateExpr(args.get(0));
                yield dialect.lengthFunction(operand);
            }
            case "matches" -> {
                if (call.target().isPresent()) {
                    yield dialect.matchesFunction(translateExpr(call.target().get()), translateExpr(args.get(0)));
                }
                yield dialect.matchesFunction(translateExpr(args.get(0)), translateExpr(args.get(1)));
            }
            case "startsWith" -> {
                if (call.target().isPresent()) {
                    yield dialect.startsWithFunction(translateExpr(call.target().get()), translateExpr(args.get(0)));
                }
                yield dialect.startsWithFunction(translateExpr(args.get(0)), translateExpr(args.get(1)));
            }
            case "endsWith" -> {
                if (call.target().isPresent()) {
                    yield dialect.endsWithFunction(translateExpr(call.target().get()), translateExpr(args.get(0)));
                }
                yield dialect.endsWithFunction(translateExpr(args.get(0)), translateExpr(args.get(1)));
            }
            case "contains" -> {
                if (call.target().isPresent()) {
                    yield dialect.containsFunction(translateExpr(call.target().get()), translateExpr(args.get(0)));
                }
                yield dialect.containsFunction(translateExpr(args.get(0)), translateExpr(args.get(1)));
            }

            default -> throw new TranslationException(
                "不支持的函数: " + func + " · SQL Backend 当前支持: 算术/比较/逻辑/in/三目/size/matches/startsWith/endsWith/contains"
            );
        };
    }

    private String binaryOp(String op, List<CelExpr> args) {
        if (args.size() != 2) {
            throw new TranslationException("二元操作符 " + op + " 需要 2 个参数，实际: " + args.size());
        }
        return "(" + translateExpr(args.get(0)) + " " + op + " " + translateExpr(args.get(1)) + ")";
    }

    private String translateInList(List<CelExpr> args) {
        if (args.size() != 2) {
            throw new TranslationException("in 操作符需要 2 个参数");
        }
        CelExpr rhs = args.get(1);
        if (rhs.exprKind().getKind() != CelExpr.ExprKind.Kind.LIST) {
            throw new TranslationException("in 右侧暂只支持字面量列表（如 [\"A\",\"B\"]）");
        }
        return "(" + translateExpr(args.get(0)) + " IN " + translateExpr(rhs) + ")";
    }

    private String translateTernary(List<CelExpr> args) {
        if (args.size() != 3) {
            throw new TranslationException("三目操作符需要 3 个参数");
        }
        return "(CASE WHEN " + translateExpr(args.get(0)) +
            " THEN " + translateExpr(args.get(1)) +
            " ELSE " + translateExpr(args.get(2)) + " END)";
    }
}
