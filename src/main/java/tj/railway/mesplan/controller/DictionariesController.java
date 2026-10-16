package tj.railway.mesplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tj.railway.mesplan.configuration.NoKnownClient;
import tj.railway.mesplan.entity.dictionaries.Conttypesize;
import tj.railway.mesplan.entity.dictionaries.Przsob;
import tj.railway.mesplan.entity.dictionaries.Rodvag;
import tj.railway.mesplan.service.MesplanService;

import java.util.List;

/** Справочники формы «Ввод». Ответы списков — те же, что в исходнике (байт в байт). */
@RestController
@CrossOrigin
@RequiredArgsConstructor
@RequestMapping("/api/dictionaries")
public class DictionariesController {

    private final MesplanService mesplanService;

    @GetMapping("/rodvag")
    public List<Rodvag> getRodvags() {
        return mesplanService.getRodvags();
    }

    @NoKnownClient("фронт берёт весь справочник /rodvag")
    @GetMapping("/rodvag/{id}")
    public Rodvag getRodvag(@PathVariable String id) {
        return mesplanService.getRodvag(id);
    }

    @GetMapping("/przsob")
    public List<Przsob> getPrzsob() {
        return mesplanService.getPrzsobs();
    }

    @NoKnownClient("фронт берёт весь справочник /przsob")
    @GetMapping("/przsob/{id}")
    public Przsob getPrzsob(@PathVariable Integer id) {
        return mesplanService.getPrzsob(id);
    }

    @GetMapping("/conttypesize")
    public List<Conttypesize> getConttypesizes() {
        return mesplanService.getConttypesizes();
    }

    @NoKnownClient("фронт берёт весь справочник /conttypesize")
    @GetMapping("/conttypesize/{id}")
    public Conttypesize getConttypesize(@PathVariable Integer id) {
        return mesplanService.getConttypesize(id);
    }
}
