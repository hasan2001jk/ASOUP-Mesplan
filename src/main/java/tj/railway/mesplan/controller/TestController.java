package tj.railway.mesplan.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tj.railway.mesplan.entity.dictionaries.Rodvag;
import tj.railway.mesplan.service.MesplanService;

@RestController
@CrossOrigin
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final MesplanService service;

    @GetMapping("/")
    public ResponseEntity<Object> test() {
        return ResponseEntity.ok("good");
    }

    @GetMapping("/rodvag")
    public ResponseEntity<Object> getRodvags() {
        return ResponseEntity.ok(service.getRodvags());
    }

    @GetMapping("/rodvag/{code}")
    public ResponseEntity<Object> getRodvag(@PathVariable String code) {
        Rodvag rodvag = service.getRodvag(code);
        log.info("getRodvag {}", rodvag.toString());
        return ResponseEntity.ok(rodvag);
    }
}
