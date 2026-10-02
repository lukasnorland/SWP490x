package com.funix.swp490x.mrs.aws;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProcessCredentialsProvider;

/**
 * Shared AWS credentials for SES and S3. Named profiles use CLI export to support
 * {@code aws login}; a blank profile uses the default credential chain.
 */
public final class AwsCredentialsFactory {

    private static final Pattern SAFE_PROFILE = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private AwsCredentialsFactory() {
    }

    /**
     * @param profile CLI profile, or blank for the default credential chain
     * @param propertyName configuration key to include in credential errors
     */
    public static AwsCredentialsProvider forProfile(String profile, String propertyName) {
        if (!StringUtils.hasText(profile)) {
            return DefaultCredentialsProvider.create();
        }

        String trimmed = profile.trim();
        if (!SAFE_PROFILE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid " + propertyName + ": " + profile);
        }

        return ProcessCredentialsProvider.builder()
                .command(exportCredentialsCommand(trimmed))
                .build();
    }

    private static List<String> exportCredentialsCommand(String profile) {
        List<String> command = new ArrayList<>();
        String aws = resolveAwsExecutable();
        boolean directExe = WINDOWS && aws.endsWith(".exe");

        if (!directExe && WINDOWS) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(aws);
        command.add("configure");
        command.add("export-credentials");
        command.add("--profile");
        command.add(profile);
        command.add("--format");
        command.add("process");
        return command;
    }

    /**
     * Finds {@code aws.exe} / {@code aws.cmd} on PATH, then common install
     * locations. Falls back to {@code aws} and lets the shell resolve it.
     */
    static String resolveAwsExecutable() {
        String fromPath = findOnPath("aws.exe", "aws.cmd");
        if (fromPath != null) {
            return fromPath;
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        List<String> fallbacks = new ArrayList<>();
        fallbacks.add("C:\\Program Files\\Amazon\\AWSCLIV2\\aws.exe");
        if (StringUtils.hasText(localAppData)) {
            fallbacks.add(localAppData + "\\Programs\\Amazon\\AWSCLIV2\\aws.exe");
        }
        for (String candidate : fallbacks) {
            if (candidate != null && Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        return "aws";
    }

    private static String findOnPath(String... names) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String dir : path.split(Pattern.quote(File.pathSeparator))) {
            if (dir.isBlank()) {
                continue;
            }
            for (String name : names) {
                Path candidate = Path.of(dir.trim(), name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        return null;
    }
}
