package dev.neoscolony.autorestart;
import java.nio.file.*;
import java.util.concurrent.*;
public final class DistributionTests {
    public static int run(Path own,Path auto)throws Exception{
        Path work=Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")),"distribution-");
        Path log=work.resolve("result.txt");
        ProcessBuilder pb=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
            "-Djava.io.tmpdir="+work,"-cp",System.getProperty("java.class.path"),DistributionHarness.class.getName(),
            own.toAbsolutePath().toString(),auto.toAbsolutePath().toString());
        pb.directory(work.toFile());pb.redirectErrorStream(true);pb.redirectOutput(log.toFile());
        Process p=pb.start();
        if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Distribution test timed out");}
        String result=Files.readString(log);System.out.print(result);
        if(p.exitValue()!=0)throw new AssertionError("Original AutoModpack distribution fixture failed");
        var match=java.util.regex.Pattern.compile("DISTRIBUTION_ASSERTIONS_PASSED=(\\d+)").matcher(result);
        if(!match.find())throw new AssertionError("Missing distribution test summary");
        return Integer.parseInt(match.group(1));
    }
}
