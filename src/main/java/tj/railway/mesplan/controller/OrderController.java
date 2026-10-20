package tj.railway.mesplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tj.railway.mesplan.configuration.NoKnownClient;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.payload.response.ResultResponse;
import tj.railway.mesplan.service.MesplanService;
import tj.railway.mesplan.web.ApiException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
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
        List<Order> saved = mesplanService.uploadOrders(file.getBytes(), file.getOriginalFilename());
        List<Integer> ids = new ArrayList<>();
        for (Order o : saved) {
            ids.add(o.getId());
        }
        Order first = saved.get(0);
        String text = saved.size() == 1
                ? "Заявка № " + first.getOrderno().trim() + " (строка " + first.getStrorderno() + ") загружена, администраций: "
                        + first.getOrderdetails().size()
                : "Загружено заявок: " + saved.size();
        return new ResultResponse(text, first.getHeader(), ids);
    }

    /**
     * Ответный файл по загруженному файлу заявок — скачивание. Имя — идентификатор
     * ответа + «.txt», как сохраняла старая программа; содержимое — CP866.
     * Имя отдаётся в Content-Disposition и X-Answer-Identifier (открыты для фронта).
     */
    @CrossOrigin(exposedHeaders = {"Content-Disposition", "X-Answer-Identifier", "X-Answer-From-Original",
            "X-Answer-Lines", "X-Answer-Informational", "X-Answer-Unanswered"})
    @GetMapping("/answer")
    public ResponseEntity<byte[]> answer(@RequestParam("header") String header,
                                         @RequestParam(value = "month", required = false) Integer month) {
        MesplanService.Answer a = mesplanService.answerFile(header, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + a.getIdentifier() + ".txt\"")
                .header("X-Answer-Identifier", a.getIdentifier())
                .header("X-Answer-From-Original", String.valueOf(a.isFromOriginal()))
                .header("X-Answer-Lines", String.valueOf(a.getLines()))
                .header("X-Answer-Informational", String.valueOf(a.getInformational()))
                .header("X-Answer-Unanswered", String.valueOf(a.getUnanswered()))
                .contentType(new MediaType("text", "plain", MesplanService.FILE_CHARSET))
                .body(a.getContent());
    }
}
