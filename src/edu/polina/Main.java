
package edu.polina;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final byte[] BOM = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final Charset UTF_8 = StandardCharsets.UTF_8;


    public static void main(String[] args) {
        List<String> argsList = Arrays.asList(args);
        boolean addBom = argsList.contains("--BOM");
        System.out.println("Флаг BOM включен?" + addBom);
        convert(Paths.get("C://Test"), addBom);
    }

    public static void convert(Path file, boolean addBom) {
        if (Files.isDirectory(file)) {
            try (Stream<Path> paths = Files.walk(file)) {
                paths.filter(Files :: isRegularFile)
                        .filter(p -> p.toString().endsWith(".txt"))
                        .forEach(p -> convertFile(p, addBom));

            } catch (IOException e) {
                System.out.println("Ошибка конвертации");
                e.getStackTrace();
            }
            System.out.println("Директория конвертирована");
        }
        else {
            convertFile(file, addBom);
            System.out.println("Файл конвертирован");
        }
    }

    public static void convertFile(Path file, boolean addBom)  {


        if (!(Files.exists(file))) {
            System.out.println("Файл не найден");
            return;
        }


        if (isFileBinary(file)) {
            System.out.println("Обнаружен бинарный файл");
            return;
        }

        try (FileInputStream in = new FileInputStream(file.toString())) {
            byte[] sample = new byte[1024];
            in.read(sample);
            if (sample[0] == BOM[0] &&
                    sample[1] == BOM[1] && sample[2] == BOM[2]) {
                System.out.println("Файл уже в UTF-8 с BOM: " + file);
                return;
            }
        } catch (IOException e) {
            e.getStackTrace();
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
            System.out.println(e.getMessage());
            // Не UTF-8
        }

        if (isUtf8) {
            System.out.println("Файл уже в UTF-8: " + file);
            return;
        }

        // Создаем временный файл для конвертации
        Path tempFile = Paths.get(file + ".tmp");
        try {
            tempFile = Files.createTempFile("convert_", ".tmp");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        boolean hasErrors = false;

        // Конвертация из windows-1251 в UTF-8
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), WINDOWS_1251));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(Files.newOutputStream(tempFile), UTF_8))) {

            // Добавляем BOM если нужно
            if (addBom) {
                try (OutputStream out = new FileOutputStream(tempFile.toString())) {
                    out.write(BOM);
                }
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
            Path bakFile = Paths.get(file + ".bak");
            Files.copy(file, bakFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            if(hasErrors) {
                System.out.println(file.getFileName() + "Сконвертирован с ошибками");
            }
            else {
                System.out.println(file.getFileName() + "Сконвертирован успешно");
            }

        } catch (IOException e) {
            e.getStackTrace();
        }

    }

    public static boolean isFileBinary(Path file) {
        byte[] bytes = new byte[1024];
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            int sum = in.read(bytes);
            for (int i = 0; i < sum; i++) {
                if (bytes[i] == 0) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка проверки на бинарность" + e.getMessage());
        }
        return false;
    }

}
