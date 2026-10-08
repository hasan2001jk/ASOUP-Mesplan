package tj.railway.mesplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.payload.response.MessageResponse;
import tj.railway.mesplan.service.MesplanService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final MesplanService mesplanService;

    @GetMapping("/date/{dateoper}")
    public ResponseEntity<Object> getOrdersByDate(@PathVariable("dateoper") String dateoper) {
        try {
            LocalDate d = LocalDate.parse(dateoper);
            List<Order> orders = this.mesplanService.getOrdersByDateoper(d.atStartOfDay());
            return ResponseEntity.ok(orders);
        } catch (DateTimeParseException e) {
            return ResponseEntity.ok(new MessageResponse("Неверный формат даты"));
        } catch (Exception e) {
            return ResponseEntity.ok(new MessageResponse(e.getMessage()));
        }
    }
    @GetMapping("/id/{id}")
    public ResponseEntity<Object> getOrderById(@PathVariable("id") String id) {
        try {
            Integer i = Integer.parseInt(id);
            return ResponseEntity.ok(mesplanService.getOrder(i));
        } catch (Exception e) {
            return ResponseEntity.ok(new MessageResponse(e.getMessage()));
        }
    }
    @PostMapping("/save")
    public ResponseEntity<Object> saveOrder(@RequestBody Order order) {
        try {
            return ResponseEntity.ok(mesplanService.saveOrder(order));
        } catch (Exception e) {
            return ResponseEntity.ok(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/delete/{id}")
    public ResponseEntity<Object> delOrderById(@PathVariable("id") String id) {
        try {
            Integer i = Integer.parseInt(id);
            return ResponseEntity.ok(mesplanService.deleteOrder(i));
        } catch (Exception e) {
            return ResponseEntity.ok(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Object> uploadOrder(@RequestParam("file") MultipartFile file) {
        Integer res = this.mesplanService.uploadOrder(file);
        if (res == 0) {
            return ResponseEntity.ok(new MessageResponse("Сообщение сохранено"));
        }
        return ResponseEntity.ok(new MessageResponse(res.toString()));
    }
}
