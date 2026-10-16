package tj.railway.mesplan.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tj.railway.mesplan.file.OrderFormatException;
import tj.railway.mesplan.payload.response.MessageResponse;

/**
 * Единый ответ об ошибке: код HTTP + {"message": "…"} по-русски.
 *
 * В исходнике ошибки отвечали 200 {"message": …}, а успех загрузки — тоже
 * 200 {"message": "Сообщение сохранено"}. Фронт читает любой {message} как
 * ошибку, поэтому успех показывался красным, а сбой — цифрой «1»/«2». Теперь:
 * успех — 200 с данными или {"result": "…"}, ошибка — 4xx/5xx с {"message"}.
 *
 *   400 — данные неверны (формат файла, обязательные поля, параметры);
 *   404 — нет такой записи;
 *   502 — не удалось записать/прочитать базу (служба жива, база — нет).
 * Фронт показывает текст сервера для 400/404/502 (ErrorInterceptor).
 */
@RestControllerAdvice
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<MessageResponse> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new MessageResponse(e.getMessage()));
    }

    @ExceptionHandler(OrderFormatException.class)
    public ResponseEntity<MessageResponse> format(OrderFormatException e) {
        return ResponseEntity.badRequest().body(new MessageResponse("Неверный формат файла заявок: " + e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<MessageResponse> badInput(Exception e) {
        log.info("Неверный запрос: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new MessageResponse("Неверные данные запроса"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<MessageResponse> tooBig(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(new MessageResponse("Файл слишком большой (больше 2 МБ) — это не файл заявок"));
    }

    /** Всё непредвиденное — тоже {"message"}, а причина — в журнал целиком. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> unexpected(Exception e) {
        if (e instanceof HttpRequestMethodNotSupportedException) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new MessageResponse("Неверный метод запроса"));
        }
        if (e instanceof HttpMediaTypeNotSupportedException) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new MessageResponse("Неверный тип данных запроса"));
        }
        log.error("Непредвиденная ошибка", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("Внутренняя ошибка службы МЕСПЛАН"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<MessageResponse> database(RuntimeException e) {
        log.error("Ошибка базы mesplan", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new MessageResponse("Не удалось выполнить операцию в базе МЕСПЛАН"));
    }
}
