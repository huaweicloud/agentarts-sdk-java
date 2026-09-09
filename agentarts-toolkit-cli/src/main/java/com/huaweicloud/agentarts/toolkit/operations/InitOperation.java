package com.huaweicloud.agentarts.toolkit.operations;

import com.huaweicloud.agentarts.sdk.core.Constants;
import com.huaweicloud.agentarts.toolkit.template.TemplateManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Init operation: creates project skeleton from templates.
 *
 * <p>Operation: initialize a new project from templates.</p>
 */
public final class InitOperation {

    private static final java.util.Set<String> SUPPORTED_TEMPLATES =
            java.util.Set.of("basic", "agentscope");

    private InitOperation() {
    }

    /**
     * Initialize a new AgentArts project.
     *
     * @param template template name (basic, agentscope)
     * @param name     project name
     * @param path     project directory
     * @param region   Huawei Cloud region
     * @param swrOrg   SWR organization (nullable, defaults to a generated name)
     * @param swrRepo  SWR repository (nullable, defaults to agent_&lt;name&gt;)
     */
    public static void initProject(String template, String name, String path,
                                    String region, String swrOrg, String swrRepo) throws IOException {
        if (!SUPPORTED_TEMPLATES.contains(template)) {
            throw new IllegalArgumentException("Unsupported template: " + template);
        }
        if (name == null || !name.matches("[a-z0-9][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException(
                    "name must be 1-63 lowercase letters, digits, or hyphens and start with a letter or digit");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("region must not be blank");
        }
        Path root = Paths.get(path).toAbsolutePath().normalize();
        Path projectDir = root.resolve(name).normalize();
        if (!projectDir.startsWith(root)) {
            throw new IllegalArgumentException("project path escapes the requested root");
        }
        Files.createDirectories(root);
        if (Files.exists(projectDir)) {
            throw new FileAlreadyExistsException(
                    "Refusing to overwrite existing project directory: " + projectDir);
        }
        Path staging = root.resolve("." + name + ".agentarts-init-"
                + java.util.UUID.randomUUID()).normalize();
        try {
            Path srcDir = staging.resolve("src/main/java/com/example");
            Files.createDirectories(srcDir);
            TemplateManager.renderToFile(
                    template + "/pom.xml.tpl", staging.resolve("pom.xml"),
                    "name", name, "region", region);
            TemplateManager.renderToFile(
                    template + "/Agent.java.tpl", srcDir.resolve("Agent.java"),
                    "name", name);
            String actualSwrOrg = swrOrg != null && !swrOrg.isBlank()
                    ? swrOrg : generateSwrOrgName(region);
            String actualSwrRepo = swrRepo != null && !swrRepo.isBlank()
                    ? swrRepo : "agent_" + name;
            TemplateManager.renderToFile(
                    "basic/config.yaml.tpl", staging.resolve(".agentarts_config.yaml"),
                    "name", name, "region", region,
                    "swr_org", actualSwrOrg,
                    "swr_repo", actualSwrRepo);
            TemplateManager.renderToFile(
                    "docker/Dockerfile.tpl", staging.resolve("Dockerfile"),
                    "name", name);
            moveIntoPlace(staging, projectDir);
        } catch (IOException | RuntimeException e) {
            try {
                deleteStagingDirectory(staging);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    private static void moveIntoPlace(Path staging, Path projectDir) throws IOException {
        try {
            Files.move(staging, projectDir, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(staging, projectDir);
        }
    }

    private static void deleteStagingDirectory(Path staging) throws IOException {
        if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(staging, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(
                        Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                        throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException error)
                        throws IOException {
                    if (error != null) {
                        throw error;
                    }
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
    }

    // ========================
    // SWR naming defaults (mirror the Python SDK's utils/swr_org.py)
    // ========================

    private static final int SWR_ORG_MAX_LENGTH = 64;
    private static final String SWR_ORG_PREFIX = "agentarts-";
    private static final String SWR_ORG_SUFFIX = "-org";
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate a default agent name ({@code myagent-xxxxx}), mirroring the
     * Python SDK's {@code generate_default_agent_name}.
     */
    public static String generateDefaultAgentName() {
        return "myagent-" + randomAlphanumeric(5);
    }

    /**
     * Generate a unique SWR organization name.
     *
     * <p>Format: {@code agentarts-{region_short}-{ak_identifier}-org}, where
     * {@code region_short} is a compact region form (e.g. {@code cnso2}) and
     * {@code ak_identifier} is the first 8 hex chars of the MD5 of the AK
     * (or 8 random chars when no AK is configured). Mirrors the Python SDK's
     * {@code generate_swr_org_name}.</p>
     */
    private static String generateSwrOrgName(String region) {
        String actualRegion = (region == null || region.isBlank())
                ? Constants.DEFAULT_REGION : region;
        String regionShort = shortenRegion(actualRegion);
        String akIdentifier = generateAkIdentifier(8);

        String baseName = SWR_ORG_PREFIX + regionShort + "-" + akIdentifier + SWR_ORG_SUFFIX;
        if (baseName.length() > SWR_ORG_MAX_LENGTH) {
            int excess = baseName.length() - SWR_ORG_MAX_LENGTH;
            akIdentifier = generateAkIdentifier(8 - Math.min(excess, 8));
            baseName = SWR_ORG_PREFIX + regionShort + "-" + akIdentifier + SWR_ORG_SUFFIX;
            if (baseName.length() > SWR_ORG_MAX_LENGTH) {
                regionShort = regionShort.substring(0, regionShort.length()
                        - Math.min(baseName.length() - SWR_ORG_MAX_LENGTH, regionShort.length()));
                baseName = SWR_ORG_PREFIX + regionShort + SWR_ORG_SUFFIX;
            }
        }
        return baseName.toLowerCase(Locale.ROOT);
    }

    /**
     * Shorten a region name to a compact form (e.g. cn-southwest-2 -&gt; cnso2).
     */
    private static String shortenRegion(String region) {
        String[] parts = region.split("-");
        if (parts.length >= 3) {
            String provider = firstChars(parts[0], 2);
            String area = firstChars(parts[1], 2);
            String num = parts[2];
            if (num.length() > 2) {
                num = num.substring(0, 2);
            }
            return provider + area + num;
        }
        return firstChars(region.replace("-", ""), 6);
    }

    private static String firstChars(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Derive an identifier of the given length from the AK: the first
     * {@code length} hex chars of MD5(AK). Falls back to random alphanumeric
     * chars when no AK is configured.
     */
    private static String generateAkIdentifier(int length) {
        String ak = Constants.getAk();
        if (ak != null && !ak.isBlank()) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(ak.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest).substring(0, length);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("MD5 algorithm unavailable", e);
            }
        }
        return randomAlphanumeric(length);
    }

    private static String randomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
