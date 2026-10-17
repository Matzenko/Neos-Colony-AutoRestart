package dev.neoscolony.autorestart.restart;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;

import java.util.*;
import java.util.concurrent.TimeUnit;
/** Windows replay core derived from the successfully tested local prototype. */
public final class RestartEngine {
    public static final String MARKER="neos.colony.autorestart.lastUpdate";
    public static Prepared prepare(String fingerprint, Path expectedGame) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows") || !System.getProperty("os.arch").equals("amd64"))
            throw new IOException("WINDOWS_X64_REQUIRED");
        Class<?> jna=Class.forName("com.sun.jna.Native");
        NativeWin win=new NativeWin(jna.getClassLoader());
        Context c=win.capture();
        c.jnaJar=resolveJnaJar(jna).toString();
        c.parentPid=ProcessHandle.current().pid();
        c.parentStart=ProcessHandle.current().info().startInstant().orElseThrow().toString();
        // Always prefer verified structured effective launcher arguments, never split sun.java.command.
        win.recoverLauncherArguments(c);
        validateContext(c);
        if(!Files.isSameFile(Path.of(c.gameDir),expectedGame))throw new IOException("GAME_DIRECTORY_MISMATCH");
        c.command=withMarker(c.command,fingerprint);
        Path nativeDll=Path.of(System.getProperty("jnidispatch.path","")).toRealPath();
        if(!Files.isRegularFile(nativeDll)||!nativeDll.getFileName().toString().endsWith(".dll"))
            throw new IOException("EXISTING_JNA_NATIVE_REQUIRED");
        c.files.put(nativeDll.toString(),digest(nativeDll));
        c.files.put(c.jnaJar,digest(Path.of(c.jnaJar)));
        Path ownJar=physicalJnaPath(Path.of(RestartEngine.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        c.files.put(ownJar.toString(),digest(ownJar));
        return startHelper(c,ownJar,nativeDll);
    }
    static String withMarker(String command,String fingerprint)throws Exception{
        if(!fingerprint.matches("[a-f0-9]{64}"))throw new IOException("INVALID_UPDATE_FINGERPRINT");
        List<String> args=new ArrayList<>(windowsArgs(command));
        args.removeIf(a->a.startsWith("-D"+MARKER+"="));
        args.add(1,"-D"+MARKER+"="+fingerprint);
        return windowsCommand(args);
    }
    static Prepared startHelper(Context c,Path ownJar,Path nativeDll)throws Exception{
        String name=nativeDll.getFileName().toString();
        ProcessBuilder pb=new ProcessBuilder(c.executable,"-XX:-UsePerfData","-Djna.nounpack=true","-Djna.noclasspath=true",
            "-Djna.boot.library.path="+nativeDll.getParent(),"-Djna.boot.library.name="+name.substring(0,name.length()-4),
            "-cp",ownJar.toString(),RestartEngine.class.getName(),"helper");
        pb.directory(Path.of(c.cwd).toFile());
        for(String key:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","CLASSPATH"))pb.environment().remove(key);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p=pb.start();
        Prepared result=null;
        try{
            ObjectOutputStream out=new ObjectOutputStream(p.getOutputStream());
            out.writeObject(c);out.flush();
            result=new Prepared(p,out,new DataInputStream(p.getInputStream()));
            result.expect("READY");
            return result;
        }catch(Exception e){p.destroyForcibly();throw e;}
        finally{Arrays.fill(c.environment,(byte)0);c.command=null;}
    }
    public static final class Prepared implements AutoCloseable{
        private final Process process;private final ObjectOutputStream out;private final DataInputStream in;
        private boolean committed,closed;
        Prepared(Process p,ObjectOutputStream o,DataInputStream i){process=p;out=o;in=i;}
        void expect(String expected)throws Exception{
            var task=new java.util.concurrent.FutureTask<String>(()->in.readUTF());
            Thread t=new Thread(task,"NeosRestart-handshake");t.setDaemon(true);t.start();
            if(!expected.equals(task.get(15,TimeUnit.SECONDS)))throw new IOException("HELPER_PROTOCOL");
        }
        public synchronized void commit()throws Exception{
            if(closed||committed)throw new IOException("HELPER_ALREADY_USED");
            try{out.writeUTF("COMMIT");out.flush();expect("COMMITTED");committed=true;}
            catch(Exception e){close();throw e;}
        }
        public synchronized void abort(){process.destroyForcibly();close();}
        public synchronized void close(){
            if(closed)return;closed=true;
            if(!committed)process.destroyForcibly();
            try{out.close();}catch(Exception ignored){}
            try{in.close();}catch(Exception ignored){}
        }
    }
    public static void main(String[] args){
        if(args.length!=1||!args[0].equals("helper"))return;
        try{helper();}catch(Throwable ignored){System.exit(2);}
    }
    static void helper()throws Exception{
        Context c=null;
        try(ObjectInputStream in=new ObjectInputStream(System.in)){
            in.setObjectInputFilter(info->{
                if(info.depth()>12||info.references()>10000||info.streamBytes()>16*1024*1024)
                    return ObjectInputFilter.Status.REJECTED;
                Class<?> t=info.serialClass();
                if(t!=null && !(t==Context.class||t==String.class||t==LinkedHashMap.class||t==HashMap.class||
                    t==ArrayList.class||t==byte[].class||t==Object[].class||
                    (t.isArray()&&t.getComponentType()==Map.Entry.class)))
                    return ObjectInputFilter.Status.REJECTED;
                return ObjectInputFilter.Status.UNDECIDED;
            });
            c=(Context)in.readObject();
            validateFiles(c);
            try(URLClassLoader loader=new URLClassLoader(new URL[]{Path.of(c.jnaJar).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
                NativeWin win=new NativeWin(loader);
                ProcessHandle parent=ProcessHandle.of(c.parentPid).orElseThrow();
                if(!parent.info().startInstant().orElseThrow().toString().equals(c.parentStart))throw new IOException("PARENT_IDENTITY");
                DataOutputStream out=new DataOutputStream(System.out);
                out.writeUTF("READY");out.flush();
                // No launch on EOF, cancellation, failed handshake or ordinary unarmed game exit.
                if(!in.readUTF().equals("COMMIT"))return;
                out.writeUTF("COMMITTED");out.flush();
                long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
                while(parent.isAlive()){
                    if(System.nanoTime()>deadline)return;
                    Thread.sleep(100);
                }
                validateFiles(c);
                win.launch(c); // exactly one attempt, no retry path
            }
        }finally{if(c!=null){Arrays.fill(c.environment,(byte)0);c.command=null;}}
    }
    static String quoteWindowsArgument(String value) throws IOException {
        if (value.indexOf('\0') >= 0) throw new IOException("INVALID_ARGUMENT_NUL");
        StringBuilder out = new StringBuilder("\"");
        int slashes = 0;
        for (int i=0; i<value.length(); i++) {
            char ch=value.charAt(i);
            if (ch=='\\') { slashes++; continue; }
            if (ch=='"') out.append("\\".repeat(slashes*2+1)).append('"');
            else out.append("\\".repeat(slashes)).append(ch);
            slashes=0;
        }
        return out.append("\\".repeat(slashes*2)).append('"').toString();
    }
    static String windowsCommand(List<String> args) throws Exception {
        if (args.isEmpty()) throw new IOException("EMPTY_ARGUMENT_VECTOR");
        List<String> quoted=new ArrayList<>();
        for (String arg:args) quoted.add(quoteWindowsArgument(arg));
        String result=String.join(" ",quoted);
        if (result.length() >= 32767) throw new IOException("WINDOWS_COMMAND_TOO_LONG");
        if (!windowsArgs(result).equals(args)) throw new IOException("ARGUMENT_ROUNDTRIP_FAILED");
        return result;
    }
    static boolean hasGameArgument(String command) throws Exception {
        return windowsArgs(command).stream().anyMatch(a->a.equals("--gameDir")||a.startsWith("--gameDir="));
    }
    static void applyLauncherArguments(Context ctx,List<String> args,int appIndex,String javaCommand) throws Exception {
        if (appIndex<2||appIndex>args.size()) throw new IOException("JLI_APP_INDEX_INVALID");
        if (!String.join(" ",args.subList(appIndex-1,args.size())).equals(javaCommand))
            throw new IOException("JLI_APPLICATION_MISMATCH");
        if (args.stream().anyMatch(s->s.startsWith("@"))) throw new IOException("JLI_LITERAL_AT_UNSUPPORTED");
        if (!Files.isSameFile(Path.of(args.get(0)),Path.of(ctx.executable))) throw new IOException("JLI_EXECUTABLE_MISMATCH");
        ctx.command=windowsCommand(args);
        ctx.argumentSource="JLI_EXPANDED_ARGV";
        ctx.launcherArgumentCount=args.size();
        ctx.applicationArgumentsVerified=true;
    }


    /** Resolve a loader's virtual view via its filesystem API, never by chopping URI text. */
    static Path resolveJnaJar(Class<?> nativeClass) throws Exception {
        Path location = Path.of(nativeClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path physical = physicalJnaPath(location);
        auditJna(physical);
        // The selected file must contain exactly the class resource exposed by this loader.
        try (var archive = new java.util.jar.JarFile(physical.toFile());
             InputStream fromJar = archive.getInputStream(archive.getJarEntry("com/sun/jna/Native.class"));
             InputStream loadedResource = nativeClass.getResourceAsStream("Native.class")) {
            if (loadedResource == null || !MessageDigest.isEqual(
                    MessageDigest.getInstance("SHA-256").digest(fromJar.readAllBytes()),
                    MessageDigest.getInstance("SHA-256").digest(loadedResource.readAllBytes())))
                throw new IOException("JNA_CLASS_RESOURCE_MISMATCH");
        }
        return physical.toRealPath();
    }

    static Path physicalJnaPath(Path location) throws Exception {
        Path physical = location;
        if ("union".equalsIgnoreCase(location.getFileSystem().provider().getScheme())) {
            Object primary = location.getFileSystem().getClass().getMethod("getPrimaryPath").invoke(location.getFileSystem());
            if (!(primary instanceof Path)) throw new IOException("JNA_PRIMARY_PATH_UNAVAILABLE");
            physical = (Path) primary;
        }
        if (!"file".equalsIgnoreCase(physical.getFileSystem().provider().getScheme()) || !Files.isRegularFile(physical))
            throw new IOException("JNA_PHYSICAL_JAR_UNAVAILABLE");
        return physical.toRealPath();
    }

    /** Read every entry and check its uncompressed size and CRC, rather than only its suffix. */
    static int auditJna(Path file) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) < 4) throw new IOException("JNA_INVALID_ARCHIVE");
        try (InputStream in = Files.newInputStream(file)) {
            if (!Arrays.equals(in.readNBytes(4), new byte[]{'P','K',3,4})) throw new IOException("JNA_INVALID_ARCHIVE");
        }
        int count = 0;
        try (var archive = new java.util.jar.JarFile(file.toFile())) {
            for (String name : List.of("com/sun/jna/Native.class","com/sun/jna/NativeLibrary.class",
                    "com/sun/jna/Pointer.class","com/sun/jna/Memory.class","com/sun/jna/Function.class"))
                if (archive.getJarEntry(name) == null) throw new IOException("JNA_REQUIRED_CLASS_MISSING");
            Set<String> names = new HashSet<>();
            byte[] buffer = new byte[16384];
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!names.add(entry.getName())) throw new IOException("JNA_DUPLICATE_ENTRY");
                if (entry.isDirectory()) continue;
                var crc = new java.util.zip.CRC32(); long size = 0;
                try (InputStream in = archive.getInputStream(entry)) {
                    int n;
                    while ((n = in.read(buffer)) != -1) { crc.update(buffer,0,n); size += n; }
                }
                if (size != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("JNA_ARCHIVE_CRC_MISMATCH");
                count++;
            }
        }
        return count;
    }


    static final class Context implements Serializable {
        private static final long serialVersionUID = 1L;
        String executable, command, cwd, gameDir, jnaJar, parentStart;
        byte[] environment;
        long parentPid;
        LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
        ArrayList<String> directories = new ArrayList<>();
        ArrayList<String> argumentFiles = new ArrayList<>();
        String argumentSource = "NATIVE_COMMAND_LINE", gameDirectorySource = "START_ARGUMENTS";
        int nativeArgumentCount, launcherArgumentCount;
        boolean applicationArgumentsVerified;
    }
    static byte[] digest(Path p) throws Exception {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
            return md.digest();
        }
    }
    static void validateFiles(Context c) throws Exception {
        if (!Files.isRegularFile(Path.of(c.executable)) || !Files.isDirectory(Path.of(c.cwd)))
            throw new IOException("MISSING_EXECUTABLE_OR_CWD");
        for (String dir : c.directories) if (!Files.isDirectory(Path.of(dir))) throw new IOException("START_DIRECTORY_MISSING");
        for (var file : c.files.entrySet())
            if (!Files.isRegularFile(Path.of(file.getKey())) || !MessageDigest.isEqual(file.getValue(), digest(Path.of(file.getKey()))))
                throw new IOException("START_FILE_MISSING_OR_CHANGED");
    }
    static void validateContext(Context c) throws Exception {
        String env = new String(c.environment, StandardCharsets.UTF_16LE);
        for (String entry : env.split("\u0000")) {
            for (String key : List.of("JAVA_TOOL_OPTIONS=", "JDK_JAVA_OPTIONS=", "_JAVA_OPTIONS="))
                if (entry.regionMatches(true, 0, key, 0, key.length()) && !entry.substring(key.length()).isBlank())
                    throw new IOException("ENVIRONMENT_JAVA_INJECTION_UNSUPPORTED");
        }
        List<String> args = windowsArgs(c.command);
        if (args.size() < 2) throw new IOException("INCOMPLETE_COMMAND");
        List<String> expanded = new ArrayList<>();
        // Java @files are not recursively expanded. Refuse uncertain syntax rather than guessing.
        boolean expandAt = true;
        for (int i = 1; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--disable-@files")) { expandAt = false; expanded.add(a); continue; }
            if (expandAt && a.startsWith("@") && !a.startsWith("@@")) {
                Path path = Path.of(a.substring(1));
                if (!path.isAbsolute()) path = Path.of(c.cwd).resolve(path);
                path = path.normalize();
                if (!Files.isRegularFile(path) || Files.size(path) > 4 * 1024 * 1024) throw new IOException("ARGFILE_UNAVAILABLE");
                c.files.put(path.toString(), digest(path));
                if (!c.argumentFiles.contains(path.toString())) c.argumentFiles.add(path.toString());
                // Conservative ASCII subset; non-ASCII Java @file encodings require a separate test.
                byte[] bytes = Files.readAllBytes(path);
                for (byte b : bytes) if (b < 0) throw new IOException("ARGFILE_ENCODING_UNSUPPORTED");
                expanded.addAll(argFile(new String(bytes, StandardCharsets.US_ASCII)));
            } else expanded.add(a);
        }
        c.gameDir = null;
        for (int i = 0; i < expanded.size(); i++) {
            String a = expanded.get(i);
            String lower = a.toLowerCase(Locale.ROOT);
            if (lower.equals("--server") || lower.startsWith("--server=")
                    || lower.startsWith("--quickplaymultiplayer") || lower.startsWith("--quickplaysingleplayer")
                    || lower.startsWith("--quickplayrealms") || lower.startsWith("-javaagent:"))
                throw new IOException("AUTOJOIN_OR_EXISTING_AGENT_UNSUPPORTED");
            if (a.equals("--gameDir")) {
                if (++i >= expanded.size() || c.gameDir != null) throw new IOException("AMBIGUOUS_GAME_DIR");
                c.gameDir = expanded.get(i);
            } else if (a.startsWith("--gameDir=")) {
                if (c.gameDir != null) throw new IOException("AMBIGUOUS_GAME_DIR");
                c.gameDir = a.substring(10);
            }
        }
        if (c.gameDir == null) throw new IOException("NO_GAME_DIR");
        Path game = Path.of(c.gameDir);
        if (!game.isAbsolute()) game = Path.of(c.cwd).resolve(game);
        c.gameDir = game.normalize().toString();
        if (!Files.isDirectory(game)) throw new IOException("GAME_DIR_MISSING");
        // Record Java class/module path entries and native/launcher paths which already exist.
        for (int i = 0; i < expanded.size(); i++) {
            String a = expanded.get(i), value = null;
            if (List.of("-cp","-classpath","--class-path","-p","--module-path","--upgrade-module-path").contains(a)) {
                if (++i >= expanded.size()) throw new IOException("MISSING_PATH_VALUE");
                value = expanded.get(i);
            } else if (a.startsWith("--class-path=") || a.startsWith("--module-path="))
                value = a.substring(a.indexOf('=') + 1);
            if (a.startsWith("-Djava.library.path=") || a.startsWith("-Dorg.lwjgl.librarypath=")) {
                for (String part : a.substring(a.indexOf('=') + 1).split(";", -1)) {
                    if (part.isEmpty()) throw new IOException("EMPTY_NATIVE_PATH");
                    Path p = Path.of(part);
                    if (!p.isAbsolute()) p = Path.of(c.cwd).resolve(p);
                    if (!Files.isDirectory(p)) throw new IOException("NATIVE_PATH_MISSING");
                    c.directories.add(p.normalize().toString());
                    try (var stream = Files.walk(p)) {
                        for (Path file : stream.filter(Files::isRegularFile).toList()) c.files.put(file.normalize().toString(), digest(file));
                    }
                }
            }
            if (value != null) {
                for (String entry : value.split(";", -1)) {
                    if (entry.isEmpty() || entry.contains("*")) throw new IOException("DYNAMIC_CLASSPATH_UNSUPPORTED");
                    Path p = Path.of(entry);
                    if (!p.isAbsolute()) p = Path.of(c.cwd).resolve(p);
                    if (!Files.exists(p)) throw new IOException("START_PATH_MISSING");
                    if (Files.isRegularFile(p)) c.files.put(p.normalize().toString(), digest(p));
                }
            }
        }
        c.files.put(c.executable, digest(Path.of(c.executable)));
        validateFiles(c);
    }

