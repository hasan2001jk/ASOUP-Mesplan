package tj.railway.mesplan.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Успех с текстом для оператора: {"result": "…", "id": N}. Ошибки — MessageResponse с кодом 4xx/5xx. */
@Data
@AllArgsConstructor
public class ResultResponse {
    private String result;
    private Integer id;
}
