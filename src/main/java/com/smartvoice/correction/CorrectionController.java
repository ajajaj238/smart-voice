package com.smartvoice.correction;

import com.smartvoice.correction.dto.CorrectionRequest;
import com.smartvoice.correction.dto.CorrectionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/corrections")
@RequiredArgsConstructor
public class CorrectionController {

    private final CorrectionService correctionService;

    @PostMapping
    public ResponseEntity<CorrectionResult> correct(@RequestBody CorrectionRequest request) {
        return ResponseEntity.ok(correctionService.correct(
                request.text(),
                request.scenario(),
                request.level()
        ));
    }
}
