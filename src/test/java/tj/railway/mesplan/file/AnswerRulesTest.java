package tj.railway.mesplan.file;

import org.junit.jupiter.api.Test;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Правила ответа — на настоящем файле архива (AnswerFileTest.REAL: две строки, вид 2, 66 последней). */
class AnswerRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    static List<Order> orders() {
        return OrderFileParser.parseFile(AnswerFileTest.REAL, TODAY);
    }

    static Orderdetail ours(Order o) {
        return AnswerRules.ours(o).get(0);
    }

    static AnswerFile.Built answer(List<Order> orders) {
        return AnswerFile.answer("PL_K6610-ANSWER", orders, AnswerFileTest.REAL, TODAY);
    }

    @Test
    void kinds() {
        assertEquals("2s", AnswerRules.answerKind("2"));
        assertEquals("2s", AnswerRules.answerKind(" 002 "));
        assertEquals("7s", AnswerRules.answerKind("7"));
        assertEquals("8s", AnswerRules.answerKind("8"));
        assertEquals("10s", AnswerRules.answerKind("10"));
        for (String info : new String[]{"3", "4", "5", "6", "11", "017", "2s", "16S", "1", "", null}) {
            assertNull(AnswerRules.answerKind(info), "только для сведения: " + info);
        }
    }

    @Test
    void answerIsWrittenAs2sAndOnlyAnsweredLinesGo() {
        List<Order> list = orders();
        ours(list.get(0)).setResult((short) 1);
        AnswerFile.Built b = answer(list);
        assertTrue(b.problems.isEmpty(), b.problems.toString());
        assertEquals(1, b.lines);
        assertEquals(1, b.unanswered, "вторая строка без ответа 66 — в файл не идёт");
        String[] lines = b.text.split("\r\n");
        assertEquals(2, lines.length);
        String[] t = lines[1].split("\\^", -1);
        assertEquals("2s", t[2], "вид 2 без «s» сеть отклоняет (0133)");
        String original = AnswerFileTest.REAL.split("\r\n")[1];
        String expected = original.replaceFirst("\\^PL_K\\^2\\^", "^PL_K^2s^");
        expected = expected.substring(0, expected.length() - "^0^".length()) + "^1^";
        assertEquals(expected, lines[1], "кроме вида и нашего результата — байт в байт как пришло");
    }

    @Test
    void reasonRequiredFor2And3() {
        List<Order> list = orders();
        ours(list.get(0)).setResult((short) 2);
        AnswerFile.Built b = answer(list);
        assertEquals(1, b.problems.size());
        assertTrue(b.problems.get(0).contains("нужна причина (0105)"), b.problems.toString());
        ours(list.get(0)).setDenyreason("нет гарантийного письма");
        assertTrue(answer(list).problems.isEmpty());
    }

    @Test
    void volumesOnlyDown() {
        List<Order> list = orders();
        Order o = list.get(0);
        ours(o).setResult((short) 3);
        ours(o).setDenyreason("сокращение объёма");
        o.setVags(50);
        o.setNetto(3000);
        assertTrue(answer(list).problems.isEmpty(), "уменьшение — можно");
        o.setVags(150);
        List<String> p = answer(list).problems;
        assertTrue(p.stream().anyMatch(s -> s.contains("можно только уменьшить (0113)")), p.toString());
    }

    @Test
    void identityFieldsCannotChange() {
        List<Order> list = orders();
        Order o = list.get(0);
        ours(o).setResult((short) 1);
        o.setStnazn(745807);
        o.setEtsng(214040);
        List<String> p = answer(list).problems;
        assertTrue(p.stream().anyMatch(s -> s.contains("Станция назначения") && s.contains("0099")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.contains("ЕТСНГ") && s.contains("0080")), p.toString());
    }

    @Test
    void textFieldsAreNotBlocked() {
        // Сеть принимала ответы с искажённым текстом (ArchiveAnswerRulesTest) — не запрещаем.
        List<Order> list = orders();
        Order o = list.get(0);
        ours(o).setResult((short) 1);
        o.setPrim("тат-оил 100-0027/20 (уточнено)");
        o.setPoluchname("ООО Нафт Наклиёт");
        assertTrue(answer(list).problems.isEmpty());
    }

    @Test
    void ourGateMayChangeOthersNot() {
        List<Order> list = orders();
        Order o = list.get(0);
        ours(o).setResult((short) 3);
        ours(o).setDenyreason("другой стык");
        ours(o).setGate(736207);
        assertTrue(answer(list).problems.isEmpty(), "свой стык — можно");
        o.getOrderdetails().get(0).setGate(813009);
        assertTrue(answer(list).problems.stream().anyMatch(s -> s.contains("стык чужой администрации (0102)")));
    }

    @Test
    void repeatedOurAdministrationNeedsSameResult() {
        List<Order> list = orders();
        Order o = list.get(0);
        Orderdetail extra = new Orderdetail();
        extra.setAdm(66);
        extra.setGate(736206);
        extra.setResult((short) 2);
        extra.setDenyreason("нет");
        ours(o).setResult((short) 1);
        o.getOrderdetails().add(extra);
        List<String> p = AnswerRules.problems(o, "2s", null);
        assertTrue(p.get(0).contains("(0145)"), p.toString());
    }

    @Test
    void repeatAgreementOnlyOneOrTwo() {
        Order o = orders().get(0);
        ours(o).setResult((short) 3);
        ours(o).setDenyreason("x");
        assertTrue(AnswerRules.problems(o, "7s", null).stream().anyMatch(s -> s.contains("(0104)")));
        ours(o).setResult((short) 1);
        assertTrue(AnswerRules.problems(o, "7s", null).isEmpty());
    }

    @Test
    void informationalFilesHaveNothingToAnswer() {
        String info = AnswerFileTest.REAL.replace("^PL_K^2^", "^PL_K^3^");
        List<Order> list = OrderFileParser.parseFile(info, TODAY);
        for (Order o : list) {
            ours(o).setResult((short) 1);
        }
        AnswerFile.Built b = AnswerFile.answer("X", list, info, TODAY);
        assertEquals(0, b.lines);
        assertEquals(2, b.informational);
    }

    @Test
    void formSaveChecksOurAnswer() {
        Order o = orders().get(0);
        ours(o).setResult((short) 3);
        assertTrue(OrderChecks.problems(o).stream().anyMatch(s -> s.contains("нужна причина")));
        ours(o).setResult((short) 4);
        assertTrue(OrderChecks.problems(o).stream().anyMatch(s -> s.contains("можно 0, 1, 2 или 3")));
    }
}
