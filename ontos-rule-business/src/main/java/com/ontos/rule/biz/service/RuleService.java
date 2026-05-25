package com.ontos.rule.biz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontos.rule.biz.domain.Rule;
import com.ontos.rule.biz.domain.RuleStatus;
import com.ontos.rule.biz.domain.Severity;
import com.ontos.rule.biz.repo.RuleRepository;
import com.ontos.rule.biz.web.dto.CheckSpec;
import com.ontos.rule.biz.web.dto.CreateRuleRequest;
import com.ontos.rule.biz.web.dto.RulePreviewRequest;
import com.ontos.rule.biz.web.dto.RulePreviewResponse;
import com.ontos.rule.biz.web.dto.UpdateRuleRequest;
import com.ontos.rule.core.RuleEngine;
import com.ontos.rule.core.compiler.CompilationException;
import com.ontos.rule.core.model.Backend;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 规则 CRUD 服务（支持双形态）。
 *
 * <p>表单形态：传 formChecks → RuleTypeRegistry 编译成 CEL → 校验语法 → 入库
 * CEL 形态：直接传 expression → 校验语法 → 入库
 */
@Service
public class RuleService {

    private final RuleRepository repo;
    private final RuleEngine engine;
    private final RuleTypeRegistry typeRegistry;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger seq = new AtomicInteger(0);

    public RuleService(RuleRepository repo, RuleEngine engine, RuleTypeRegistry typeRegistry) {
        this.repo = repo;
        this.engine = engine;
        this.typeRegistry = typeRegistry;
    }

    @Transactional
    public Rule create(CreateRuleRequest req) {
        ResolvedRule resolved = resolveExpression(req.formChecks(), req.expression(), req.dimensions(), req.target());
        validateCel(resolved.expression);

        String id = (req.id() == null || req.id().isBlank()) ? nextId() : req.id();
        if (repo.existsById(id)) {
            throw new IllegalArgumentException("规则 ID 已存在: " + id);
        }
        Rule r = new Rule(
            id, req.name(), req.target(), resolved.expression,
            parseSeverity(req.severity()), parseBackend(req.backendHint()),
            RuleStatus.ACTIVE, req.owner() != null ? req.owner() : "system"
        );
        r.setFormChecksJson(resolved.formChecksJson);
        r.setDimensions(resolved.dimensions);
        return repo.save(r);
    }

    @Transactional
    public Rule update(String id, UpdateRuleRequest req) {
        Rule r = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("规则不存在: " + id));
        if (req.name() != null) r.setName(req.name());
        if (req.target() != null) r.setTarget(req.target());

        if (req.formChecks() != null || req.expression() != null) {
            // target 可能本轮被修改了，用最新值
            String effectiveTarget = req.target() != null ? req.target() : r.getTarget();
            ResolvedRule resolved = resolveExpression(req.formChecks(), req.expression(), req.dimensions(), effectiveTarget);
            validateCel(resolved.expression);
            r.setExpression(resolved.expression);
            r.setFormChecksJson(resolved.formChecksJson);
            r.setDimensions(resolved.dimensions);
        }
        if (req.severity() != null) r.setSeverity(parseSeverity(req.severity()));
        if (req.backendHint() != null) r.setBackendHint(parseBackend(req.backendHint()));
        if (req.status() != null) r.setStatus(RuleStatus.valueOf(req.status().toUpperCase()));
        if (req.owner() != null) r.setOwner(req.owner());
        r.setUpdatedAt(Instant.now());
        return repo.save(r);
    }

    /**
     * 实时预览：把 formChecks/expression 编译成 CEL，提取变量，推断维度，校验语法。
     * 永不抛异常 —— 失败时 valid=false，避免编辑器按键时弹 toast。
     */
    public RulePreviewResponse preview(RulePreviewRequest req) {
        boolean hasForm = req.formChecks() != null && !req.formChecks().isEmpty();
        boolean hasCel = req.expression() != null && !req.expression().isBlank();

        if (!hasForm && !hasCel) {
            return new RulePreviewResponse("", List.of(), List.of(), false,
                "请填写 CEL 表达式或勾选维度卡片");
        }

        String compiledCel;
        List<String> dims;
        try {
            if (hasForm) {
                compiledCel = typeRegistry.compileChecks(req.formChecks(),
                    req.target() != null ? req.target() : "");
                dims = new java.util.ArrayList<>(typeRegistry.inferDimensions(req.formChecks()));
            } else {
                compiledCel = req.expression();
                dims = (req.dimensions() == null || req.dimensions().isBlank())
                    ? List.of("validity")
                    : java.util.Arrays.stream(req.dimensions().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
            }
        } catch (IllegalArgumentException e) {
            return new RulePreviewResponse("", List.of(), List.of(), false, e.getMessage());
        }

        // 编译 + 提取变量
        try {
            var compiled = engine.compile(compiledCel);
            List<String> vars = compiled.variables().stream()
                .filter(v -> !v.startsWith("_"))   // 过滤 _now 等内部注入变量
                .sorted()
                .toList();
            return new RulePreviewResponse(compiledCel, vars, dims, true, null);
        } catch (CompilationException e) {
            return new RulePreviewResponse(compiledCel, List.of(), dims, false, e.getMessage());
        }
    }

    public List<Rule> list() { return repo.findAll(); }

    public Rule get(String id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("规则不存在: " + id));
    }

    @Transactional
    public void delete(String id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("规则不存在: " + id);
        repo.deleteById(id);
    }

    /**
     * 处理双形态：决定最终 expression / formChecksJson / dimensions。
     */
    private ResolvedRule resolveExpression(List<CheckSpec> formChecks, String expression,
                                           String dimensions, String target) {
        boolean hasForm = formChecks != null && !formChecks.isEmpty();
        boolean hasCel = expression != null && !expression.isBlank();
        if (hasForm && hasCel) {
            throw new IllegalArgumentException("formChecks 和 expression 互斥，只能传一个");
        }
        if (!hasForm && !hasCel) {
            throw new IllegalArgumentException("formChecks 或 expression 必须传一个");
        }

        ResolvedRule out = new ResolvedRule();
        if (hasForm) {
            out.expression = typeRegistry.compileChecks(formChecks, target);
            try {
                out.formChecksJson = mapper.writeValueAsString(formChecks);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("formChecks 序列化失败: " + e.getMessage());
            }
            // 自动推断维度
            out.dimensions = String.join(",", typeRegistry.inferDimensions(formChecks));
        } else {
            out.expression = expression;
            out.formChecksJson = null;
            out.dimensions = dimensions != null ? dimensions : "validity";  // 默认归 validity
        }
        return out;
    }

    private void validateCel(String expression) {
        try {
            engine.compile(expression);
        } catch (CompilationException e) {
            throw new IllegalArgumentException("CEL 编译失败: " + e.getMessage());
        }
    }

    private String nextId() {
        return String.format("QR-%04d", seq.incrementAndGet());
    }

    private Severity parseSeverity(String s) {
        try { return Severity.valueOf(s.toUpperCase()); }
        catch (Exception e) { throw new IllegalArgumentException("severity 必须是 ERROR 或 WARN，实际: " + s); }
    }

    private Backend parseBackend(String s) {
        try { return Backend.valueOf(s.toUpperCase()); }
        catch (Exception e) { throw new IllegalArgumentException("backendHint 必须是 AUTO/JVM/SQL/SPARK，实际: " + s); }
    }

    private static class ResolvedRule {
        String expression;
        String formChecksJson;
        String dimensions;
    }
}
