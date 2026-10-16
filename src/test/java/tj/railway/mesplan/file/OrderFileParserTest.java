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
        StringBuilder sb = new StringBuilder("(:МЕСПЛАН 66 15112026:)\r\n");
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
        Order o = OrderFileParser.parse(file(twoAdms()), TODAY);
        assertEquals("(:МЕСПЛАН 66 15112026:)", o.getHeader());
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
            Order now = OrderFileParser.parse(text, TODAY);
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
                OrderFileParser.parse(text, TODAY).getOrderdetails().get(1).getDenyreason());
    }

    @Test
    void acceptsMissingEmptyLastReasonLikeLegacy() {
        List<String> v = orderValues();
        v.addAll(detail("66", "746000", "1", "x").subList(0, 7));
        Order o = OrderFileParser.parse(file(v), TODAY);
        assertEquals("", o.getOrderdetails().get(0).getDenyreason());
    }

    /** Файл с переводом строки в конце: исходник уходил в цикл по «\r\n» и падал. */
    @Test
    void trailingNewlineIsHarmless() throws Exception {
        String text = file(twoAdms());
        assertThrows(RuntimeException.class, () -> LegacyOrderParser.getOrder(text + "\r\n"), "так было");
        assertEquals(json(OrderFileParser.parse(text, TODAY)), json(OrderFileParser.parse(text + "\r\n", TODAY)));
        assertEquals(json(OrderFileParser.parse(text, TODAY)), json(OrderFileParser.parse(text.replace("\r\n", "\n") + "\n", TODAY)));
    }

    @Test
    void cp866RoundTripOfCyrillic() {
        byte[] bytes = file(twoAdms()).getBytes(CP866);
        Order o = OrderFileParser.parse(new String(bytes, CP866), TODAY);
        assertEquals("ГУП ТАДЖИКСКИЙ АЛЮМИНИЙ", o.getOtprname());
    }

    // ── ошибки называют место ──

    private static String error(String text) {
        return assertThrows(OrderFormatException.class, () -> OrderFileParser.parse(text, TODAY)).getMessage();
    }

    @Test
    void errorsAreSpecific() {
        assertEquals("Файл пустой", error("  "));
        assertTrue(error("только одна строка").contains("одна строка"));

        List<String> bad = twoAdms();
        bad.set(3, "2026-11-15");
        assertTrue(error(file(bad)).contains("поле 4 «Дата погрузки»"), error(file(bad)));

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

        assertTrue(error(file(orderValues().subList(0, 30))).contains("полей 30, а у заявки их 35"));

        List<String> ragged = orderValues();
        ragged.addAll(Arrays.asList("66", "746000", "ЭКСП"));
        assertTrue(error(file(ragged)).contains("не делится на группы по 8"));
    }

    @Test
    void severalDataLinesAreRefusedNotTruncated() {
        String text = file(twoAdms()) + "\r\n" + String.join("^", orderValues()) + "^";
        assertTrue(error(text).contains("больше одной строки"), "вторая заявка не теряется молча");
    }

    @Test
    void fieldTablesMatchEntityColumns() {
        assertEquals(35, OrderFileParser.FIELDS.size());
        assertEquals(8, OrderFileParser.DETAIL_FIELDS.size());
        // Каждое поле таблиц существует в сущности (разбор не упадёт на отражении).
        OrderFileParser.parse(file(twoAdms()), TODAY);
    }
}
