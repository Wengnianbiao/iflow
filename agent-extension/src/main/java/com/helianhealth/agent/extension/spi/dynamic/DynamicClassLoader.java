package com.helianhealth.agent.extension.spi.dynamic;

import lombok.extern.slf4j.Slf4j;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.SecureClassLoader;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated Dynamic ClassLoader
 * Each instance loads only one dynamic class, achieving one-to-one isolation between class and ClassLoader
 * Supports complete class unloading (when ClassLoader is GC'd, its loaded class will also be unloaded)
 */
@Slf4j
public class DynamicClassLoader extends ClassLoader {

    /**
     * Current loaded class by this ClassLoader (only one)
     */
    private Class<?> loadedClass;
    
    /**
     * Name of the class being loaded
     */
    private final String className;

    public DynamicClassLoader(ClassLoader parent, String className) {
        super(parent);
        this.className = className;
    }

    /**
     * Compile and load class from Java source code string
     * This ClassLoader instance will only load one class
     * 
     * @param javaCode Java source code string
     * @return Loaded Class object
     */
    public Class<?> compileAndLoad(String javaCode) throws Exception {
        if (loadedClass != null) {
            log.warn("Class already loaded in this ClassLoader: {}", className);
            return loadedClass;
        }

        log.info("Start compiling class: {}", className);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler not available, please ensure the application is running on JDK");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, null);

        ClassFileManager fileManager = new ClassFileManager(standardFileManager);

        JavaFileObject javaFileObject = new JavaSourceFromString(className, javaCode);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                null,
                null,
                Collections.singletonList(javaFileObject)
        );

        boolean success = task.call();
        if (!success) {
            StringBuilder errorMsg = new StringBuilder("Compilation failed:\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorMsg.append(String.format("Line %d: %s%n",
                        diagnostic.getLineNumber(),
                        diagnostic.getMessage(null)));
            }
            throw new RuntimeException(errorMsg.toString());
        }

        // Use internal IsolatedClassLoader to load class
        loadedClass = fileManager.getIsolatedClassLoader().loadClass(className);
        log.info("Successfully compiled and loaded class: {} by ClassLoader: {}", 
                className, Integer.toHexString(this.hashCode()));
        return loadedClass;
    }
    
    /**
     * Load pre-compiled class from parent ClassLoader (project mode)
     * Suitable for scenarios where .java files are written directly in the project
     * 
     * @return Loaded Class object
     */
    public Class<?> loadFromClasspath() throws Exception {
        if (loadedClass != null) {
            log.warn("Class already loaded in this ClassLoader: {}", className);
            return loadedClass;
        }
        
        log.info("Loading class from classpath: {}", className);
        
        // Use parent ClassLoader to load class
        loadedClass = Class.forName(className, true, getParent());
        
        log.info("Successfully loaded class from classpath: {} by ClassLoader: {}", 
                className, Integer.toHexString(this.hashCode()));
        return loadedClass;
    }
    
    /**
     * Get the loaded class
     */
    public Class<?> getLoadedClass() {
        return loadedClass;
    }
    
    /**
     * Clear references to help GC
     */
    public void unload() {
        if (loadedClass != null) {
            log.info("Unloading class: {} from ClassLoader: {}", 
                    className, Integer.toHexString(this.hashCode()));
            loadedClass = null;
        }
    }

    private static class JavaClassObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        protected JavaClassObject(String name, Kind kind) {
            super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    private static class JavaSourceFromString extends SimpleJavaFileObject {
        private final String code;

        JavaSourceFromString(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static class ClassFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, JavaClassObject> classObjectMap = new ConcurrentHashMap<>();
        private IsolatedClassLoader isolatedClassLoader;

        protected ClassFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                    JavaFileObject.Kind kind, FileObject sibling) {
            JavaClassObject classObject = new JavaClassObject(className, kind);
            classObjectMap.put(className, classObject);
            return classObject;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            return getIsolatedClassLoader();
        }
        
        /**
         * Get isolated ClassLoader instance
         */
        public IsolatedClassLoader getIsolatedClassLoader() {
            if (isolatedClassLoader == null) {
                isolatedClassLoader = new IsolatedClassLoader(classObjectMap);
            }
            return isolatedClassLoader;
        }
    }
    
    /**
     * Isolated ClassLoader - each instance loads classes independently
     * When this ClassLoader is GC'd, its loaded classes will also be unloaded
     */
    private static class IsolatedClassLoader extends SecureClassLoader {
        private final Map<String, JavaClassObject> classObjectMap;
        
        IsolatedClassLoader(Map<String, JavaClassObject> classObjectMap) {
            super(Thread.currentThread().getContextClassLoader()); // Use application ClassLoader as parent
            this.classObjectMap = classObjectMap;
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            JavaClassObject classObject = classObjectMap.get(name);
            if (classObject != null) {
                byte[] bytes = classObject.getBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }

    private static class ForwardingJavaFileManager<M extends JavaFileManager> implements JavaFileManager {
        protected final M fileManager;

        protected ForwardingJavaFileManager(M fileManager) {
            this.fileManager = fileManager;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            return fileManager.getClassLoader(location);
        }

        @Override
        public Iterable<JavaFileObject> list(Location location, String packageName,
                                             java.util.Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
            return fileManager.list(location, packageName, kinds, recurse);
        }

        @Override
        public String inferBinaryName(Location location, JavaFileObject file) {
            return fileManager.inferBinaryName(location, file);
        }

        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            return fileManager.isSameFile(a, b);
        }

        @Override
        public boolean handleOption(String current, java.util.Iterator<String> remaining) {
            return fileManager.handleOption(current, remaining);
        }

        @Override
        public boolean hasLocation(Location location) {
            return fileManager.hasLocation(location);
        }

        @Override
        public JavaFileObject getJavaFileForInput(Location location, String className,
                                                   JavaFileObject.Kind kind) throws IOException {
            return fileManager.getJavaFileForInput(location, className, kind);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                    JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            return fileManager.getJavaFileForOutput(location, className, kind, sibling);
        }

        @Override
        public FileObject getFileForInput(Location location, String packageName,
                                          String relativeName) throws IOException {
            return fileManager.getFileForInput(location, packageName, relativeName);
        }

        @Override
        public FileObject getFileForOutput(Location location, String packageName,
                                           String relativeName, FileObject sibling) throws IOException {
            return fileManager.getFileForOutput(location, packageName, relativeName, sibling);
        }

        @Override
        public void flush() throws IOException {
            fileManager.flush();
        }

        @Override
        public void close() throws IOException {
            fileManager.close();
        }

        @Override
        public int isSupportedOption(String option) {
            return fileManager.isSupportedOption(option);
        }
    }
}
