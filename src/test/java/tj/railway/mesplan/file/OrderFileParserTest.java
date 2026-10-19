package tj.railway.mesplan.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class OrderFileParserTest {

    static final Charset CP866 = Charset.forName("IBM866");
    static final LocalDate TODAY = LocalDate.now();

    /** Идентификатор файла — как в настоящих файлах архива. */
    public static final String HEADER = "PL_K6611-2026-11-10-10.00.00.000000_NV_2013.1";

    /** Файл, в котором заявка ровно одна. */
    public static Order one(String text, LocalDate today) {
        List<Order> all = OrderFileParser.parseFile(text, today);
        assertEquals(1, all.size(), "заявок в файле");
        return all.get(0);
    }

    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** 35 полей заявки — правдоподобные значения (Таджикистан 66 / 762). */
    static List<String> orderValues() {
        return new ArrayList<>(Arrays.asList(
                "66", "01", "2s", "15112026", "1", "762", "0000012345", "3", "1", "112",
                "ГУП ТАДЖИКСКИЙ АЛЮМИНИЙ", "ООО ПРИЁМЩИК", "748504", "938608", "41107", "76011000",
                "643", "", "", "", "60", "1", "1380", "20", "срочно", "1", "10234", "55871", "", "", "",
                "0", "0", "0", "2"));
    }

    static List<String> detail(String adm, String gate, String result, String reason) {
        return Arrays.asList(adm, gate, "ЭКСПЕДИТОР", "1234", "", "", result, reason);
    }

    /** Строка файла: каждое поле с «^» на конце, как в исходном формате. */
    public static String file(List<String> values) {
        StringBuilder sb = new StringBuilder(HEADER + "\r\n");
        for (String v : values) {
            sb.append(v).append('^');
        }
        return sb.toString();
    }

    public static List<String> twoAdms() {
        List<String> v = orderValues();
        v.addAll(detail("66", "746000", "1", ""));
        v.addAll(detail("29", "746805", "2", "нет согласования стыка"));
        return v;
    }

    static String json(Order o) throws Exception {
        return JSON.writeValueAsString(o) + JSON.writeValueAsString(o.getOrderdetails());
    }

    @Test
    void readsAllFieldsOfARealisticFile() {
        Order o = one(file(twoAdms()), TODAY);
        assertEquals(HEADER, o.getHeader());
        assertEquals(TODAY.atStartOfDay(), o.getDateoper(), "дата загрузки — сегодня");
        assertEquals(66, o.getAdm());
        assertEquals("2s", o.getStrview());
        assertEquals(LocalDate.of(2026, 11, 15).atStartOfDay(), o.getDatepogr(), "ДДММГГГГ");
        assertEquals("0000012345", o.getOrderno());
        assertEquals("ГУП ТАДЖИКСКИЙ АЛЮМИНИЙ", o.getOtprname());
        assertEquals("76011000", o.getGng());
        assertEquals((short) 2, o.getAdmcount());
        assertEquals(2, o.getOrderdetails().size());
        Orderdetail second = o.getOrderdetails().get(1);
        assertEquals(29, second.getAdm());
        assertEquals((short) 2, second.getResult());
        assertEquals("нет согласования стыка", second.getDenyreason());
        assertSame(o, second.getOrder(), "строка администрации привязана к заявке — иначе orderid не запишется");
    }

    /** На всём, что исходный разбор принимал, новый даёт тот же результат. */
    @Test
    void sameAsLegacyWhereLegacyWorked() throws Exception {
        Random rnd = new Random(20261016);
        int compared = 0;
        for (int n = 0; n < 300; n++) {
            List<String> v = orderValues();
            v.set(7, String.valueOf(rnd.nextInt(999)));
            v.set(22, String.valueOf(rnd.nextInt(100000)));
            v.set(10, rnd.nextBoolean() ? "" : "ОТПР " + rnd.nextInt(1000));
            int adms = rnd.nextInt(4);
            for (int g = 0; g < adms; g++) {
                v.addAll(detail(String.valueOf(20 + rnd.nextInt(60)), String.valueOf(rnd.nextInt(999999)),
                        String.valueOf(rnd.nextInt(3)), rnd.nextBoolean() ? "" : "причина " + g));
            }
            String text = file(v);
            Order legacy = LegacyOrderParser.getOrder(text);
            Order now = one(text, TODAY);
            assertEquals(json(legacy), json(now), "файл " + n);
            compared++;
        }
        assertEquals(300, compared);
    }

    /** Исходник терял «Причину отказа» последней администрации, если после неё не было «^». */
    @Test
    void keepsLastReasonWithoutTrailingCaret() throws Exception {
        String text = file(twoAdms());
        text = text.substring(0, text.length() - 1);
        Order legacy = LegacyOrderParser.getOrder(text);
        assertEquals("", legacy.getOrderdetails().get(1).getDenyreason(), "так было: причина пропадала");
        assertEquals("нет согласования стыка",
                one(text, TODAY).getOrderdetails().get(1).getDenyreason());
    }

    @Test
    void acceptsMissingEmptyLastReasonLikeLegacy() {
        List<String> v = orderValues();
        v.addAll(detail("66", "746000", "1", "x").subList(0, 7));
        Order o = one(file(v), TODAY);
        assertEquals("", o.getOrderdetails().get(0).getDenyreason());
    }

    /** Файл с переводом строки в конце: исходник уходил в цикл по «\r\n» и падал. */
    @Test
    void trailingNewlineIsHarmless() throws Exception {
        String text = file(twoAdms());
        assertThrows(RuntimeException.class, () -> LegacyOrderParser.getOrder(text + "\r\n"), "так было");
        assertEquals(json(one(text, TODAY)), json(one(text + "\r\n", TODAY)));
        assertEquals(json(one(text, TODAY)), json(one(text.replace("\r\n", "\n") + "\n", TODAY)));
    }

    @Test
    void cp866RoundTripOfCyrillic() {
        byte[] bytes = file(twoAdms()).getBytes(CP866);
        Order o = one(new String(bytes, CP866), TODAY);
        assertEquals("ГУП ТАДЖИКСКИЙ АЛЮМИНИЙ", o.getOtprname());
    }

    // ── ошибки называют место ──

    private static String error(String text) {
        return assertThrows(OrderFormatException.class, () -> OrderFileParser.parseFile(text, TODAY)).getMessage();
    }

    @Test
    void errorsAreSpecific() {
        assertEquals("Файл пустой", error("  "));
        assertTrue(error("только одна строка").contains("строка 1: полей 1"), error("только одна строка"));
        assertEquals("В файле нет ни одной заявки", error(HEADER + "\r\n\r\n"));

        List<String> bad = twoAdms();
        bad.set(3, "2026-11-15");
        assertTrue(error(file(bad)).contains("строка 2, поле 4 «Дата погрузки»"), error(file(bad)));

        bad = twoAdms();
        bad.set(3, "31022026");
        assertTrue(error(file(bad)).contains("такой даты нет"));

        bad = twoAdms();
        bad.set(12, "74A504");
        assertTrue(error(file(bad)).contains("поле 13 «Станция отправления»: ожидалось число, получено «74A504»"));

        bad = twoAdms();
        bad.set(41, "300");
        assertTrue(error(file(bad)).contains("«Результат» (администрация 1): число 300 вне 0…255"), error(file(bad)));

        bad = twoAdms();
        bad.set(10, new String(new char[51]).replace('\0', 'А'));
        assertTrue(error(file(bad)).contains("«Отправитель»: длина 51, допустимо 50"));

        assertTrue(error(file(orderValues().subList(0, 30))).contains("строка 2: полей 30, а у заявки их не меньше 35"));

        List<String> ragged = orderValues();
        ragged.addAll(Arrays.asList("66", "746000", "ЭКСП"));
        assertTrue(error(file(ragged)).contains("не делится на группы по 8"));
    }

    /** Как в архиве: одна строка — одна заявка; у 20 % файлов их больше одной. */
    @Test
    void severalOrdersInOneFile() {
        List<String> second = orderValues();
        second.set(6, "0000012346");
        second.addAll(detail("66", "746000", "0", ""));
        second.set(34, "1");
        String text = file(twoAdms()) + "\r\n" + String.join("^", second) + "^\r\n";
        List<Order> all = OrderFileParser.parseFile(text, TODAY);
        assertEquals(2, all.size());
        assertEquals("0000012345", all.get(0).getOrderno());
        assertEquals("0000012346", all.get(1).getOrderno());
        assertEquals(1, all.get(1).getOrderdetails().size());
        assertEquals(HEADER, all.get(1).getHeader(), "идентификатор — у каждой заявки файла");
    }

    /** «Вид строки» 001: у администрации 6 полей — без результата и причины (main.pas). */
    @Test
    void strview001HasSixFieldGroups() {
        List<String> v = orderValues();
        v.set(2, "001");
        v.addAll(Arrays.asList("66", "746000", "ЭКСП", "12", "0066", "ГУП \"РОТ\""));
        v.addAll(Arrays.asList("29", "746805", "ЭКСП-2", "13", "0029", "УТИ"));
        Order o = one(file(v), TODAY);
        assertEquals(2, o.getOrderdetails().size());
        assertEquals("ЭКСП-2", o.getOrderdetails().get(1).getExpname());
        assertNull(o.getOrderdetails().get(1).getResult());
    }

    /** Строка «R…» — тоже идентификатор (ответные файлы), как в main.pas. */
    @Test
    void identifierLinesStartWithPOrR() {
        String text = file(twoAdms()).replace(HEADER, "RYPL_K2710-2026-10-16-15.17.45.005159_NV_2013.1");
        assertEquals("RYPL_K2710-2026-10-16-15.17.45.005159_NV_2013.1", one(text, TODAY).getHeader());
        String tooLong = file(twoAdms()).replace(HEADER, HEADER + "-EXTRA-TEXT-EXTRA");
        assertTrue(error(tooLong).contains("идентификатор файла длиннее 50"));
    }

    @Test
    void fieldTablesMatchEntityColumns() {
        assertEquals(35, OrderFileParser.FIELDS.size());
        assertEquals(8, OrderFileParser.DETAIL_FIELDS.size());
        // Каждое поле таблиц существует в сущности (разбор не упадёт на отражении).
        one(file(twoAdms()), TODAY);
    }
}
