package com.smartvoice.pronunciation;

import com.smartvoice.pronunciation.dto.PronunciationEvaluateRequest;
import com.smartvoice.pronunciation.dto.PronunciationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pronunciation")
@RequiredArgsConstructor
public class PronunciationController {

    private final PronunciationService pronunciationService;

    @PostMapping("/evaluate")
    public ResponseEntity<PronunciationResult> evaluate(@RequestBody PronunciationEvaluateRequest request) {
        return ResponseEntity.ok(pronunciationService.evaluate(
                request.text(),
                request.referenceText(),
                request.durationMs()
        ));
    }
}
