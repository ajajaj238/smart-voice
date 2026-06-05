package com.smartvoice.scenario;

import com.smartvoice.scenario.dto.ScenarioDto;
import com.smartvoice.shared.enums.ScenarioCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

    @GetMapping
    public ResponseEntity<List<ScenarioDto>> list(@RequestParam(required = false) ScenarioCategory category) {
        if (category != null) return ResponseEntity.ok(scenarioService.getByCategory(category));
        return ResponseEntity.ok(scenarioService.getAllActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScenarioDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(scenarioService.getById(id));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(Arrays.stream(ScenarioCategory.values()).map(Enum::name).toList());
    }
}
