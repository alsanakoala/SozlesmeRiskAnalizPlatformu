package com.riskguard.interfaces.rest;

import com.riskguard.domain.model.RulePack;
import com.riskguard.infrastructure.ruleengine.RulePackLoader;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final RulePackLoader loader;

    public AdminController(RulePackLoader loader) { this.loader = loader; }

    @PostMapping(value="/rulepacks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RulePack uploadRulePack(@RequestParam("file") MultipartFile file) throws Exception {
        loader.reloadFromYaml(file.getBytes());
        return loader.getCurrent();
    }

    @GetMapping("/rulepacks/current")
    public RulePack current() { return loader.getCurrent(); }
}
