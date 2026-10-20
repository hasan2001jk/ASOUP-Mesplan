package tj.railway.mesplan.file;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tj.railway.mesplan.entity.Order;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Правила ответа (AnswerRules) против того, что сеть МЕСПЛАН на самом деле приняла и отклонила.
 *
 * Вход — triples.tsv: имя ответа старой программы (2017–2019), коды ошибок из квитанции RC…
 * на эту строку («-» — принята), входящая строка (на что отвечали) и строка ответа. Собирается
 * из docs/mesplan_messages скриптом в docs/АНАЛИЗ-И-ПЛАН.md §2г; в git его нет, поэтому:
 *   gradle test --tests '*ArchiveAnswerRulesTest' -Dmesplan.answers=<папка с triples.tsv>
 * Без пути тест пропускается.
 */
class ArchiveAnswerRulesTest {

    @Test
    void networkVerdicts() throws IOException {
        String dir = System.getProperty("mesplan.answers");
        Assumptions.assumeTrue(dir != null && !dir.trim().isEmpty() && Files.isDirectory(Paths.get(dir)), "пары не указаны");
        LocalDate today = LocalDate.of(2026, 10, 20);
        int accepted = 0, acceptedFlagged = 0, rejected = 0, kindRejected = 0, kindFixed = 0, informationalSent = 0;
        int informationalRejected = 0, unparsed = 0, shifted = 0, otherKind = 0, logicRejected = 0, logicCaught = 0;
        List<String> logicMissed = new ArrayList<>();
        Map<String, Integer> flaggedReasons = new TreeMap<>();
        Map<String, Integer> rejectedCodes = new TreeMap<>();
        List<String> falsePositives = new ArrayList<>();
        for (String row : Files.readAllLines(Paths.get(dir, "triples.tsv"), StandardCharsets.UTF_8)) {
            String[] c = row.split("\t", -1);
            String codes = c[1];
            Order original, answer;
            try {
                original = OrderFileParser.parseLine(c[2], 0, today);
                answer = OrderFileParser.parseLine(c[3], 0, today);
            } catch (OrderFormatException e) {
                unparsed++;
                continue;
            }
            String sentKind = c[3].split("\\^", -1)[2].trim();
            if (c[2].split("\\^", -1).length != c[3].split("\\^", -1).length) {
                // Старая программа сдвинула поля при записи — такую строку наш ответ не создаёт.
                shifted++;
                continue;
            }
            String kind = AnswerRules.answerKind(original.getStrview());
            boolean ok = "-".equals(codes);
            if (kind == null) {
                // На строки «для сведения» мы не отвечаем вовсе.
                informationalSent++;
                if (!ok) {
                    informationalRejected++;
                }
                continue;
            }
            if (!ok && codes.matches("(0078|0104|0105|0106|0145|0147|0148|0112|0113|0073)(,.*)?")) {
                // Отказ по самому ответу — правила должны его поймать.
                logicRejected++;
                if (!AnswerRules.problems(answer, kind, original).isEmpty()) {
                    logicCaught++;
                } else if (logicMissed.size() < 5) {
                    logicMissed.add(c[0] + " " + codes + " результат66=" + AnswerRules.ours(answer).get(0).getResult());
                }
            }
            if (!ok) {
                rejected++;
                for (String code : codes.split(",")) {
                    rejectedCodes.merge(code, 1, Integer::sum);
                }
                if (codes.contains("0133")) {
                    kindRejected++;
                    if (!sentKind.equalsIgnoreCase(kind)) {
                        kindFixed++;   // мы пишем вид ответа сами — этой ошибки у нас не будет
                    }
                }
                continue;
            }
            if (!sentKind.equalsIgnoreCase(kind)) {
                // Старая программа ответила другим видом (например, «2s» на «7») — у нас так не будет.
                otherKind++;
                continue;
            }
            accepted++;
            List<String> p = AnswerRules.problems(answer, kind, original);
            if (!AnswerRules.answered(answer)) {
                // Принятая строка без нашего результата — у нас она просто не попала бы в файл.
                flaggedReasons.merge("без результата 66 (была бы пропущена, не заблокирована)", 1, Integer::sum);
                continue;
            }
            if (!p.isEmpty()) {
                acceptedFlagged++;
                for (String s : p) {
                    flaggedReasons.merge(s.replaceAll("\\d+", "N"), 1, Integer::sum);
                }
                if (falsePositives.size() < 10) {
                    falsePositives.add(c[0] + " | " + p);
                }
            }
        }
        System.out.println("Ответов на строки 2/7/8/10: принято сетью " + accepted + ", отклонено " + rejected
                + " (из них за вид строки " + kindRejected + " — у нас исправлено: " + kindFixed + ")");
        System.out.println("Отклонённые по кодам: " + rejectedCodes);
        System.out.println("Ответы на строки «для сведения» (у нас не отправляются): " + informationalSent
                + ", из них отклонено сетью " + informationalRejected);
        System.out.println("Принятых сетью, но запрещённых нашими правилами: " + acceptedFlagged + " " + flaggedReasons);
        falsePositives.forEach(s -> System.out.println("  " + s));
        System.out.println("Отказы по самому ответу: " + logicRejected + ", правила поймали " + logicCaught + " " + logicMissed);
        System.out.println("Принятые с другим видом строки (у нас не встретятся): " + otherKind);
        System.out.println("Не разобрано: " + unparsed + ", сдвиг полей у старой программы: " + shifted);

        assertTrue(accepted > 10_000, "пар мало: " + accepted);
        assertTrue(kindFixed >= kindRejected * 0.99,
                "ошибки «вид строки» — из-за вида, который мы теперь пишем сами: " + kindFixed + " из " + kindRejected);
        assertEquals(0, acceptedFlagged, "правила не должны запрещать то, что сеть приняла");
    }
}
