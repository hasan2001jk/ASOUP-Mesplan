package tj.railway.mesplan.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnswerFileTest {

    /** Настоящий файл архива (2026/10/16), две заявки. */
    static final String REAL = "PL_K6610-2026-10-15-14.59.56.666397_NV_2013.1\r\n"
            + "20^PL_K^2^17102026^3^643^6310001817^2^0^03^АО \"ТАИФ-НК\"^ООО \"Нафт Наклиёт\"^648202^745309^214039^27101944^762^00000000000000000000^^^77^2^06020^100^тат-оил 100-0027/20^1^0000^0000^^^^0000000^    ^^3^27^813008^ТОО Транс Энерго Сервис^10001964            ^0027^АО \"КТЖ-ГП\"^0^^29^704101^\"Узбэксжд\"^2941118             ^0029^ГАЖК \"УТИ\"^0^^66^736206^ТАДЖДЭ^1                   ^0066^ГУП \"РОТ\"^0^\r\n"
            + "20^PL_K^2^17102026^3^643^6310001818^2^0^03^АО \"ТАИФ-НК\"^ООО \"Нафт Наклиёт\"^648202^747802^214039^27101944^762^00000000000000000000^^^77^2^04010^066^тат оил^1^0000^0000^^^^0000000^    ^^3^27^813008^ТОО Транс Энерго Сервис^10001964            ^0027^АО \"КТЖ-ГП\"^0^^29^704101^\"Узбэксжд\"^2941118             ^0029^ГАЖК \"УТИ\"^0^^66^726204^ТАДЖДЭ^1                   ^0066^ГУП \"РОТ\"^0^\r\n";

    static List<String> dataLines(String text) {
        List<String> out = new ArrayList<>(Arrays.asList(text.split("\r\n")));
        out.remove(0);
        return out;
    }

    // ── имя ответного файла ──

    /** Как в квитанции 16.10.2026: «PL_K6610-2026-10-16-09.34.02.826123_NV_2013.1». */
    @Test
    void identifierLikeTheOldProgram() {
        LocalDateTime local = LocalDateTime.of(2026, 10, 16, 10, 34, 2, 826_000_000);
        assertEquals("PL_K6610-2026-10-16-09.34.02.826123_NV_2013.1",
                AnswerFileWriter.identifier("PL_K6610-2026-10-15-14.59.56.666397_NV_2013.1", 10, local, 1));
    }

    /** Миллисекунд меньше 100 — две цифры, и до 31 знака дополняется «0»: «…331230». */
    @Test
    void shortMillisArePaddedWithZero() {
        LocalDateTime local = LocalDateTime.of(2026, 10, 16, 10, 34, 59, 33_000_000);
        assertEquals("PL_K6610-2026-10-16-09.34.59.331230_NV_2013.1",
                AnswerFileWriter.identifier("PL_K6610-x", 10, local, 1));
        assertEquals("PLAN6609-2026-09-22-08.00.00.001230_NV_2013.1",
                AnswerFileWriter.identifier("PLAN6609-2026-09-22-10.31.48.579123_NV_2013.1", 9,
                        LocalDateTime.of(2026, 9, 22, 9, 0, 0, 0), 1));
        assertTrue(AnswerFileWriter.identifier(null, 1, LocalDateTime.now(), 1).startsWith("PL_K6601-"));
    }

    // ── содержимое ──

    @Test
    void untouchedAnswerIsTheOriginal() {
        List<Order> orders = OrderFileParser.parseFile(REAL, LocalDate.now());
        String out = AnswerFile.build("PL_K6610-ID", orders, REAL);
        assertEquals("PL_K6610-ID", out.split("\r\n")[0]);
        assertEquals(dataLines(REAL), dataLines(out));
        assertTrue(out.endsWith("\r\n"));
    }

    @Test
    void ourAnswerChangesOnlyResultAndReason() {
        List<Order> orders = OrderFileParser.parseFile(REAL, LocalDate.now());
        Orderdetail ours = orders.get(1).getOrderdetails().get(2);
        assertEquals(66, ours.getAdm());
        ours.setResult((short) 2);
        ours.setDenyreason("нет гарантийного письма грузополучателя");
        List<String> out = dataLines(AnswerFile.build("ID", orders, REAL));
        assertEquals(dataLines(REAL).get(0), out.get(0), "первая заявка не тронута");
        assertEquals(dataLines(REAL).get(1).replace("^0066^ГУП \"РОТ\"^0^", "^0066^ГУП \"РОТ\"^2^нет гарантийного письма грузополучателя"),
                out.get(1));
    }

    /** Заявки из базы могут прийти в другом порядке — строка находится по ключу. */
    @Test
    void linesAreMatchedByKeyNotPosition() {
        List<Order> orders = OrderFileParser.parseFile(REAL, LocalDate.now());
        Collections.reverse(orders);
        List<String> out = dataLines(AnswerFile.build("ID", orders, REAL));
        assertEquals(dataLines(REAL).get(1), out.get(0));
        assertEquals(dataLines(REAL).get(0), out.get(1));
    }

    /** Нет исходного файла — стандартная ширина: поля на месте, ведущие нули по архиву. */
    @Test
    void withoutOriginalStandardWidths() {
        List<Order> orders = OrderFileParser.parseFile(REAL, LocalDate.now());
        String line = dataLines(AnswerFile.build("ID", orders, null)).get(0);
        assertTrue(line.startsWith("20^PL_K^2^17102026^3^643^6310001817^2^0^03^"), line);
        assertTrue(line.contains("^06020^100^"), "нетто 5 знаков, вагоны 3");
        assertTrue(line.contains("^10001964            ^"), "код экспедитора — 20 знаков");
        assertEquals(35 + 3 * 8, line.split("\\^", -1).length);
        assertEquals(orders.get(0).getOrderno(),
                OrderFileParser.parseFile("PL_K-ID\r\n" + line, LocalDate.now()).get(0).getOrderno(), "читается обратно");
    }

    /** Администрацию добавили или убрали — группы пишутся заново, «Кол-во адм» совпадает. */
    @Test
    void changedAdministrationsAreRewritten() {
        List<Order> orders = OrderFileParser.parseFile(REAL, LocalDate.now());
        orders.get(0).getOrderdetails().remove(1);
        String line = dataLines(AnswerFile.build("ID", orders, REAL)).get(0);
        String[] t = line.split("\\^", -1);
        assertEquals("2", t[34]);
        assertEquals(35 + 2 * 8, t.length);
        assertTrue(line.startsWith(dataLines(REAL).get(0).substring(0, 120)), "поля заявки — как пришли");
    }

    // ── хранилище исходных файлов ──

    @Test
    void originalStoreAcceptsOnlySafeNames(@TempDir Path dir) {
        OriginalFiles files = new OriginalFiles(dir.toString());
        files.save("PL_K6610-2026-10-15-14.59.56.666397_NV_2013.1", new byte[]{1, 2});
        assertArrayEquals(new byte[]{1, 2}, files.load("PL_K6610-2026-10-15-14.59.56.666397_NV_2013.1").get());
        for (String bad : new String[]{"../x", "a/b", "..", ".hidden", "a\\b", null, ""}) {
            assertFalse(OriginalFiles.isSafe(bad), String.valueOf(bad));
            files.save(bad, new byte[]{9});
            assertFalse(files.load(bad).isPresent());
        }
        assertEquals(1, dir.toFile().list().length, "ничего лишнего не записано");
    }
}
