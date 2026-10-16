package tj.railway.mesplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tj.railway.mesplan.configuration.NoKnownClient;
import tj.railway.mesplan.entity.Orderdetail;
import tj.railway.mesplan.service.MesplanService;

/** Отдельная строка администрации. Фронт сохраняет заявку целиком через /api/order/save. */
@RestController
@CrossOrigin
@RequestMapping("/api/orderdetails")
@RequiredArgsConstructor
public class OrderDetailController {

    private final MesplanService mesplanService;

    @NoKnownClient("фронт сохраняет заявку целиком — /api/order/save")
    @PostMapping("/save")
    public Orderdetail saveOrderDetails(@RequestBody Orderdetail orderdetail) {
        return mesplanService.saveOrderdetail(orderdetail);
    }

    @NoKnownClient("фронт сохраняет заявку целиком — /api/order/save")
    @PostMapping("/{id}/delete")
    public Orderdetail deleteOrderDetails(@PathVariable Integer id) {
        return mesplanService.deleteOrderdetail(id);
    }
}
