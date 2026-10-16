package tj.railway.mesplan.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;

@Data
@Entity(name = "orderdetails")
@ToString(exclude = "order")
@EqualsAndHashCode(exclude = "order")
public class Orderdetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    /*
     * Связь с заявкой пишет колонку orderid. В исходнике и связь, и поле ниже были
     * insertable = false — то есть orderid не записывал НИКТО, и строка
     * администрации не могла сохраниться (orderid NOT NULL). Теперь колонку пишет
     * связь, а поле orderid только читается — для фронта.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderid", nullable = false)
    @JsonIgnore
    private Order order;
    @Column(name = "orderid", nullable = false, insertable = false, updatable = false)
    private Integer orderid;

    @Column(name = "adm", nullable = false)
    private Integer adm;

    @Column(name = "gate", nullable = false)
    private Integer gate;

    @Column(name = "expname", nullable = false, length = 50)
    private String expname;

    @Column(name = "expcode", nullable = false)
    private Integer expcode;

    @Column(name = "info1", length = 4)
    private String info1;

    @Column(name = "info2", length = 50)
    private String info2;

    @Column(name = "result", columnDefinition = "tinyint")
    private Short result;

    @Column(name = "denyreason", length = 100)
    private String denyreason;

}