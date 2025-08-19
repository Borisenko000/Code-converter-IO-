
package edu.polina;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.stream.Stream;

public class Main {
    private static final byte[] BOM = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Введите путь к файлу или директории: ");
        String path = scanner.nextLine();
        System.out.print("Добавить BOM? (y/n): ");
        boolean addBom = scanner.nextLine().toLowerCase().startsWith("y");
        scanner.close();

        Path targetPath = Paths.get(path);
        if (Files.isDirectory(targetPath)) {
            // Пакетная конвертация с сохранением структуры
            try (Stream<Path> paths = Files.walk(targetPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".txt"))
                        .forEach(file -> convertFile(file, addBom));
            }
        } else if (Files.isRegularFile(targetPath)) {
            convertFile(targetPath, addBom);
        } else {
            System.err.println("Путь не существует: " + path);
        }
    }

    private static void convertFile(Path file, boolean addBom) {
        try {
            // Проверка на бинарный файл (наличие нулевых байтов)
            byte[] sample = new byte[1024];
            try (InputStream is = Files.newInputStream(file)) {
                int bytesRead = is.read(sample);
                for (int i = 0; i < bytesRead; i++) {
                    if (sample[i] == 0) {
                        System.out.println("Пропущен бинарный файл: " + file);
                        return;
                    }
                }
            }

            // Проверка на UTF-8 с BOM
            if (sample.length >= 3 && sample[0] == BOM[0] &&
                    sample[1] == BOM[1] && sample[2] == BOM[2]) {
                System.out.println("Файл уже в UTF-8 с BOM: " + file);
                return;
            }

            // Попытка прочитать как UTF-8
            boolean isUtf8 = false;
            try (BufferedReader testReader = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file), UTF_8))) {
                char[] buffer = new char[1024];
                int charsRead = testReader.read(buffer);
                // Если читается без ошибок и нет символа замены - файл уже UTF-8
                String test = new String(buffer, 0, charsRead);
                if (!test.contains("\uFFFD")) {
                    isUtf8 = true;
                }
            } catch (Exception e) {
                // Не UTF-8
            }

            if (isUtf8) {
                System.out.println("Файл уже в UTF-8: " + file);
                return;
            }

            // Создаем временный файл для конвертации
            Path tempFile = Files.createTempFile("convert_", ".tmp");
            boolean hasErrors = false;

            // Конвертация из windows-1251 в UTF-8
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file), WINDOWS_1251));
                 BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(Files.newOutputStream(tempFile), UTF_8))) {

                // Добавляем BOM если нужно
                if (addBom) {
                    Files.newOutputStream(tempFile).write(BOM);
                }

                // Читаем и пишем построчно для избежания OOM
                String line;
                while ((line = reader.readLine()) != null) {

// Проверка на битые последовательности
                    if (line.contains("\uFFFD")) {
                        hasErrors = true;
                    }
                    writer.write(line);
                    writer.newLine();
                }
            }

            // Создаем .bak файл
            Path bakFile = Paths.get(file.toString() + ".bak");
            Files.copy(file, bakFile, StandardCopyOption.REPLACE_EXISTING);

            // Заменяем оригинальный файл
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);

            if (hasErrors) {
                System.out.println("Конвертирован с ошибками: " + file);
            } else {
                System.out.println("Успешно конвертирован: " + file);
            }

        } catch (IOException e) {
            System.err.println("Ошибка конвертации " + file + ": " + e.getMessage());
        }
    }
}