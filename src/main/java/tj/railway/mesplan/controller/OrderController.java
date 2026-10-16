package tj.railway.mesplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tj.railway.mesplan.configuration.NoKnownClient;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.payload.response.ResultResponse;
import tj.railway.mesplan.service.MesplanService;
import tj.railway.mesplan.web.ApiException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Заявки МЕСПЛАН. Успех — 200 с данными (или {"result", "id"} у загрузки),
 * ошибка — 400/404/502 с {"message"} (ApiErrors).
 */
@RestController
@CrossOrigin
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final MesplanService mesplanService;

    @NoKnownClient("«Просмотр» берёт период: /api/order/list?from=&to=")
    @GetMapping("/date/{dateoper}")
    public List<Order> getOrdersByDate(
            @PathVariable("dateoper") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateoper) {
        return mesplanService.getOrdersByDateoper(dateoper);
    }

    /** «Просмотр»: заявки, загруженные или введённые с from по to включительно (ГГГГ-ММ-ДД). */
    @GetMapping("/list")
    public List<Order> getOrdersByPeriod(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return mesplanService.getOrdersByPeriod(from, to);
    }

    @GetMapping("/id/{id}")
    public Order getOrderById(@PathVariable("id") Integer id) {
        return mesplanService.getOrder(id);
    }

    @PostMapping("/save")
    public Order saveOrder(@RequestBody Order order) {
        return mesplanService.saveOrder(order);
    }

    @PostMapping("/delete/{id}")
    public Order delOrderById(@PathVariable("id") Integer id) {
        return mesplanService.deleteOrder(id);
    }

    @PostMapping("/upload")
    public ResultResponse uploadOrder(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("Файл пустой");
        }
        Order saved = mesplanService.uploadOrder(file.getBytes(), file.getOriginalFilename());
        return new ResultResponse("Заявка № " + saved.getOrderno().trim() + " (строка " + saved.getStrorderno()
                + ") загружена, администраций: " + saved.getOrderdetails().size(), saved.getId());
    }
}
