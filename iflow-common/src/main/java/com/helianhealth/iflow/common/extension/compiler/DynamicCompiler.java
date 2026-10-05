package com.helianhealth.iflow.common.extension.compiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态Java代码编译器（单例）
 * 支持在运行时动态编译Java源代码字符串并加载生成的类
 * 复用编译器实例，每次编译创建独立的ClassLoader支持热替换
 */
public class DynamicCompiler {
    private static final Logger log = LoggerFactory.getLogger(DynamicCompiler.class);

    private static volatile DynamicCompiler INSTANCE;

    private final JavaCompiler compiler;
    private final StandardJavaFileManager standardFileManager;
    private final List<String> options;
    private static final String FAT_JAR_CACHE_ROOT = "dynamic-compiler-fatjar-cache";
    private static final String CACHE_CLASSPATH_INDEX = "classpath.idx";
    private static final String CACHE_READY_MARKER = ".ready";
    private static final Map<String, Set<String>> FAT_JAR_CLASSPATH_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取单例实例
     */
    public static DynamicCompiler getInstance() {
        if (INSTANCE == null) {
            synchronized (DynamicCompiler.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DynamicCompiler();
                }
            }
        }
        return INSTANCE;
    }

    private DynamicCompiler() {
        // iFlow 的编译基线是 Java 17。使用 --release 同时约束语言特性、JDK API 与字节码版本，
        // 即使应用由更高版本 JDK 启动，在线扩展仍保持 Java 17 兼容。
        this(Arrays.asList("--release", "17", "-proc:none", "-parameters"));

    }

    private DynamicCompiler(List<String> compileOptions) {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException("无法获取系统Java编译器，请确保使用JDK而非JRE运行");
        }

        this.options = new ArrayList<>(compileOptions);
        this.standardFileManager = compiler.getStandardFileManager(null, null, null);

        // 设置ClassPath，包含当前类加载器的所有路径
        initClassPath();
    }

    /**
     * 初始化编译类路径
     * 支持开发环境和Spring Boot Fat Jar环境
     */
    private void initClassPath() {
        Set<String> classpathEntries = new LinkedHashSet<>();
        log.info("DynamicCompiler init start");
        log.info("DynamicCompiler code source: {}", getCodeSourceLocation());

        // 1. 从 java.class.path 获取
        String javaClassPath = System.getProperty("java.class.path");
        if (javaClassPath != null && !javaClassPath.isEmpty()) {
            for (String path : javaClassPath.split(File.pathSeparator)) {
                if (new File(path).exists()) {
                    classpathEntries.add(path);
                }
            }
        }

        // 2. 从ClassLoader获取URLs
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        boolean isFatJar = false;
        String mainJarPath = null;

        try {
            URL[] urls = getClassLoaderUrls(loader);
            if (urls != null) {
                for (URL url : urls) {
                    String urlStr = url.toString();
                    // 检测 Spring Boot fat jar 环境
                    if (urlStr.contains("!/BOOT-INF/")) {
                        isFatJar = true;
                        mainJarPath = extractMainJarPath(url);
                    } else if (urlStr.contains("!/")) {
                        // 某些环境 URL 中不会直接出现 BOOT-INF，先提取外层 jar，再做结构探测
                        String candidateJar = extractMainJarPath(url);
                        if (candidateJar != null && isSpringBootFatJar(new File(candidateJar))) {
                            isFatJar = true;
                            mainJarPath = candidateJar;
                        }
                    } else {
                        String path = extractPath(url);
                        if (path != null && new File(path).exists()) {
                            classpathEntries.add(path);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }

        // 2.1 fallback: 从 java.class.path / CodeSource 探测 Fat Jar
        if (!isFatJar || mainJarPath == null) {
            String detectedJar = detectFatJarMainPath(javaClassPath);
            if (detectedJar != null) {
                isFatJar = true;
                mainJarPath = detectedJar;
            }
        }
        log.info("DynamicCompiler fat-jar detect result: isFatJar={}, mainJarPath={}", isFatJar, mainJarPath);

        // 3. Fat Jar 环境：解压依赖到临时目录
        if (isFatJar && mainJarPath != null) {
            try {
                Set<String> extractedPaths = resolveFatJarClassPath(mainJarPath);
                classpathEntries.addAll(extractedPaths);
                log.info("DynamicCompiler extracted classpath entries from fat jar: {}", extractedPaths.size());
            } catch (Exception e) {
                throw new IllegalStateException("解析Fat Jar编译路径失败: " + mainJarPath, e);
            }
        }

        // 4. 设置编译器类路径
        if (!classpathEntries.isEmpty()) {
            try {
                List<File> files = new ArrayList<>();
                for (String entry : classpathEntries) {
                    files.add(new File(entry));
                }
                standardFileManager.setLocation(StandardLocation.CLASS_PATH, files);
                log.info("DynamicCompiler classpath initialized. totalEntries={}", files.size());
                if (log.isDebugEnabled()) {
                    int i = 0;
                    for (File file : files) {
                        if (i++ >= 20) {
                            log.debug("DynamicCompiler classpath ... (truncated)");
                            break;
                        }
                        log.debug("DynamicCompiler classpath[{}]={}", i, file.getAbsolutePath());
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("设置ClassPath失败: " + e.getMessage(), e);
            }
        } else {
            log.warn("DynamicCompiler classpath is empty after initialization");
        }
    }

    /**
     * 解析 Fat Jar 编译类路径（带缓存）
     * 首次提取后会复用缓存目录，避免每次动态编译都复制一份依赖
     */
    private Set<String> resolveFatJarClassPath(String jarPath) throws Exception {
        File mainJar = new File(jarPath).getCanonicalFile();
        if (!mainJar.exists()) {
            log.warn("DynamicCompiler fat-jar main file does not exist: {}", jarPath);
            return Collections.emptySet();
        }

        String jarIdentity = mainJar.getAbsolutePath() + "|" + mainJar.length() + "|" + mainJar.lastModified();
        log.info("DynamicCompiler resolve fat-jar classpath: {}", mainJar.getAbsolutePath());
        return FAT_JAR_CLASSPATH_CACHE.computeIfAbsent(jarIdentity, k -> {
            try {
                return extractFatJarDependencies(mainJar.getAbsolutePath(), jarIdentity);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 提取主jar路径
     */
    private String extractMainJarPath(URL url) {
        String path = url.toString();
        // jar:file:/path/app.jar!/BOOT-INF/...
        if (path.startsWith("jar:file:")) {
            path = path.substring(9);
        }
        int idx = path.indexOf("!/");
        if (idx > 0) {
            path = path.substring(0, idx);
        }
        return decodeUrlPath(path);
    }

    /**
     * 解压Fat Jar的依赖到临时目录
     */
    private Set<String> extractFatJarDependencies(String jarPath, String jarIdentity) throws Exception {
        Set<String> paths = new LinkedHashSet<>();

        File cacheRoot = new File(System.getProperty("java.io.tmpdir"), FAT_JAR_CACHE_ROOT);
        if (!cacheRoot.exists()) {
            cacheRoot.mkdirs();
        }

        String cacheKey = sha256(jarIdentity);
        File cacheDir = new File(cacheRoot, cacheKey);
        File readyMarker = new File(cacheDir, CACHE_READY_MARKER);
        File classpathIndex = new File(cacheDir, CACHE_CLASSPATH_INDEX);

        if (readyMarker.exists() && classpathIndex.exists()) {
            Set<String> cached = readClasspathIndex(classpathIndex.toPath());
            if (!cached.isEmpty() && allPathsExist(cached)) {
                log.info("DynamicCompiler fat-jar classpath cache hit: key={}, entries={}", cacheKey, cached.size());
                return cached;
            }
            log.warn("DynamicCompiler fat-jar cache invalid, rebuilding. key={}", cacheKey);
        }

        if (cacheDir.exists()) {
            deleteDirectory(cacheDir.toPath());
        }
        cacheDir.mkdirs();

        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // 解压 BOOT-INF/lib/*.jar
                if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
                    File outFile = new File(cacheDir, "lib/" + name.substring("BOOT-INF/lib/".length()));
                    extractEntry(jar, entry, outFile);
                    paths.add(outFile.getAbsolutePath());
                }
                // 解压 BOOT-INF/classes/
                else if (name.startsWith("BOOT-INF/classes/") && !entry.isDirectory()) {
                    String relativePath = name.substring("BOOT-INF/classes/".length());
                    File outFile = new File(cacheDir, "classes/" + relativePath);
                    extractEntry(jar, entry, outFile);
                }
            }

            // 添加 classes 目录
            File classesDir = new File(cacheDir, "classes");
            if (classesDir.exists()) {
                paths.add(classesDir.getAbsolutePath());
            }
        }

        writeClasspathIndex(classpathIndex.toPath(), paths);
        Files.write(readyMarker.toPath(), Collections.singletonList("ok"), StandardCharsets.UTF_8);
        log.info("DynamicCompiler fat-jar classpath cache built: key={}, entries={}, cacheDir={}",
            cacheKey, paths.size(), cacheDir.getAbsolutePath());

        return paths;
    }

    /**
     * 解压jar条目
     */
    private void extractEntry(java.util.jar.JarFile jar, java.util.jar.JarEntry entry, File outFile) throws Exception {
        outFile.getParentFile().mkdirs();
        try (java.io.InputStream in = jar.getInputStream(entry);
             java.io.OutputStream out = new java.io.FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    /**
     * 获取ClassLoader的URLs
     */
    private URL[] getClassLoaderUrls(ClassLoader loader) {
        if (loader instanceof URLClassLoader) {
            return ((URLClassLoader) loader).getURLs();
        }

        // Spring Boot LaunchedURLClassLoader
        try {
            java.lang.reflect.Method method = loader.getClass().getMethod("getURLs");
            method.setAccessible(true);
            return (URL[]) method.invoke(loader);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从URL提取文件路径
     * 支持普通URL和Spring Boot嵌套jar URL
     */
    private String extractPath(URL url) {
        if (url == null) {
            return null;
        }

        String protocol = url.getProtocol();
        String path = url.getPath();

        // file:/path/to/file.jar
        if ("file".equals(protocol)) {
            try {
                return new File(url.toURI()).getAbsolutePath();
            } catch (Exception e) {
                return decodeUrlPath(path);
            }
        }

        // jar:file:/path/to/app.jar!/BOOT-INF/lib/xxx.jar!/
        if ("jar".equals(protocol)) {
            // 提取最外层jar的路径
            if (path.startsWith("file:")) {
                path = path.substring(5);
            }
            int jarSeparator = path.indexOf("!/");
            if (jarSeparator > 0) {
                path = path.substring(0, jarSeparator);
            }
            return decodeUrlPath(path);
        }

        return null;
    }

    /**
     * 解码URL路径中的特殊字符
     */
    private String decodeUrlPath(String path) {
        try {
            return java.net.URLDecoder.decode(path, "UTF-8");
        } catch (Exception e) {
            return path;
        }
    }

    /**
     * 从 java.class.path 或当前类 CodeSource 探测 Spring Boot Fat Jar 主包路径
     */
    private String detectFatJarMainPath(String javaClassPath) {
        // 优先从 java.class.path 探测
        if (javaClassPath != null && !javaClassPath.isEmpty()) {
            for (String cp : javaClassPath.split(File.pathSeparator)) {
                File f = new File(cp);
                if (isSpringBootFatJar(f)) {
                    try {
                        return f.getCanonicalPath();
                    } catch (IOException e) {
                        return f.getAbsolutePath();
                    }
                }
            }
        }

        // 再从当前类的 CodeSource 探测
        try {
            URL location = DynamicCompiler.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                File f = new File(location.toURI());
                if (isSpringBootFatJar(f)) {
                    return f.getCanonicalPath();
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    /**
     * 判断一个 jar 是否是 Spring Boot Fat Jar
     */
    private boolean isSpringBootFatJar(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar")) {
            return false;
        }

        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
            if (jar.getEntry("BOOT-INF/classes/") != null) {
                return true;
            }
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                if (entryName.startsWith("BOOT-INF/lib/") && entryName.endsWith(".jar")) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    private String getCodeSourceLocation() {
        try {
            URL location = DynamicCompiler.class.getProtectionDomain().getCodeSource().getLocation();
            return location == null ? "unknown" : location.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Set<String> readClasspathIndex(Path indexPath) throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(indexPath.toFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    paths.add(trimmed);
                }
            }
        }
        return paths;
    }

    private void writeClasspathIndex(Path indexPath, Set<String> paths) throws IOException {
        if (indexPath.getParent() != null) {
            Files.createDirectories(indexPath.getParent());
        }
        Files.write(indexPath, paths, StandardCharsets.UTF_8);
    }

    private boolean allPathsExist(Set<String> paths) {
        for (String path : paths) {
            if (!new File(path).exists()) {
                return false;
            }
        }
        return true;
    }

    private String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        Files.walk(directory)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException("删除缓存目录失败: " + path, e);
                }
            });
    }

    /**
     * 预处理Lombok注解，手动展开@Slf4j等注解
     * 因为动态编译禁用了注解处理器(-proc:none)，需要手动展开
     */
    private String preprocessLombok(String fullClassName, String sourceCode) {
        // 获取简单类名
        String simpleClassName = fullClassName.contains(".")
            ? fullClassName.substring(fullClassName.lastIndexOf('.') + 1)
            : fullClassName;

        // 处理 @Slf4j
        if (sourceCode.contains("@Slf4j")) {
            // 移除 import lombok.extern.slf4j.Slf4j;
            sourceCode = sourceCode.replaceAll(
                "import\\s+lombok\\.extern\\.slf4j\\.Slf4j\\s*;\\s*\n?",
                "");

            // 移除 @Slf4j 注解
            sourceCode = sourceCode.replaceAll(
                "@Slf4j\\s*\n?",
                "");

            // 添加 Logger 声明（在类头部）
            String loggerDeclaration = "private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("
                + simpleClassName + ".class);\n    ";

            // 在类体开始处插入 Logger
            sourceCode = sourceCode.replaceFirst(
                "(class\\s+" + simpleClassName + "[^{]*\\{)",
                "$1\n    " + loggerDeclaration);
        }

        // 处理 @Log4j2
        if (sourceCode.contains("@Log4j2")) {
            sourceCode = sourceCode.replaceAll(
                "import\\s+lombok\\.extern\\.log4j\\.Log4j2\\s*;\\s*\n?",
                "");
            sourceCode = sourceCode.replaceAll(
                "@Log4j2\\s*\n?",
                "");
            String loggerDeclaration = "private static final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger("
                + simpleClassName + ".class);\n    ";
            sourceCode = sourceCode.replaceFirst(
                "(class\\s+" + simpleClassName + "[^{]*\\{)",
                "$1\n    " + loggerDeclaration);
        }

        return sourceCode;
    }

    /**
     * 编译Java源代码并返回编译结果
     *
     * @param fullClassName 完全限定类名（包含包名）
     * @param sourceCode Java源代码字符串
     * @return 编译结果，包含Class和对应的ClassLoader
     * @throws Exception 编译失败时抛出异常
     */
    public CompileResult compile(String fullClassName, String sourceCode) throws Exception {
        // 预处理源码：展开Lombok注解
        sourceCode = preprocessLombok(fullClassName, sourceCode);

        // 每次编译创建独立的ClassLoader，支持类的热替换
        DynamicClassLoader classLoader = new DynamicClassLoader(
            Thread.currentThread().getContextClassLoader());
        DynamicFileManager fileManager = new DynamicFileManager(standardFileManager, classLoader);
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();

        // 创建Java文件对象
        JavaFileObject javaFileObject = new JavaSourceFromString(fullClassName, sourceCode);

        // 执行编译
        JavaCompiler.CompilationTask task = compiler.getTask(
            null,
            fileManager,
            diagnosticCollector,
            options,
            null,
            Collections.singletonList(javaFileObject)
        );

        Boolean result = task.call();

        if (result == null || !result) {
            StringBuilder errorMsg = new StringBuilder("编译失败：\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticCollector.getDiagnostics()) {
                errorMsg.append(String.format("第%d行: %s\n",
                    diagnostic.getLineNumber(),
                    diagnostic.getMessage(Locale.CHINESE)));
            }
            throw new IllegalStateException(errorMsg.toString());
        }

        Class<?> clazz = classLoader.loadClass(fullClassName);
        return new CompileResult(clazz, classLoader);
    }

    /**
     * 编译结果，包含编译后的Class和对应的ClassLoader
     */
    public static class CompileResult {
        private final Class<?> clazz;
        private final DynamicClassLoader classLoader;

        public CompileResult(Class<?> clazz, DynamicClassLoader classLoader) {
            this.clazz = clazz;
            this.classLoader = classLoader;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public DynamicClassLoader getClassLoader() {
            return classLoader;
        }
    }

    /**
     * 表示内存中的Java源代码文件
     */
    private static class JavaSourceFromString extends SimpleJavaFileObject {
        private final String code;

        public JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),
                  Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /**
     * 表示内存中的字节码文件
     */
    private static class JavaClassInMemory extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        public JavaClassInMemory(String name, Kind kind) {
            super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        public byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    /**
     * 自定义文件管理器，用于管理编译过程中的文件对象
     */
    private static class DynamicFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final DynamicClassLoader classLoader;

        protected DynamicFileManager(StandardJavaFileManager fileManager, DynamicClassLoader classLoader) {
            super(fileManager);
            this.classLoader = classLoader;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            JavaClassInMemory classInMemory = new JavaClassInMemory(className, kind);
            classLoader.addClass(className, classInMemory);
            return classInMemory;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            return classLoader;
        }
    }

    /**
     * 自定义类加载器，用于加载动态编译的类
     * 支持卸载以便类的热替换
     */
    public static class DynamicClassLoader extends ClassLoader {
        private final Map<String, JavaClassInMemory> classFiles = new HashMap<>();
        private volatile boolean unloaded = false;

        public DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }

        void addClass(String name, JavaClassInMemory classFile) {
            classFiles.put(name, classFile);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    // 对动态编译产生的类采用 child-first，避免被父加载器中的同名旧类遮蔽
                    JavaClassInMemory dynamicClass = classFiles.get(name);
                    if (dynamicClass != null) {
                        byte[] bytes = dynamicClass.getBytes();
                        loadedClass = defineClass(name, bytes, 0, bytes.length);
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (unloaded) {
                throw new ClassNotFoundException("ClassLoader has been unloaded: " + name);
            }

            JavaClassInMemory classFile = classFiles.get(name);
            if (classFile != null) {
                byte[] bytes = classFile.getBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }

        /**
         * 卸载类加载器，释放资源
         */
        public void unload() {
            unloaded = true;
            classFiles.clear();
        }
    }
}
