package tj.railway.mesplan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;
import tj.railway.mesplan.entity.dictionaries.Conttypesize;
import tj.railway.mesplan.entity.dictionaries.Przsob;
import tj.railway.mesplan.entity.dictionaries.Rodvag;
import tj.railway.mesplan.repository.OrderRepository;
import tj.railway.mesplan.repository.OrderdetailRepository;
import tj.railway.mesplan.repository.dictionaries.ConttypesizeRepository;
import tj.railway.mesplan.repository.dictionaries.PrzsobRepository;
import tj.railway.mesplan.repository.dictionaries.RodvagRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MesplanService {

    private final RodvagRepository rodvagRepository;
    private final PrzsobRepository przsobRepository;
    private final ConttypesizeRepository conttypesizeRepository;
    private final OrderRepository orderRepository;
    private final OrderdetailRepository orderdetailRepository;

    public List<Rodvag> getRodvags() {
        return rodvagRepository.findAll();
    }
    public Rodvag getRodvag(String code) {
        return rodvagRepository.findById(code).orElse(null);
    }

    public List<Przsob> getPrzsobs() {return przsobRepository.findAll(); }
    public Przsob getPrzsob(Integer code) {return przsobRepository.findById(code).orElse(null); }

    public List<Conttypesize> getConttypesizes() {return conttypesizeRepository.findAll(); }
    public Conttypesize getConttypesize(Integer code) {return conttypesizeRepository.findById(code).orElse(null); }

    public List<Order> getOrdersByDateoper(LocalDateTime dateoper) {return orderRepository.findAllByDateoper(dateoper); }
    public Order getOrder(Integer code) {return orderRepository.findById(code).orElse(null);}
    public Order saveOrder(Order order) {return this.orderRepository.save(order); }
    public Order deleteOrder(Integer id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order != null) {
            orderRepository.delete(order);
        }
        return order;
    }

    //public List<Orderdetail> getOrderdetails(Integer id) {return orderdetailRepository.findByOrderid(id); };
    public Orderdetail saveOrderdetail(Orderdetail orderdetail) {return orderdetailRepository.save(orderdetail) ;}
    public Orderdetail deleteOrderdetail(Integer id) {
        Orderdetail orderdetail = orderdetailRepository.findById(id).orElse(null);
        if (orderdetail != null) {
            orderdetailRepository.delete(orderdetail);
        }
        return orderdetail;
    }

    public Integer uploadOrder(MultipartFile file) {
        try {
            String s = new String(file.getBytes(), "Cp866");
            Order order = getOrder(s);
            Order o = orderRepository.save(order);
            return 0;
        } catch (IOException e) {
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
         return 2;
        }

    }
    private Order getOrder(String msg) {
        Order order = new Order();
        order.setDateoper(LocalDate.now().atStartOfDay());
        order.setHeader(msg.substring(0, msg.indexOf('\r')));
        String t = msg.substring(msg.indexOf('\n') + 1);
        order.setAdm(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setFtype(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setStrview(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        String dd = t.substring(0, t.indexOf('^'));
        dd = dd.substring(4, 8).concat("-").concat(dd.substring(2, 4)).concat("-").concat(dd.substring(0, 2));
        order.setDatepogr(LocalDate.parse(dd, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay());
        t = t.substring(t.indexOf('^') + 1);
        order.setTransid(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setGosotpr (toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setOrderno(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setStrorderno(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setIstrorderno(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setCodengr(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setOtprname(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setPoluchname(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setStotpr(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setStnazn(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setEtsng(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setGng(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setStrnazn(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setCodegrpolport(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setNamegrpolport(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setCodeport(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setRodvag(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setPrinadl(toShort(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setNetto(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setVags(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setPrim(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setRodotpr(toShort(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setOtprcode(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setPoluchcode(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setRezerv1(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setRezerv2(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setRezerv3(t.substring(0, t.indexOf('^')));
        t = t.substring(t.indexOf('^') + 1);
        order.setContainers(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setConttr(toInt(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setContpr(toShort(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        order.setAdmcount(toShort(t.substring(0, t.indexOf('^'))));
        t = t.substring(t.indexOf('^') + 1);
        Orderdetail od;
        while(t.length() > 1) {
            od = new Orderdetail();
            od.setAdm(toInt(t.substring(0, t.indexOf('^'))));
            t = t.substring(t.indexOf('^') + 1);
            od.setGate(toInt(t.substring(0, t.indexOf('^'))));
            t = t.substring(t.indexOf('^') + 1);
            od.setExpname(t.substring(0, t.indexOf('^')));
            t = t.substring(t.indexOf('^') + 1);
            od.setExpcode(toInt(t.substring(0, t.indexOf('^'))));
            t = t.substring(t.indexOf('^') + 1);
            od.setInfo1(t.substring(0, t.indexOf('^')));
            t = t.substring(t.indexOf('^') + 1);
            od.setInfo2(t.substring(0, t.indexOf('^')));
            t = t.substring(t.indexOf('^') + 1);
            od.setResult(toShort(t.substring(0, t.indexOf('^'))));
            t = t.substring(t.indexOf('^') + 1);
            if(t.indexOf('^') >= 0) {
                od.setDenyreason(t.substring(0, t.indexOf('^')));
                t = t.substring(t.indexOf('^') + 1);
            } else {
                t = "";
                od.setDenyreason(t);
            }
            order.addOrderdetail(od);
        }
        return order;
    }

    Integer toInt(String s) {
        if (s.trim().isEmpty()) return 0;
        return Integer.parseInt(s.trim());
    }
    Short toShort(String s) {
        if (s.trim().isEmpty()) return 0;
        return Short.parseShort(s.trim());
    }
}
