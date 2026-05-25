package com.ontos.rule.biz.web;

import com.ontos.rule.biz.domain.Rule;
import com.ontos.rule.biz.service.RuleService;
import com.ontos.rule.biz.web.dto.CreateRuleRequest;
import com.ontos.rule.biz.web.dto.RuleDto;
import com.ontos.rule.biz.web.dto.RulePreviewRequest;
import com.ontos.rule.biz.web.dto.RulePreviewResponse;
import com.ontos.rule.biz.web.dto.UpdateRuleRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 规则 CRUD REST API。
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RuleDto> create(@Valid @RequestBody CreateRuleRequest req) {
        Rule r = service.create(req);
        return ResponseEntity.ok(RuleDto.from(r));
    }

    /** 实时预览（编辑器 debounce 调用） · 永不抛异常 */
    @PostMapping("/preview")
    public RulePreviewResponse preview(@RequestBody RulePreviewRequest req) {
        return service.preview(req);
    }

    @GetMapping
    public List<RuleDto> list() {
        return service.list().stream().map(RuleDto::from).toList();
    }

    @GetMapping("/{id}")
    public RuleDto get(@PathVariable String id) {
        return RuleDto.from(service.get(id));
    }

    @PutMapping("/{id}")
    public RuleDto update(@PathVariable String id, @RequestBody UpdateRuleRequest req) {
        return RuleDto.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
