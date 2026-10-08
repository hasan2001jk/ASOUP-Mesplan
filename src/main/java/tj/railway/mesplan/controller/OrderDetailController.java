package tj.railway.mesplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tj.railway.mesplan.entity.Orderdetail;
import tj.railway.mesplan.payload.response.MessageResponse;
import tj.railway.mesplan.service.MesplanService;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/orderdetails")
@RequiredArgsConstructor
public class OrderDetailController {

    private final MesplanService mesplanService;

  /*  @GetMapping("/{id}")
    public ResponseEntity<Object> getOrderDetails(@PathVariable String id) {
        try {
            Integer i = Integer.parseInt(id);
            return ResponseEntity.ok(mesplanService.getOrderdetails(i));
        } catch (Exception e) {
            return ResponseEntity.ok(new MessageResponse(e.getLocalizedMessage()));
        }
    }*/
    @PostMapping("/save")
    public ResponseEntity<Object> saveOrderDetails(@RequestBody Orderdetail orderdetail) {
        try {
            return ResponseEntity.ok(mesplanService.saveOrderdetail(orderdetail));
        } catch (Exception e) {
            return ResponseEntity.ok(new MessageResponse(e.getLocalizedMessage()));
        }
    }
    @PostMapping("/{id}/delete")
    public ResponseEntity<Object> deleteOrderDetails(@PathVariable String id) {
        try {
            Integer i = Integer.parseInt(id);
            return ResponseEntity.ok(mesplanService.deleteOrderdetail(i));
        } catch (Exception e) {
            return ResponseEntity.ok(new MessageResponse(e.getLocalizedMessage()));
        }
    }

}