    /** CRT argument rules for ordinary java/javaw invocations; raw command is used for replay. */
    static List<String> windowsArgs(String s) throws IOException {
        List<String> result = new ArrayList<>(); int i = 0;
        while (i < s.length()) {
            while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
            if (i == s.length()) break;
            StringBuilder b = new StringBuilder(); boolean quoted = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (!quoted && (c == ' ' || c == '\t')) break;
                int slashes = 0;
                while (i < s.length() && s.charAt(i) == '\\') { slashes++; i++; }
                if (i < s.length() && s.charAt(i) == '"') {
                    b.append("\\".repeat(slashes / 2));
                    if ((slashes & 1) == 1) { b.append('"'); i++; }
                    else if (quoted && i + 1 < s.length() && s.charAt(i + 1) == '"') { b.append('"'); i += 2; }
                    else { quoted = !quoted; i++; }
                } else {
                    b.append("\\".repeat(slashes));
                    if (i < s.length()) {
                        c = s.charAt(i);
                        if (!quoted && (c == ' ' || c == '\t')) break;
                        b.append(c); i++;
                    }
                }
            }
            if (quoted) throw new IOException("UNBALANCED_QUOTES");
            result.add(b.toString());
        }
        return result;
    }
    static List<String> argFile(String s) throws IOException {
        List<String> out = new ArrayList<>(); StringBuilder b = new StringBuilder();
        char quote = 0; boolean token = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote == 0 && c == '#') {
                while (i < s.length() && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
                if (token) { out.add(b.toString()); b.setLength(0); token = false; }
            } else if (quote == 0 && Character.isWhitespace(c)) {
                if (token) { out.add(b.toString()); b.setLength(0); token = false; }
            } else if (c == '"' || c == '\'') {
                if (quote == 0) { quote = c; token = true; }
                else if (quote == c) quote = 0;
                else { b.append(c); token = true; }
            } else if (quote != 0 && c == '\\') {
                if (++i >= s.length()) throw new IOException("ARGFILE_ESCAPE");
                c = s.charAt(i);
                if (c == '\n' || c == '\r') {
                    if (c == '\r' && i+1 < s.length() && s.charAt(i+1) == '\n') i++;
                    while (i+1 < s.length() && Character.isWhitespace(s.charAt(i+1))) i++;
                } else b.append(switch (c) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case 'f' -> '\f'; default -> c; });
                token = true;
            } else { b.append(c); token = true; }
        }
        if (quote != 0) throw new IOException("ARGFILE_QUOTE");
        if (token) out.add(b.toString());
        return out;
    }

    /** Reflection avoids bundling/installing JNA or depending on a Minecraft classloader layout. */
    static final class NativeWin {
        final Class<?> pointer, memory, wstring, function;
        final Object kernel;
        final Method getFunction, invokeInt, invokePointer, getWideString, setInt, getInt, getPointer, write, read, clear;
        NativeWin(ClassLoader loader) throws Exception {
            Class<?> library = Class.forName("com.sun.jna.NativeLibrary", true, loader);
            pointer = Class.forName("com.sun.jna.Pointer", true, loader);
            memory = Class.forName("com.sun.jna.Memory", true, loader);
            wstring = Class.forName("com.sun.jna.WString", true, loader);
            function = Class.forName("com.sun.jna.Function", true, loader);
            kernel = library.getMethod("getInstance",String.class).invoke(null,"kernel32");
            getFunction = library.getMethod("getFunction",String.class);
            invokeInt = function.getMethod("invokeInt",Object[].class);
            invokePointer = function.getMethod("invokePointer",Object[].class);
            getWideString = pointer.getMethod("getWideString",long.class);
            setInt = pointer.getMethod("setInt",long.class,int.class);
            getInt = pointer.getMethod("getInt",long.class);
            getPointer = pointer.getMethod("getPointer",long.class);
            write = pointer.getMethod("write",long.class,byte[].class,int.class,int.class);
            read = pointer.getMethod("getByteArray",long.class,int.class);
            clear = memory.getMethod("clear");
        }
        Object mem(long size) throws Exception { Object m=memory.getConstructor(long.class).newInstance(size); clear.invoke(m); return m; }
        Object ws(String value) throws Exception { return wstring.getConstructor(String.class).newInstance(value); }
        Object fn(String name) throws Exception { return getFunction.invoke(kernel,name); }
        int call(String name, Object... args) throws Exception { return (int)invokeInt.invoke(fn(name),(Object)args); }
        Object ptr(String name, Object... args) throws Exception { return invokePointer.invoke(fn(name),(Object)args); }

        void recoverLauncherArguments(Context ctx) throws Exception {
            if (Runtime.version().feature()!=21) throw new IOException("JLI_REQUIRES_JAVA_21");
            Object handle=ptr("GetModuleHandleW",ws("jli.dll"));
            if (handle==null) throw new IOException("JLI_NOT_ALREADY_LOADED");
            Object pathBuffer=mem(32768L*2);
            int pathLength=call("GetModuleFileNameW",handle,pathBuffer,32768);
            if(pathLength<=0||pathLength>=32768)throw new IOException("JLI_MODULE_PATH_UNAVAILABLE");
            Path libraryPath=Path.of((String)getWideString.invoke(pathBuffer,0L)).toRealPath();
            Path expected=Path.of(System.getProperty("java.home"),"bin","jli.dll").toRealPath();
            if(!Files.isSameFile(libraryPath,expected))throw new IOException("JLI_MODULE_PATH_MISMATCH");
            Class<?> library=Class.forName("com.sun.jna.NativeLibrary",true,pointer.getClassLoader());
            Object jli=library.getMethod("getInstance",String.class).invoke(null,libraryPath.toString());
            int count=(int)invokeInt.invoke(getFunction.invoke(jli,"JLI_GetStdArgc"),(Object)new Object[0]);
            int app=(int)invokeInt.invoke(getFunction.invoke(jli,"JLI_GetAppArgIndex"),(Object)new Object[0]);
            if(count<2||count>10000)throw new IOException("JLI_COUNT_INVALID");
            Object array=invokePointer.invoke(getFunction.invoke(jli,"JLI_GetStdArgs"),(Object)new Object[0]);
            if(array==null)throw new IOException("JLI_ARGUMENTS_MISSING");
            int acp=call("GetACP");
            java.nio.charset.Charset charset=java.nio.charset.Charset.forName(acp==65001?"UTF-8":"windows-"+acp);
            List<String> args=new ArrayList<>();
            Method indexOf=pointer.getMethod("indexOf",long.class,byte.class);
            Method getByte=pointer.getMethod("getByte",long.class);
            for(int i=0;i<count;i++){
                if((byte)getByte.invoke(array,i*16L+8)!=0)throw new IOException("JLI_WILDCARD_EXPANSION_UNSUPPORTED");
                Object value=getPointer.invoke(array,i*16L);
                if(value==null)throw new IOException("JLI_NULL_ARGUMENT");
                long length=(long)indexOf.invoke(value,0L,(byte)0);
                if(length<0||length>32767)throw new IOException("JLI_ARGUMENT_TOO_LONG");
                byte[] bytes=(byte[])read.invoke(value,0L,(int)length);
                String decoded=charset.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                if(!Arrays.equals(bytes,decoded.getBytes(charset)))throw new IOException("JLI_ENCODING_ROUNDTRIP_FAILED");
                args.add(decoded);
            }
            applyLauncherArguments(ctx,args,app,System.getProperty("sun.java.command",""));
            ctx.files.put(libraryPath.toString(),digest(libraryPath));
        }


        Context capture() throws Exception {
            Context c = new Context();
            c.executable = ProcessHandle.current().info().command().orElseThrow();
            c.command = (String)getWideString.invoke(ptr("GetCommandLineW"),0L);
            int size = call("GetCurrentDirectoryW",0,null);
            if (size <= 0 || size > 32768) throw new IOException("CWD_READ_FAILED");
            Object cwd = mem(size * 2L);
            int written = call("GetCurrentDirectoryW",size,cwd);
            if (written <= 0 || written >= size) throw new IOException("CWD_CHANGED_DURING_CAPTURE");
            c.cwd = (String)getWideString.invoke(cwd,0L);
            Object env = ptr("GetEnvironmentStringsW");
            if (env == null) throw new IOException("ENVIRONMENT_READ_FAILED");
            try {
                Method getShort = pointer.getMethod("getShort",long.class);
                int chars=0; boolean lastZero=false;
                for (; chars < 1024*1024; chars++) {
                    boolean zero = (short)getShort.invoke(env,chars*2L) == 0;
                    if (zero && lastZero) { chars++; break; }
                    lastZero=zero;
                }
                if (chars >= 1024*1024) throw new IOException("ENVIRONMENT_TOO_LARGE");
                c.environment=(byte[])read.invoke(env,0L,chars*2);
            } finally { call("FreeEnvironmentStringsW",env); }
            return c;
        }
        long launch(Context c) throws Exception {
            byte[] cmd=(c.command+"\0").getBytes(StandardCharsets.UTF_16LE);
            Object command=mem(cmd.length); write.invoke(command,0L,cmd,0,cmd.length);
            Object env=mem(c.environment.length); write.invoke(env,0L,c.environment,0,c.environment.length);
            Object si=mem(104), pi=mem(24);
            setInt.invoke(si,0L,104);
            int success=call("CreateProcessW",ws(c.executable),command,null,null,0,0x400,env,ws(c.cwd),si,pi);
            if (success == 0) throw new IOException("CREATE_PROCESS_FAILED");
            long pid=Integer.toUnsignedLong((int)getInt.invoke(pi,16L));
            call("CloseHandle",getPointer.invoke(pi,8L));
            call("CloseHandle",getPointer.invoke(pi,0L));
            return pid;
        }
    }
}
