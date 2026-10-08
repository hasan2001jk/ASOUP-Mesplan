package tj.railway.mesplan.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity(name = "orders")
@ToString(exclude = "orderdetails")
@EqualsAndHashCode(exclude = "orderdetails")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "dateoper", nullable = false)
    private LocalDateTime dateoper;

    @Column(name = "adm", nullable = false)
    private Integer adm;

    @Column(name = "ftype", nullable = false, length = 2)
    private String ftype;

    @Column(name = "strview", nullable = false, length = 3)
    private String strview;

    @Column(name = "datepogr", nullable = false)
    private LocalDateTime datepogr;

    @Column(name = "transid", nullable = false)
    private Integer transid;

    @Column(name = "gosotpr", nullable = false)
    private Integer gosotpr;

    @Column(name = "orderno", nullable = false, length = 10)
    private String orderno;

    @Column(name = "strorderno", nullable = false)
    private Integer strorderno;

    @Column(name = "istrorderno")
    private Integer istrorderno;

    @Column(name = "codengr", nullable = false)
    private Integer codengr;

    @Column(name = "otprname", nullable = false, length = 50)
    private String otprname;

    @Column(name = "poluchname", nullable = false, length = 50)
    private String poluchname;

    @Column(name = "stotpr", nullable = false)
    private Integer stotpr;

    @Column(name = "stnazn", nullable = false)
    private Integer stnazn;

    @Column(name = "etsng", nullable = false)
    private Integer etsng;

    @Column(name = "gng", nullable = false, length = 12)
    private String gng;

    @Column(name = "strnazn", nullable = false)
    private Integer strnazn;

    @Column(name = "codegrpolport", length = 20)
    private String codegrpolport;

    @Column(name = "namegrpolport", length = 50)
    private String namegrpolport;

    @Column(name = "codeport", length = 50)
    private String codeport;

    @Column(name = "rodvag")
    private Integer rodvag;

    @Column(name = "prinadl", columnDefinition = "tinyint not null")
    private Short prinadl;

    @Column(name = "netto", nullable = false)
    private Integer netto;

    @Column(name = "vags", nullable = false)
    private Integer vags;

    @Column(name = "prim")
    private String prim;

    @Column(name = "rodotpr", columnDefinition = "tinyint not null")
    private Short rodotpr;

    @Column(name = "otprcode")
    private Integer otprcode;

    @Column(name = "poluchcode")
    private Integer poluchcode;

    @Column(name = "rezerv1", length = 20)
    private String rezerv1;

    @Column(name = "rezerv2", length = 20)
    private String rezerv2;

    @Column(name = "rezerv3")
    private String rezerv3;

    @Column(name = "containers")
    private Integer containers;

    @Column(name = "conttr")
    private Integer conttr;

    @Column(name = "contpr", columnDefinition = "tinyint")
    private Short contpr;

    @Column(name = "admcount", columnDefinition = "tinyint not null")
    private Short admcount;

    private String header;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Fetch(FetchMode.SUBSELECT)
    private List<Orderdetail> orderdetails = new ArrayList<>();

    public void addOrderdetail(Orderdetail orderdetail) {
        orderdetails.add(orderdetail);
        orderdetail.setOrder(this);
    }
}