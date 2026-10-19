package tj.railway.mesplan.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Успех загрузки: {"result": "…", "header": "<идентификатор файла>", "ids": [N, …]}.
 * Ошибки — MessageResponse с кодом 4xx/5xx.
 */
@Data
@AllArgsConstructor
public class ResultResponse {
    private String result;
    private String header;
    private List<Integer> ids;
}
