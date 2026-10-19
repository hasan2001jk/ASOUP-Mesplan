package tj.railway.mesplan.file;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Весь архив сообщений МЕСПЛАН (docs/mesplan_messages, 2018–2026): каждый файл
 * PL_K / PLAN / PL_E разбирается, каждая заявка записывается обратно и сравнивается
 * с исходной строкой.
 *
 * Архива нет в git (189 МБ), поэтому тест запускается, только если указан путь:
 *   gradle test --tests '*ArchiveRoundTripTest' -Dmesplan.archive=<папка>
 * В CI он пропускается.
 */
class ArchiveRoundTripTest {

    static final Charset CP866 = Charset.forName("IBM866");

    @Test
    void wholeArchive() throws IOException {
        String dir = System.getProperty("mesplan.archive");
        Assumptions.assumeTrue(dir != null && !dir.trim().isEmpty() && Files.isDirectory(Paths.get(dir)),
                "архив не указан");
        List<Path> files;
        try (Stream<Path> s = Files.walk(Paths.get(dir))) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("(PL_K|PLAN|PL_E).*"))
                    .sorted().collect(Collectors.toList());
        }
        int orders = 0, identical = 0, multi = 0, wholeFiles = 0, answeredOrders = 0, answeredOk = 0, standard = 0;
        List<String> keptExamples = new ArrayList<>();
        List<String> answerExamples = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Path p : files) {
            String text = new String(Files.readAllBytes(p), CP866);
            List<Order> parsed;
            try {
                parsed = OrderFileParser.parseFile(text, LocalDate.of(2026, 10, 19));
            } catch (OrderFormatException e) {
                failures.add(p.getFileName() + ": " + e.getMessage());
                continue;
            }
            if (parsed.size() > 1) {
                multi++;
            }
            List<String> lines = new ArrayList<>();
            for (String l : text.split("\n")) {
                l = l.endsWith("\r") ? l.substring(0, l.length() - 1) : l;
                if (!l.trim().isEmpty() && "PR".indexOf(Character.toUpperCase(l.charAt(0))) < 0) {
                    lines.add(l);
                }
            }
            assertEquals(lines.size(), parsed.size(), p.toString());
            // Ответный файл целиком без правок — те же строки, что во входящем.
            String answer = AnswerFile.build("ID", parsed, text);
            List<String> answerLines = new ArrayList<>(java.util.Arrays.asList(answer.split("\r\n")));
            answerLines.remove(0);
            if (answerLines.equals(lines)) {
                wholeFiles++;
            }
            for (int i = 0; i < parsed.size(); i++) {
                orders++;
                Order o = parsed.get(i);
                String original = lines.get(i);
                // 1. Без правок — строка исходного файла байт в байт.
                String kept = AnswerFileWriter.line(o, original);
                if (original.equals(kept)) {
                    identical++;
                } else if (keptExamples.size() < 10) {
                    keptExamples.add(p.getFileName() + " токенов " + original.split("\\^", -1).length + "/" + kept.split("\\^", -1).length + " стр.вида " + o.getStrview() + "\n    было: …" + original.substring(Math.max(0, original.length() - 90)).replace(" ", "·") + "\n    стало: …" + kept.substring(Math.max(0, kept.length() - 90)).replace(" ", "·"));
                }
                // 2. Ответ нашей администрации — меняются ровно результат и причина.
                Orderdetail ours = null;
                for (Orderdetail d : o.getOrderdetails()) {
                    if (Integer.valueOf(66).equals(d.getAdm())) {
                        ours = d;
                    }
                }
                if (ours != null && OrderFileParser.groupSize(o.getStrview()) == 8) {
                    ours.setResult((short) 2);
                    ours.setDenyreason("ПРОВЕРКА ОТВЕТА");
                    String answered = AnswerFileWriter.line(o, original);
                    String[] a = original.split("\\^", -1), b = answered.split("\\^", -1);
                    int changed = 0;
                    for (int k = 0; k < Math.max(a.length, b.length); k++) {
                        String x = k < a.length ? a[k] : null, y = k < b.length ? b[k] : null;
                        if (x == null || !x.equals(y)) {
                            changed++;
                        }
                    }
                    answeredOrders++;
                    boolean ok = b.length >= a.length && changed <= 2 && answered.contains("^2^ПРОВЕРКА ОТВЕТА");
                    if (ok) {
                        answeredOk++;
                    } else if (answerExamples.size() < 10) {
                        answerExamples.add(p.getFileName() + "\n    было: " + original + "\n    стало: " + answered);
                    }
                }
                // 3. Для сравнения: та же заявка без исходного текста (стандартная ширина).
                if (original.equals(AnswerFileWriter.line(parsed.get(i)))) {
                    standard++;
                }
            }
        }
        System.out.println("файлов " + files.size() + ", в нескольких заявок " + multi + ", заявок " + orders
                + ", не разобрано файлов " + failures.size());
        System.out.println("ответный файл без правок = входящий: " + wholeFiles + " из " + (files.size() - failures.size()));
        System.out.println("без правок, байт в байт: " + identical + " из " + orders);
        System.out.println("ответ нашей администрации, меняются только результат и причина: " + answeredOk + " из " + answeredOrders);
        System.out.println("для сравнения, без исходного текста (стандартная ширина): " + standard + " из " + orders);
        keptExamples.forEach(e -> System.out.println("  ≠ " + e));
        answerExamples.forEach(e -> System.out.println("  ≠ " + e));
        failures.stream().limit(20).forEach(f -> System.out.println("  ✗ " + f));
        assertTrue(failures.size() * 1000 < files.size(), "не разобрано больше 0.1 % файлов: " + failures.size());
        assertEquals(orders, identical, "без правок каждая строка должна выйти как была");
        assertEquals(files.size() - failures.size(), wholeFiles, "ответный файл без правок — строки входящего");
        assertEquals(answeredOrders, answeredOk, "ответ меняет только результат и причину");
    }

}
