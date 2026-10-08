package tj.railway.mesplan.entity.dictionaries;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "rodvag")
@Data
public class Rodvag {
    @Id
    private String code;
    private String symb;
    private String name;
}
