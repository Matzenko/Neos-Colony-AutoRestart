package dev.neoscolony.autorestart;
import java.io.*;import java.net.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.security.*;import java.security.cert.*;import java.math.BigInteger;
public final class FullRolloutHarness {
 static int checks;
 static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;System.out.println("PASS_ROLLOUT "+text);}
 static final String NEW="neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar",OLD="neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar",PIN="neoscolony-autorestart-trusted.properties";
 static final String CPIN="/config/"+PIN;
 static class Core extends URLClassLoader{
  Core(URL[] u){super(u,ClassLoader.getPlatformClassLoader());}
  protected Class<?> loadClass(String n,boolean r)throws ClassNotFoundException{if(n.startsWith("net.minecraft.")||n.startsWith("dev.neoscolony.")||n.startsWith("de.neoscolony."))throw new ClassNotFoundException("No game entrypoints in rollout fixture");return super.loadClass(n,r);}
 }
 static void set(Object o,String f,Object v)throws Exception{Field x=o instanceof Class<?> c?c.getField(f):o.getClass().getDeclaredField(f);x.setAccessible(true);x.set(o instanceof Class<?>?null:o,v);}
 static Object get(Object o,String f)throws Exception{return (o instanceof Class<?> c?c:o.getClass()).getField(f).get(o instanceof Class<?>?null:o);}
 static Object construct(ClassLoader l,String n)throws Exception{return Class.forName(n,true,l).getConstructor().newInstance();}
 static String fingerprint()throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new TestCertificate().getEncoded()));}
 static Properties pin()throws Exception{Properties p=new Properties();p.setProperty("serverHost","server.fixture.invalid");p.setProperty("serverPort","25565");p.setProperty("modpackHost","host.fixture.invalid");p.setProperty("modpackPort","30037");p.setProperty("fingerprint",fingerprint());return p;}
 public static void main(String[] a)throws Exception{
  List<URL> urls=new ArrayList<>();for(String p:System.getProperty("java.class.path").split(File.pathSeparator))urls.add(Path.of(p).toUri().toURL());
  try(Core l=new Core(urls.toArray(URL[]::new))){
   Class<?> g=Class.forName("pl.skidam.automodpack_core.GlobalVariables",true,l);
   set(g,"LOADER","neoforge");set(g,"LOADER_VERSION","21.1.251");set(g,"AM_VERSION","4.0.6");set(g,"MC_VERSION","1.21.1");set(g,"MODS_DIR",Path.of("mods"));
   Files.createDirectories(Path.of("mods"));Files.createDirectories(Path.of("automodpack/private"));
   if(a[0].equals("server"))server(l,g,Path.of(a[1]),Path.of(a[2]));
   else client(l,g,Path.of(a[1]),Path.of(a[2]),Integer.parseInt(a[3]));
  }
  System.out.println("ROLLOUT_ASSERTIONS_PASSED="+checks);
 }
 static void server(Core l,Class<?> g,Path jar,Path old)throws Exception{
  Object config=construct(l,"pl.skidam.automodpack_core.config.Jsons$ServerConfigFieldsV2");
  set(config,"modpackName","fixture");set(config,"modpackHost",true);set(config,"generateModpackOnStart",true);set(config,"syncedFiles",Set.of("/mods/*.jar"));
  set(config,"allowEditsInFiles",Set.of("/config/**"));set(config,"forceCopyFilesToStandardLocation",Set.of(CPIN));
  set(config,"nonModpackFilesToDelete",Map.of("/mods/"+OLD,"d0f8f39848b77e6ea6034b6a85f8400b3267140b"));
  set(g,"serverConfig",config);
  Path root=(Path)get(g,"hostContentModpackDir");Files.createDirectories(root.resolve("mods"));Files.createDirectories(root.resolve("config"));
  Files.copy(jar,root.resolve("mods").resolve(NEW));
  try(var out=Files.newOutputStream(root.resolve("config").resolve(PIN))){pin().store(out,"SYNTHETIC TEST ONLY");}
  Files.writeString(root.resolve("sentinel.txt"),"unrelated content");
  Object executor=construct(l,"pl.skidam.automodpack_core.modpack.ModpackExecutor");
  try{
   Files.delete(root.resolve("mods").resolve(NEW));Files.copy(old,root.resolve("mods").resolve(OLD));check((boolean)executor.getClass().getMethod("generateNew").invoke(executor),"native baseline manifest generated");Files.delete(root.resolve("mods").resolve(OLD));Files.copy(jar,root.resolve("mods").resolve(NEW));check((boolean)executor.getClass().getMethod("generateNew").invoke(executor),"original update generator succeeds");
   Path index=(Path)get(g,"hostModpackContentFile");Object content=Class.forName("pl.skidam.automodpack_core.config.ConfigTools",true,l).getMethod("loadModpackContent",Path.class).invoke(null,index);
   Collection<?> rows=(Collection<?>)get(content,"list");int jarCount=0,pinCount=0;
   for(Object row:rows){String file=(String)get(row,"file");if(file.contains("autorestart")&&file.endsWith(".jar")){jarCount++;check(file.equals("/mods/"+NEW),"manifest never distributes old JAR");}
    if(file.equals(CPIN)){pinCount++;check(Boolean.TRUE.equals(get(row,"forceCopy")),"manifest forces exact config to standard location despite editable wildcard");}
   }
   check(jarCount==1&&pinCount==1,"one JAR and one private configuration");check(((Collection<?>)get(content,"nonModpackFilesToDelete")).size()==1,"original update manifest includes exact hash-protected deletion rule");
  }finally{executor.getClass().getMethod("stop").invoke(executor);check(((ThreadPoolExecutor)executor.getClass().getMethod("getExecutor").invoke(executor)).awaitTermination(10,TimeUnit.SECONDS),"generator threads stopped");}
 }
 @SuppressWarnings({"unchecked","rawtypes"}) static void client(Core l,Class<?> g,Path server,Path old,int number)throws Exception{
  Class<?> tools=Class.forName("pl.skidam.automodpack_core.config.ConfigTools",true,l);
  Object content=tools.getMethod("loadModpackContent",Path.class).invoke(null,server.resolve("automodpack/host-modpack/automodpack-content.json"));
  Path cache=Path.of("automodpack/modpacks/fixture");Files.createDirectories(cache.resolve("mods"));Files.createDirectories(Path.of("config"));
  Files.copy(old,cache.resolve("mods").resolve(OLD));Files.copy(old,Path.of("mods").resolve(OLD));
  if(number==1)Files.copy(server.resolve("automodpack/host-modpack/main/config").resolve(PIN),Path.of("config").resolve(PIN));
  Object cc=construct(l,"pl.skidam.automodpack_core.config.Jsons$ClientConfigFieldsV2");set(cc,"allowRemoteNonModpackDeletions",true);set(g,"clientConfig",cc);
  Object known=construct(l,"pl.skidam.automodpack_core.config.Jsons$KnownHostsFields");Map<String,String> hosts=new HashMap<>();set(known,"hosts",hosts);set(g,"knownHosts",known);
  Class<?> service=Class.forName("pl.skidam.automodpack_loader_core.screen.ScreenService",true,l);int[] prompts={0};boolean[] accepts={false};
  Object proxy=java.lang.reflect.Proxy.newProxyInstance(l,new Class<?>[]{service},(o,m,args)->{
   if(m.getName().equals("getScreen"))return Optional.of(new Object());
   if(m.getName().equals("getScreenString"))return Optional.of("fixture");
   if(m.getName().equals("validation")){prompts[0]++;Object[] values=(Object[])args[0];((Runnable)values[accepts[0]?2:3]).run();}
   return null;
  });
  set(Class.forName("pl.skidam.automodpack_loader_core.screen.ScreenManager",true,l),"INSTANCE",proxy);
  Class<?> utils=Class.forName("pl.skidam.automodpack_loader_core.client.ModpackUtils",true,l);
  InetSocketAddress host=InetSocketAddress.createUnresolved("host.fixture.invalid",30037),address=InetSocketAddress.createUnresolved("server.fixture.invalid",25565);
  var validation=utils.getMethod("userValidationCallback",InetSocketAddress.class,boolean.class);
  java.util.function.Function<X509Certificate,Boolean> f=(java.util.function.Function)validation.invoke(null,host,true);
  check(!TrustedServer.matches(pin(),address,host,hosts.get(host.getHostString())),"configuration alone never authorizes first trust");
  check(!f.apply(new TestCertificate())&&prompts[0]==1&&hosts.isEmpty(),"explicit simulated first rejection prevents trust");
  accepts[0]=true;check(f.apply(new TestCertificate())&&prompts[0]==2,"only explicit simulated first acceptance establishes original known-host trust");
  check(f.apply(new TestCertificate())&&prompts[0]==2,"already accepted certificate prompts no second time");
  check(TrustedServer.matches(pin(),address,host,hosts.get(host.getHostString())),"prepared target plus actual accepted certificate enables simplified UI");
  var foreign=InetSocketAddress.createUnresolved("foreign.fixture.invalid",30037);
  var foreignValidation=(java.util.function.Function<X509Certificate,Boolean>)validation.invoke(null,foreign,false);
  check(!foreignValidation.apply(new TestCertificate())&&!TrustedServer.matches(pin(),address,foreign,hosts.get(host.getHostString())),"foreign host not silently trusted");
  Collection<?> rows=(Collection<?>)get(content,"list");
  for(Object row:rows){String file=((String)get(row,"file")).substring(1);Path src=server.resolve("automodpack/host-modpack/main").resolve(file),dest=cache.resolve(file);Files.createDirectories(dest.getParent());Files.copy(src,dest,StandardCopyOption.REPLACE_EXISTING);}
  Class<?> updater=Class.forName("pl.skidam.automodpack_loader_core.client.ModpackUpdater",true,l);
  var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);Object instance=((sun.misc.Unsafe)uf.get(null)).allocateInstance(updater);
  set(instance,"modpackDir",cache);set(instance,"newDownloadedFiles",new HashSet<>(Set.of(CPIN,"/mods/"+NEW)));set(instance,"changelogs",construct(l,"pl.skidam.automodpack_loader_core.client.Changelogs"));
  Method ignored=updater.getDeclaredMethod("getFilesNotToCopy",Set.class,Set.class);ignored.setAccessible(true);
  Set<?> skip=(Set<?>)ignored.invoke(instance,new HashSet<>(rows),Set.of("/mods/"+NEW));
  check(!skip.contains(CPIN),"native ignore calculation cannot skip forced config");
  utils.getMethod("correctFilesLocations",Path.class,content.getClass(),Set.class).invoke(null,cache,content,skip);
  check(Arrays.equals(Files.readAllBytes(Path.of("config").resolve(PIN)),Files.readAllBytes(cache.resolve("config").resolve(PIN))),"original copy function puts exact config in client/config");
  Method clean=updater.getDeclaredMethod("deleteNonModpackFiles",content.getClass());clean.setAccessible(true);clean.invoke(instance,content);
  check(!Files.exists(cache.resolve("mods").resolve(OLD))&&!Files.exists(Path.of("mods").resolve(OLD)),"native managed cleanup removes old cache and matching root JAR");
  check(Files.exists(cache.resolve("mods").resolve(NEW)),"new JAR retained for loading");
  Path manual=Path.of("mods").resolve(OLD);Files.copy(old,manual);
  Object deletion=get(content,"nonModpackFilesToDelete");Method delete=utils.getMethod("deleteFilesMarkedForDeletionByTheServer",Set.class);
  set(cc,"allowRemoteNonModpackDeletions",false);delete.invoke(null,deletion);check(Files.exists(manual),"native client deletion opt-out respected");
  set(cc,"allowRemoteNonModpackDeletions",true);delete.invoke(null,deletion);check(!Files.exists(manual),"exact old manual JAR migration uses original hash-protected function");
  Files.writeString(manual,"not old jar");delete.invoke(null,deletion);check(Files.exists(manual),"different bytes never deleted by old-JAR hash rule");
  System.out.println("CLIENT_ROLLOUT_OK="+number);
 }
 /** Synthetic certificate solely for original consent-state callback tests, never TLS/network. */
 static final class TestCertificate extends X509Certificate{
  public byte[] getEncoded(){return "synthetic rollout certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8);}
  public void checkValidity(){}public void checkValidity(Date d){}public int getVersion(){return 3;}public BigInteger getSerialNumber(){return BigInteger.ONE;}
  public Principal getIssuerDN(){return new javax.security.auth.x500.X500Principal("CN=fixture");}public Principal getSubjectDN(){return getIssuerDN();}
  public Date getNotBefore(){return new Date(0);}public Date getNotAfter(){return new Date(Long.MAX_VALUE);}
  public byte[] getTBSCertificate(){return getEncoded();}public byte[] getSignature(){return new byte[0];}public String getSigAlgName(){return "TEST";}public String getSigAlgOID(){return "0.0";}public byte[] getSigAlgParams(){return new byte[0];}
  public boolean[] getIssuerUniqueID(){return null;}public boolean[] getSubjectUniqueID(){return null;}public boolean[] getKeyUsage(){return null;}public int getBasicConstraints(){return -1;}
  public void verify(PublicKey k){}public void verify(PublicKey k,String s){}public String toString(){return "synthetic fixture";}public PublicKey getPublicKey(){return null;}
  public boolean hasUnsupportedCriticalExtension(){return false;}public Set<String> getCriticalExtensionOIDs(){return null;}public Set<String> getNonCriticalExtensionOIDs(){return null;}public byte[] getExtensionValue(String s){return null;}
 }
}
