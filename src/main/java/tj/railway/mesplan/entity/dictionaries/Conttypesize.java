package tj.railway.mesplan.entity.dictionaries;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "conttypesize")
@Data
public class Conttypesize {
    @Id
    private Integer code;
    private String name;
    private String nameshort;
    private Integer length;
    private Float brutto;
}
