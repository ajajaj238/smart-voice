package com.smartvoice.session;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartvoice.session.dto.CreateSessionRequest;
import com.smartvoice.session.dto.SessionDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<Session> create(@RequestBody CreateSessionRequest request, Authentication auth) {
        return ResponseEntity.ok(sessionService.create(auth.getPrincipal().toString(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Session> getById(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(sessionService.getOwnedById(auth.getPrincipal().toString(), id));
    }

    @GetMapping({"/{id}/detail", "/detail/{id}"})
    public ResponseEntity<SessionDetailResponse> getDetail(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(sessionService.getDetail(auth.getPrincipal().toString(), id));
    }

    @GetMapping
    public ResponseEntity<IPage<Session>> list(Authentication auth,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(sessionService.listByUser(auth.getPrincipal().toString(), page, size));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<Session> end(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(sessionService.end(auth.getPrincipal().toString(), id));
    }
}
