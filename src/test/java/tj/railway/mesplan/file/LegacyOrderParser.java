package tj.railway.mesplan.file;

import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * ЗАМОРОЖЕННАЯ копия разбора из исходного MesplanService (коммит 42e09b4), без
 * единой правки, кроме static. Не исправлять: это эталон «как было», с которым
 * OrderFileParserTest сравнивает новый разбор.
 */
final class LegacyOrderParser {
    private LegacyOrderParser() {
    }

    static Order getOrder(String msg) {
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

    static Integer toInt(String s) {
        if (s.trim().isEmpty()) return 0;
        return Integer.parseInt(s.trim());
    }
    static Short toShort(String s) {
        if (s.trim().isEmpty()) return 0;
        return Short.parseShort(s.trim());
    }
}
