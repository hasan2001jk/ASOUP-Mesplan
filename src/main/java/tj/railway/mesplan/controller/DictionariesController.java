package tj.railway.mesplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tj.railway.mesplan.service.MesplanService;

@RestController
@CrossOrigin
@RequiredArgsConstructor
@RequestMapping("/api/dictionaries")
public class DictionariesController {

    private final MesplanService mesplanService;

    @GetMapping("/rodvag")
    ResponseEntity<Object> getRodvags() {
        return ResponseEntity.ok(mesplanService.getRodvags());
    }
    @GetMapping("/rodvag/{id}")
    ResponseEntity<Object> getRodvag(@PathVariable String id) {
        return ResponseEntity.ok(mesplanService.getRodvag(id));
    }

    @GetMapping("/przsob")
    ResponseEntity<Object> getPrzsob() {
        return ResponseEntity.ok(mesplanService.getPrzsobs());
    }
    @GetMapping("/przsob/{id}")
    ResponseEntity<Object> getPrzsob(@PathVariable String id) {
        try {
            int i = Integer.parseInt(id);
            return ResponseEntity.ok(mesplanService.getPrzsob(i));
        } catch (Exception e) {
            return ResponseEntity.ok(e.getLocalizedMessage());
        }
    }

        @GetMapping("/conttypesize")
    ResponseEntity<Object> getConttypesizes() {
        return ResponseEntity.ok(mesplanService.getConttypesizes());
    }
    @GetMapping("/conttypesize/{id}")
    ResponseEntity<Object> getConttypesizes(@PathVariable String id) {
        try {
            Integer i = Integer.parseInt(id);
            return ResponseEntity.ok(mesplanService.getConttypesize(i));
        } catch (Exception e) {
            return ResponseEntity.ok(e.getLocalizedMessage());
        }
    }
}
