package com.huaweicloud.agentarts.toolkit.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.huaweicloud.agentarts.sdk.runtime.AgentArtsRuntimeApp;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/** Runs a local agent in a managed child JVM with optional source reload. */
public final class DevOperation {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Pattern ENVIRONMENT_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(10);
    private static final long RELOAD_POLL_MILLIS = 750;
    private static final long RELOAD_DEBOUNCE_MILLIS = 250;
    private static final String DEV_CLASSPATH_FILE = "agentarts-dev-classpath.txt";
    private static final String DEV_LAUNCHER_JAR = "agentarts-dev-launcher-classpath.jar";
    private static final String DEPENDENCY_CLASSPATH_GOAL =
            "org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath";
    /** On Windows the child JVM's file handle on the launcher jar can outlive the
     * killed process for a few hundred milliseconds; retry deletion briefly. */
    private static final int ARTIFACT_DELETE_RETRIES = 40;
    private static final long ARTIFACT_DELETE_RETRY_MILLIS = 50;

    private DevOperation() {
    }

    /**
     * Start a local development server for the configured Java agent.
     *
     * <p>The agent runs in a child JVM so {@code --env} values are real environment
     * variables and can be consumed by user code and credential providers. With
     * reload enabled, source, resource, dependency, compiled-class, and config
     * changes restart a freshly loaded JVM.</p>
     */
    public static void runDevServer(int port, String host, boolean reload,
                                    String configPath, String projectPath,
                                    String[] envVars) throws Exception {
        validateAddress(port, host);
        Path project = resolveProjectPath(projectPath);
        File configFile = requireConfigFile(configPath, project);
        Map<String, String> commandLineEnvironment = parseEnvironment(envVars);

        prepareProject(project);
        DevConfiguration configuration = loadConfiguration(configFile, commandLineEnvironment);
        AtomicReference<Process> child = new AtomicReference<>();
        Thread shutdownHook = new Thread(
                () -> terminateTree(child.getAndSet(null)), "agentarts-dev-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            Process process = startChild(project, host, port, configuration);
            child.set(process);
            if (!reload) {
                int exitCode = process.waitFor();
                child.compareAndSet(process, null);
                if (exitCode != 0) {
                    throw new IllegalStateException(
                            "Development server exited with code " + exitCode);
                }
                return;
            }

            ProjectState previous = snapshot(project, configFile.toPath());
            System.out.println("Auto-reload enabled; watching Java sources, resources, config, and compiled output");
            while (!Thread.currentThread().isInterrupted()) {
                if (!process.isAlive()) {
                    int exitCode = process.exitValue();
                    child.compareAndSet(process, null);
                    throw new IllegalStateException(
                            "Development server exited with code " + exitCode);
                }
                Thread.sleep(RELOAD_POLL_MILLIS);
                ProjectState current = snapshot(project, configFile.toPath());
                if (current.equals(previous)) {
                    continue;
                }

                Thread.sleep(RELOAD_DEBOUNCE_MILLIS);
                current = snapshot(project, configFile.toPath());
                System.out.println("Development files changed; restarting agent...");
                try {
                    if (current.sources() != previous.sources()) {
                        prepareProject(project);
                    }
                    configFile = requireConfigFile(configPath, project);
                    configuration = loadConfiguration(configFile, commandLineEnvironment);
                } catch (RuntimeException | IOException reloadError) {
                    System.err.println("Reload preparation failed; keeping the current server: "
                            + reloadError.getMessage());
                    previous = snapshot(project, configFile.toPath());
                    continue;
                }

                terminateTree(child.getAndSet(null));
                process = startChild(project, host, port, configuration);
                child.set(process);
                previous = snapshot(project, configFile.toPath());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            terminateTree(child.getAndSet(null));
            // The child JVM loaded the launcher classpath jar via its system class
            // loader; on Windows the file handle can outlive the killed process for a
            // few hundred milliseconds, blocking deletion of the project directory
            // (and @TempDir cleanup in tests). Best-effort delete with retries.
            try {
                deleteDevArtifacts(project);
            } catch (RuntimeException ignored) {
                // Best effort; must not disrupt shutdown.
            }
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is already in progress.
            }
        }
    }

    /** Backwards-compatible overload (no project path). */
    public static void runDevServer(int port, String host, boolean reload,
                                    String configPath, String[] envVars) throws Exception {
        runDevServer(port, host, reload, configPath, null, envVars);
    }

