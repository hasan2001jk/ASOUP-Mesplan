package tj.railway.mesplan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.file.OrderFileParser;
import tj.railway.mesplan.file.OrderFileParserTest;
import tj.railway.mesplan.service.MesplanService;
import tj.railway.mesplan.web.ApiException;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Договор ответов с фронтом: успех — 200 с данными или {"result"}, ошибка —
 * 4xx/5xx с {"message"}. Фронт любое {message} показывает как ошибку, поэтому
 * {message} в успешном ответе (как было у загрузки) — это ошибка договора.
 */
@WebMvcTest(properties = "MESPLAN_DB_PASSWORD=test")
class WebContractTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    MesplanService service;

    static Order parsed() {
        Order o = OrderFileParser.parse(OrderFileParserTest.file(OrderFileParserTest.twoAdms()), LocalDate.now());
        o.setId(5);
        return o;
    }

    @Test
    void uploadSuccessIsResultNotMessage() throws Exception {
        when(service.uploadOrder(any(), anyString())).thenReturn(parsed());
        mvc.perform(multipart("/api/order/upload").file(new MockMultipartFile("file", "z.txt", "text/plain", new byte[]{1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Заявка № 0000012345 (строка 3) загружена, администраций: 2"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void uploadFormatErrorIs400WithPlace() throws Exception {
        byte[] bad = "только одна строка".getBytes(MesplanService.FILE_CHARSET);
        when(service.uploadOrder(any(), anyString())).thenAnswer(inv ->
                OrderFileParser.parse(new String((byte[]) inv.getArgument(0), MesplanService.FILE_CHARSET), LocalDate.now()));
        mvc.perform(multipart("/api/order/upload").file(new MockMultipartFile("file", "z.txt", "text/plain", bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Неверный формат файла заявок: ")));
    }

    @Test
    void uploadEmptyOrMissingFileIs400() throws Exception {
        mvc.perform(multipart("/api/order/upload").file(new MockMultipartFile("file", "z.txt", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Файл пустой"));
        mvc.perform(multipart("/api/order/upload"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    void databaseFailureIs502() throws Exception {
        when(service.uploadOrder(any(), anyString())).thenThrow(new DataIntegrityViolationException("515"));
        mvc.perform(multipart("/api/order/upload").file(new MockMultipartFile("file", "z.txt", "text/plain", new byte[]{1})))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Не удалось выполнить операцию в базе МЕСПЛАН"));
    }

    @Test
    void notFoundIs404() throws Exception {
        when(service.getOrder(9)).thenThrow(ApiException.notFound("Нет заявки с номером записи 9"));
        mvc.perform(get("/api/order/id/9"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Нет заявки с номером записи 9"));
        mvc.perform(get("/api/order/id/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void saveReturnsTheOrder() throws Exception {
        when(service.saveOrder(any())).thenReturn(parsed());
        mvc.perform(post("/api/order/save").contentType(MediaType.APPLICATION_JSON).content("{\"orderno\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.orderdetails.length()").value(2))
                .andExpect(jsonPath("$.orderdetails[0].order").doesNotExist());
        mvc.perform(post("/api/order/save").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Неверные данные запроса"));
    }

    @Test
    void periodNeedsBothDates() throws Exception {
        mvc.perform(get("/api/order/list?from=2026-10-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/order/list?from=2026-10-01&to=1.10.2026")).andExpect(status().isBadRequest());
    }

    @Test
    void wrongMethodIs405NotInternalError() throws Exception {
        mvc.perform(get("/api/order/save"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.message").exists());
    }
}
