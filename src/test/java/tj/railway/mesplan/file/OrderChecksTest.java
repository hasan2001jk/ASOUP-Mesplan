package tj.railway.mesplan.file;

import org.junit.jupiter.api.Test;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderChecksTest {

    static Order valid() {
        return OrderFileParserTest.one(OrderFileParserTest.file(OrderFileParserTest.twoAdms()), LocalDate.now());
    }

    @Test
    void parsedFileIsValid() {
        assertEquals(0, OrderChecks.problems(valid()).size());
    }

    @Test
    void namesEveryMissingRequiredField() {
        Order o = valid();
        o.setOrderno(null);
        o.setNetto(null);
        o.getOrderdetails().get(1).setExpname(null);
        List<String> p = OrderChecks.problems(o);
        assertTrue(p.contains("не заполнено «№ заявки»"), p.toString());
        assertTrue(p.contains("не заполнено «Нетто»"), p.toString());
        assertTrue(p.contains("администрация 2: не заполнено «Экспедитор»"), p.toString());
        assertEquals(3, p.size());
    }

    @Test
    void optionalFieldsMayBeEmpty() {
        Order o = valid();
        o.setCodeport(null);
        o.setRezerv3(null);
        o.setIstrorderno(null);
        assertEquals(0, OrderChecks.problems(o).size());
    }

    @Test
    void lengthsAndTinyint() {
        Order o = valid();
        o.setGng("1234567890123");
        o.setPrinadl((short) 256);
        Orderdetail d = o.getOrderdetails().get(0);
        d.setDenyreason(new String(new char[101]).replace('\0', 'x'));
        List<String> p = OrderChecks.problems(o);
        assertTrue(p.contains("«ГНГ» длиннее 12 знаков"), p.toString());
        assertTrue(p.contains("«Принадлежность» вне 0…255"), p.toString());
        assertTrue(p.contains("администрация 1: «Причина отказа» длиннее 100 знаков"), p.toString());
    }

    @Test
    void nullOrder() {
        assertEquals("Нет данных заявки", OrderChecks.problems((Order) null).get(0));
    }
}