    static void validateAddress(int port, String host) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
    }

    static Path resolveProjectPath(String projectPath) {
        String value = projectPath == null || projectPath.isBlank() ? "." : projectPath;
        Path project = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isDirectory(project)) {
            throw new IllegalArgumentException("Project directory does not exist: " + project);
        }
        return project;
    }

    static File resolveConfigFile(String configPath, String projectPath) {
        Path project = resolveProjectPath(projectPath);
        Path path = configPath == null || configPath.isBlank()
                ? project.resolve(".agentarts_config.yaml")
                : Path.of(configPath).toAbsolutePath().normalize();
        return Files.isRegularFile(path) ? path.toFile() : null;
    }

    private static File requireConfigFile(String configPath, Path project) {
        Path path = configPath == null || configPath.isBlank()
                ? project.resolve(".agentarts_config.yaml")
                : Path.of(configPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Configuration file does not exist: " + path);
        }
        return path.toFile();
    }

    @SuppressWarnings("unchecked")
    static String resolveEntrypoint(File configFile) {
        if (configFile == null) {
            return null;
        }
        try {
            Map<String, Object> root = YAML_MAPPER.readValue(configFile, Map.class);
            Map<String, Object> agent = resolveDefaultAgent(root);
            if (agent == null || !(agent.get("base") instanceof Map<?, ?> base)) {
                return null;
            }
            Object entrypoint = base.get("entrypoint");
            return entrypoint instanceof String value && !value.isBlank()
                    ? value.trim() : null;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to parse configuration file " + configFile + ": " + e.getMessage(), e);
        }
    }

    static Map<String, String> parseEnvironment(String[] envVars) {
        Map<String, String> result = new LinkedHashMap<>();
        if (envVars == null) {
            return result;
        }
        for (String entry : envVars) {
            if (entry == null) {
                throw new IllegalArgumentException("Environment variable must use KEY=VALUE format");
            }
            int separator = entry.indexOf('=');
            if (separator < 1) {
                throw new IllegalArgumentException(
                        "Environment variable must use KEY=VALUE format: " + entry);
            }
            String name = entry.substring(0, separator).trim();
            validateEnvironmentName(name);
            result.put(name, entry.substring(separator + 1));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> resolveConfiguredEnvironment(File configFile) {
        try {
            Map<String, Object> root = YAML_MAPPER.readValue(configFile, Map.class);
            Map<String, Object> agent = resolveDefaultAgent(root);
            if (agent == null || !(agent.get("runtime") instanceof Map<?, ?> runtime)) {
                return Map.of();
            }
            Object configured = runtime.get("environment_variables");
            Map<String, String> result = new LinkedHashMap<>();
            if (configured instanceof Map<?, ?> values) {
                values.forEach((key, value) -> addEnvironment(result, key, value));
            } else if (configured instanceof List<?> values) {
                for (Object item : values) {
                    if (item instanceof Map<?, ?> pair) {
                        addEnvironment(result, pair.get("key"), pair.get("value"));
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to parse configuration file " + configFile + ": " + e.getMessage(), e);
        }
    }

    private static void addEnvironment(Map<String, String> target, Object key, Object value) {
        if (!(key instanceof String name) || name.isBlank()) {
            return;
        }
        validateEnvironmentName(name);
        target.put(name, value == null ? "" : String.valueOf(value));
    }

    private static void validateEnvironmentName(String name) {
        if (!ENVIRONMENT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid environment variable name: " + name);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveDefaultAgent(Map<String, Object> root) {
        if (root == null || !(root.get("agents") instanceof Map<?, ?> rawAgents)
                || rawAgents.isEmpty()) {
            return null;
        }
        Map<Object, Object> agents = (Map<Object, Object>) rawAgents;
        Object defaultAgent = root.get("default_agent");
        if (defaultAgent == null) {
            defaultAgent = agents.keySet().iterator().next();
        }
        Object configured = agents.get(defaultAgent);
        return configured instanceof Map<?, ?> agent
                ? (Map<String, Object>) agent : null;
    }

    private static DevConfiguration loadConfiguration(
            File configFile, Map<String, String> commandLineEnvironment) {
        String entrypoint = resolveEntrypoint(configFile);
        if (entrypoint == null) {
            throw new IllegalArgumentException(
                    "No Java entrypoint is configured in " + configFile);
        }
        Map<String, String> environment = new LinkedHashMap<>(
                resolveConfiguredEnvironment(configFile));
        environment.putAll(commandLineEnvironment);
        return new DevConfiguration(entrypoint, Map.copyOf(environment), configFile.toPath());
    }

    static LoadedApp loadApp(String entrypointClassName, String projectPath) {
        Objects.requireNonNull(entrypointClassName, "entrypointClassName must not be null");
        ClassLoader loader = buildProjectClassLoader(projectPath);
        try {
            Class<?> type = Class.forName(entrypointClassName, true, loader);
            Method factory = type.getMethod("createApp");
            if (!Modifier.isStatic(factory.getModifiers())
                    || factory.getParameterCount() != 0
                    || !AgentArtsRuntimeApp.class.isAssignableFrom(factory.getReturnType())) {
                throw new IllegalArgumentException("Entrypoint " + entrypointClassName
                        + " must expose public static AgentArtsRuntimeApp createApp()");
            }
            AgentArtsRuntimeApp app = (AgentArtsRuntimeApp) factory.invoke(null);
            if (app == null) {
                throw new IllegalStateException(
                        "Entrypoint factory returned null: " + entrypointClassName);
            }
            return new LoadedApp(app, loader);
        } catch (ReflectiveOperationException e) {
            closeLoader(loader);
            throw new IllegalArgumentException("Failed to load entrypoint "
                    + entrypointClassName + ": " + rootMessage(e), e);
        } catch (RuntimeException e) {
            closeLoader(loader);
            throw e;
        }
    }

    static ClassLoader buildProjectClassLoader(String projectPath) {
        Path base = resolveProjectPath(projectPath);
        List<URL> urls = new ArrayList<>();
        Path classesDir = base.resolve("target/classes");
        if (Files.isDirectory(classesDir)) {
            urls.add(toUrl(classesDir));
        }
        Path depDir = base.resolve("target/dependency");
        if (Files.isDirectory(depDir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(depDir, "*.jar")) {
                for (Path jar : files) {
                    urls.add(toUrl(jar));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read project dependencies", e);
            }
        }
        if (urls.isEmpty()) {
            return DevOperation.class.getClassLoader();
        }
        return new URLClassLoader(urls.toArray(new URL[0]), DevOperation.class.getClassLoader());
    }

    private static void prepareProject(Path project) throws IOException {
        Path pom = project.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            if (!Files.isDirectory(project.resolve("target/classes"))) {
                throw new IllegalArgumentException(
                        "Project has neither pom.xml nor compiled target/classes: " + project);
            }
            return;
        }

        Path target = project.resolve("target");
        Files.createDirectories(target);
        Path classpathFile = target.resolve(DEV_CLASSPATH_FILE);
        Files.deleteIfExists(classpathFile);
        String maven = findMaven();
        List<String> command = List.of(
                maven, "-q", "-B", "-DskipTests", "compile",
                DEPENDENCY_CLASSPATH_GOAL, "-DincludeScope=runtime",
                "-Dmdep.outputFile=" + classpathFile.toAbsolutePath());
        System.out.println("Compiling development project...");
        int exitCode = ProcessRunner.run(
                command, project.toFile(), null, "mvn compile", BUILD_TIMEOUT);
        if (exitCode != 0 || !Files.isRegularFile(classpathFile)) {
            throw new IllegalStateException(
                    "Failed to compile development project (exit " + exitCode + ")");
        }
    }

    private static Process startChild(Path project, String host, int port,
                                      DevConfiguration configuration) throws IOException {
        Path launcherClasspath = buildLauncherClasspath(project);
        List<String> command = List.of(
                javaExecutable(), "-cp", launcherClasspath.toString(),
                DevServerLauncher.class.getName(), host, Integer.toString(port),
                configuration.entrypoint(), project.toString());
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(project.toFile())
                .inheritIO();
        builder.environment().putAll(configuration.environment());
        builder.environment().put("AGENTARTS_ENV", "development");
        builder.environment().put("AGENTARTS_CONFIG",
                configuration.configFile().toAbsolutePath().toString());
        System.out.println("Starting AgentArts dev server on " + host + ":" + port
                + " (entrypoint: " + configuration.entrypoint() + ", environment variables: "
                + configuration.environment().size() + ")");
        return builder.start();
    }

    static Path buildLauncherClasspath(Path project) throws IOException {
        Set<Path> entries = new LinkedHashSet<>();
        String currentClasspath = System.getProperty("java.class.path", "");
        for (String entry : currentClasspath.split(Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry).toAbsolutePath().normalize());
            }
        }
        entries.add(project.resolve("target/classes").toAbsolutePath().normalize());

        Path generatedClasspath = project.resolve("target").resolve(DEV_CLASSPATH_FILE);
        if (Files.isRegularFile(generatedClasspath)) {
            String value = Files.readString(generatedClasspath).trim();
            for (String entry : value.split(Pattern.quote(File.pathSeparator))) {
                if (!entry.isBlank()) {
                    entries.add(Path.of(entry).toAbsolutePath().normalize());
                }
            }
        } else {
            Path dependencies = project.resolve("target/dependency");
            if (Files.isDirectory(dependencies)) {
                try (var files = Files.list(dependencies)) {
                    files.filter(path -> path.getFileName().toString().endsWith(".jar"))
                            .sorted()
                            .forEach(path -> entries.add(path.toAbsolutePath().normalize()));
                }
            }
        }

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        String classPath = entries.stream()
                .filter(Files::exists)
                .map(path -> path.toUri().toASCIIString())
                .reduce((left, right) -> left + " " + right)
                .orElseThrow(() -> new IllegalStateException("Development classpath is empty"));
        attributes.put(Attributes.Name.CLASS_PATH, classPath);

        Path launcherJar = project.resolve("target").resolve(DEV_LAUNCHER_JAR);
        Files.createDirectories(launcherJar.getParent());
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(launcherJar), manifest)) {
            // The manifest-only JAR avoids platform command-line length limits.
        }
        return launcherJar;
    }

    static ProjectState snapshot(Path project, Path configFile) throws IOException {
        long sources = fingerprint(List.of(
                project.resolve("pom.xml"), project.resolve("src/main/java"),
                project.resolve("src/main/resources")));
        long artifacts = fingerprint(List.of(
                project.resolve("target/classes"), project.resolve("target/dependency"),
                project.resolve("target").resolve(DEV_CLASSPATH_FILE)));
        long config = fingerprint(List.of(configFile));
        return new ProjectState(sources, artifacts, config);
    }

    private static long fingerprint(List<Path> roots) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isRegularFile(root)) {
                files.add(root);
            } else if (Files.isDirectory(root)) {
                try (var paths = Files.walk(root)) {
                    paths.filter(Files::isRegularFile).forEach(files::add);
                }
            }
        }
        files.sort(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()));
        long result = 1_125_899_906_842_597L;
        for (Path file : files) {
            BasicFileAttributes attributes = Files.readAttributes(
                    file, BasicFileAttributes.class);
            result = 31 * result + file.toAbsolutePath().normalize().toString().hashCode();
            result = 31 * result + Long.hashCode(attributes.size());
            result = 31 * result + Long.hashCode(attributes.lastModifiedTime().toMillis());
        }
        return result;
    }

    private static String findMaven() {
        for (String candidate : Arrays.asList("mvn.cmd", "mvn")) {
            String executable = findOnPath(candidate);
            if (executable != null) {
                return executable;
            }
        }
        return "mvn";
    }

    private static String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String directory : path.split(Pattern.quote(File.pathSeparator))) {
            if (!directory.isBlank()) {
                File candidate = new File(directory, name);
                if (candidate.isFile()) {
                    return candidate.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    static void terminateTree(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                descendants.forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            descendants.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    /** Best-effort delete of the dev server's ephemeral launcher jar and classpath
     *  file, retrying briefly so the OS can release a lingering child-JVM file
     *  handle on Windows. */
    private static void deleteDevArtifacts(Path project) {
        Path target = project.resolve("target");
        List<Path> artifacts = List.of(
                target.resolve(DEV_LAUNCHER_JAR),
                target.resolve(DEV_CLASSPATH_FILE));
        for (Path artifact : artifacts) {
            deleteWithRetries(artifact);
        }
    }

    private static void deleteWithRetries(Path path) {
        for (int attempt = 0; attempt < ARTIFACT_DELETE_RETRIES; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException ignored) {
                // File still held open by a dying child JVM; retry shortly.
            }
            try {
                Thread.sleep(ARTIFACT_DELETE_RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static void closeLoader(ClassLoader loader) {
        if (loader instanceof URLClassLoader closeable) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Best effort during process shutdown or failed class loading.
            }
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toAbsolutePath().normalize().toUri().toURL();
        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException("Invalid classpath entry: " + path, e);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage()
                : current.getClass().getSimpleName();
    }

    record LoadedApp(AgentArtsRuntimeApp app, ClassLoader loader) {
    }

    record ProjectState(long sources, long artifacts, long config) {
    }

    private record DevConfiguration(
            String entrypoint, Map<String, String> environment, Path configFile) {
    }
}
