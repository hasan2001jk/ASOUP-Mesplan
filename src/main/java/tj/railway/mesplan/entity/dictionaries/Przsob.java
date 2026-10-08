package tj.railway.mesplan.entity.dictionaries;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "przsob")
@Data
public class Przsob {
    @Id
    private Integer code;
    private String name;
    private String comment;
}
