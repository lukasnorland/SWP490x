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
 * Resolves credentials for every AWS client in the app, so SES and S3 cannot
 * drift apart on which account they talk to.
 *
 * <p>A named profile is resolved through {@code aws configure
 * export-credentials} rather than
 * {@link software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider}:
 * {@code aws login} stores a {@code login_session} that the profile provider
 * cannot read.
 *
 * <p>On Windows, {@code aws} is resolved to a full path when the JVM's PATH
 * does not include the CLI (common when the app is started from an IDE). When
 * only a bare name is available, the export is launched via {@code cmd /c}.
 *
 * <p>An empty profile falls back to the default credential chain, which is what
 * the EC2 instance role needs.
 */
public final class AwsCredentialsFactory {

    private static final Pattern SAFE_PROFILE = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private AwsCredentialsFactory() {
    }

    /**
     * @param profile named CLI profile, or blank for the default chain
     * @param propertyName the property the profile came from, named in the
     *     exception so a typo points at the setting that caused it
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
