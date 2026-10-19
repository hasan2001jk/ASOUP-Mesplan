package tj.railway.mesplan.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Исходные файлы заявок — по идентификатору файла (header).
 *
 * Ответный файл собирается из строк исходного: старая программа хранила каждое
 * поле текстом и писала его как пришло, а у нас числа лежат в числовых колонках
 * и ведущие нули теряются. Поэтому при загрузке файл кладётся сюда как есть, а
 * ответ меняет в его строках только то, что изменилось в базе (AnswerFileWriter).
 *
 * Папка — mesplan.files.dir (переменная MESPLAN_FILES_DIR), по умолчанию «files»
 * рядом со службой. Имя файла — идентификатор; принимается только безопасный
 * (буквы, цифры, «_», «-», «.», без «..»), иначе файл не хранится и ответ
 * пишется в стандартной ширине.
 */
@Component
public class OriginalFiles {

    private static final Logger log = LoggerFactory.getLogger(OriginalFiles.class);

    static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.\\-]{0,49}");

    private final Path dir;

    public OriginalFiles(@Value("${mesplan.files.dir:files}") String dir) {
        this.dir = Paths.get(dir).toAbsolutePath().normalize();
    }

    public static boolean isSafe(String header) {
        return header != null && SAFE.matcher(header).matches() && !header.contains("..");
    }

    /** Сохранить исходный файл. Ошибка записи — не повод отказать в загрузке: ответ будет в стандартной ширине. */
    public void save(String header, byte[] content) {
        if (!isSafe(header)) {
            log.warn("Идентификатор файла «{}» не годится для имени — исходный файл не сохранён", header);
            return;
        }
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, ".upload-", ".tmp");
            Files.write(tmp, content);
            Files.move(tmp, dir.resolve(header), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Не удалось сохранить исходный файл {} в {}", header, dir, e);
        }
    }

    public Optional<byte[]> load(String header) {
        if (!isSafe(header)) {
            return Optional.empty();
        }
        Path p = dir.resolve(header);
        try {
            return Files.isRegularFile(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            log.error("Не удалось прочитать исходный файл {}", p, e);
            return Optional.empty();
        }
    }

    Path dir() {
        return dir;
    }
}
