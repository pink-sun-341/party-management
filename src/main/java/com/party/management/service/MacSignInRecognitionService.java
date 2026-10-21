package com.party.management.service;

import com.party.management.model.Member;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MacSignInRecognitionService implements SignInRecognitionService {
    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final Duration OCR_TIMEOUT = Duration.ofSeconds(90);

    @Override
    public RecognitionResult recognize(MultipartFile file, List<Member> members) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择签到表文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("签到表不能超过 10MB");
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extension(originalName);
        String text;
        if (Set.of("txt", "csv", "tsv").contains(extension)) {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else if (Set.of("jpg", "jpeg", "png", "heic", "heif").contains(extension)) {
            ImageRecognitionResult imageResult = recognizeImage(file, members);
            return imageResult.toRecognitionResult(members);
        } else {
            throw new IllegalArgumentException("支持 TXT、CSV、TSV、JPG、PNG 或 HEIC 格式");
        }

        List<String> names = splitNames(text);
        List<Member> matched = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        Set<Long> matchedIds = new LinkedHashSet<>();
        for (String name : names) {
            Member member = findMember(name, members);
            if (member != null) {
                if (matchedIds.add(member.getId())) {
                    matched.add(member);
                }
            } else if (!name.isBlank() && !unmatched.contains(name)) {
                unmatched.add(name);
            }
        }
        return new RecognitionResult(names, matched, unmatched, List.of());
    }

    private ImageRecognitionResult recognizeImage(MultipartFile file, List<Member> members) throws IOException {
        Path image = Files.createTempFile("party-sign-in-", "." + extension(file.getOriginalFilename()));
        Path memberFile = Files.createTempFile("party-sign-in-members-", ".txt");
        Path moduleCache = Files.createTempDirectory("party-swift-cache-");
        try {
            file.transferTo(image);
            Files.write(memberFile, members.stream().map(Member::getName).toList(), StandardCharsets.UTF_8);
            Path script = Files.createTempFile("party-vision-ocr-", ".swift");
            try {
                try (var input = new ClassPathResource("ocr/macos_vision_ocr.swift").getInputStream()) {
                    Files.copy(input, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                Process process = new ProcessBuilder("/usr/bin/swift", "-module-cache-path", moduleCache.toString(),
                                script.toString(), image.toString(), memberFile.toString())
                        .redirectErrorStream(true)
                        .start();
                try {
                    if (!process.waitFor(OCR_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        throw new IOException("签到表图片识别超时，请改用 CSV 或 TXT 文件");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("签到表图片识别被中断", ex);
                }
                String output;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    output = reader.lines().collect(Collectors.joining("\n"));
                }
                if (process.exitValue() != 0) {
                    String detail = output.lines().reduce((first, second) -> second).orElse("").trim();
                    if (detail.length() > 180) detail = detail.substring(Math.max(0, detail.length() - 180));
                    throw new IOException(detail.isBlank() ? "当前电脑未能完成图片识别，请重启应用后重试" : "图片识别程序启动失败：" + detail);
                }
                return parseImageResult(output);
            } finally {
                Files.deleteIfExists(script);
            }
        } finally {
            Files.deleteIfExists(image);
            Files.deleteIfExists(memberFile);
            try (var paths = Files.walk(moduleCache)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        }
    }

    private static ImageRecognitionResult parseImageResult(String output) throws IOException {
        List<ImageMatch> matches = new ArrayList<>();
        for (String line : output.lines().toList()) {
            if (!line.startsWith("RESULT\t")) continue;
            String[] parts = line.split("\\t", -1);
            if (parts.length < 5) continue;
            matches.add(new ImageMatch(parts[1], Boolean.parseBoolean(parts[2]),
                    Boolean.parseBoolean(parts[3]), parts.length > 5 ? parts[5] : ""));
        }
        if (matches.isEmpty()) {
            throw new IOException("未能识别签到表中的党员名单，请拍摄清晰、正对表格的照片");
        }
        return new ImageRecognitionResult(matches);
    }

    private record ImageMatch(String name, boolean recognized, boolean present, String evidence) {}

    private record ImageRecognitionResult(List<ImageMatch> matches) {
        private RecognitionResult toRecognitionResult(List<Member> members) {
            List<String> recognizedNames = matches.stream().filter(ImageMatch::recognized).map(ImageMatch::name).toList();
            List<String> absentNames = matches.stream().filter(match -> match.recognized() && !match.present()).map(ImageMatch::name).toList();
            List<String> unmatchedNames = matches.stream().filter(match -> !match.recognized()).map(ImageMatch::name).toList();
            Set<String> presentNames = matches.stream().filter(match -> match.recognized() && match.present())
                    .map(ImageMatch::name).collect(java.util.stream.Collectors.toSet());
            List<Member> matchedMembers = members.stream().filter(member -> presentNames.contains(member.getName())).toList();
            return new RecognitionResult(recognizedNames, matchedMembers, unmatchedNames, absentNames);
        }
    }

    private static List<String> splitNames(String text) {
        return text.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split("[,，;；\\t|]")))
                .map(String::trim)
                .map(value -> value.replaceAll("^[-*•、.。编号：: ]+", "").trim())
                .filter(value -> !value.isBlank())
                .filter(value -> !value.matches(".*(签到|姓名|日期|时间|活动|单位|序号|签名|备注).*"))
                .distinct()
                .toList();
    }

    private static Member findMember(String text, List<Member> members) {
        String candidate = text.replaceAll("\\s+", "");
        return members.stream()
                .filter(member -> candidate.equals(member.getName()) || candidate.contains(member.getName()))
                .findFirst()
                .orElse(null);
    }

    private static String extension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
