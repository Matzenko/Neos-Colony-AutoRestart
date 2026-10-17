package dev.neoscolony.autorestart;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
public final class UpdateFingerprint{
    public static String from(Path directory)throws Exception{
        Path file=directory.resolve("automodpack-content.json");
        if(!Files.isRegularFile(file)||Files.size(file)>32*1024*1024)throw new IllegalStateException("UPDATE_METADATA_UNAVAILABLE");
        String text=Files.readString(file);
        JsonObject root=JsonParser.parseString(text).getAsJsonObject();
        if(!root.has("mcVersion")||!root.get("mcVersion").getAsString().equals("1.21.1")||
           !root.has("loader")||!root.get("loader").getAsString().equalsIgnoreCase("neoforge")||
           !root.has("loaderVersion")||!root.get("loaderVersion").getAsString().equals("21.1.251"))
            throw new IllegalStateException("UPDATED_LOADER_OR_MC_REQUIRES_MANUAL_START");
        verifyFiles(directory,root);
        return json(text);
    }

    static void verifyFiles(Path directory,JsonObject root)throws Exception{
        Path base=directory.toRealPath();
        JsonArray entries=root.getAsJsonArray("list");
        if(entries==null||entries.isEmpty())throw new IllegalStateException("UPDATE_METADATA_EMPTY");
        for(JsonElement element:entries){
            JsonObject row=element.getAsJsonObject();
            String relative=row.get("file").getAsString();
            if(relative.startsWith("/"))relative=relative.substring(1);
            if(relative.isEmpty()||relative.contains(":")||relative.contains("\\")||Path.of(relative).isAbsolute())
                throw new IllegalStateException("UPDATE_FILE_PATH_REFUSED");
            Path path=base.resolve(relative).normalize();
            if(!path.startsWith(base)||!Files.isRegularFile(path)||!path.toRealPath().startsWith(base))
                throw new IllegalStateException("UPDATE_FILE_MISSING");
            String expected=row.get("sha1").getAsString();
            if(!expected.matches("[a-fA-F0-9]{40}")||Files.size(path)!=Long.parseLong(row.get("size").getAsString()))
                throw new IllegalStateException("UPDATE_FILE_INVALID");
            MessageDigest md=MessageDigest.getInstance("SHA-1");
            try(var in=Files.newInputStream(path)){
                byte[] buffer=new byte[65536];int count;
                while((count=in.read(buffer))!=-1)md.update(buffer,0,count);
            }
            if(!HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(expected))
                throw new IllegalStateException("UPDATE_FILE_HASH_MISMATCH");
        }
    }

    static String json(String text)throws Exception{
        JsonObject root=JsonParser.parseString(text).getAsJsonObject();
        JsonArray rows=root.getAsJsonArray("list");
        if(rows==null||rows.isEmpty())throw new IllegalStateException("UPDATE_METADATA_EMPTY");
        TreeMap<String,String> sorted=new TreeMap<>();
        for(JsonElement row:rows){
            JsonObject r=row.getAsJsonObject();
            for(String key:List.of("file","sha1","size","type"))if(!r.has(key)||r.get(key).isJsonNull())throw new IllegalStateException("UPDATE_METADATA_INCOMPLETE");
            String path=r.get("file").getAsString();
            if(sorted.containsKey(path))throw new IllegalStateException("UPDATE_METADATA_DUPLICATE");
            JsonObject normalized=new JsonObject();
            for(String key:List.of("file","sha1","size","type","editable","forceCopy"))
                normalized.add(key,r.has(key)?r.get(key):JsonNull.INSTANCE);
            sorted.put(path,normalized.toString());
        }
        JsonObject canonical=new JsonObject();
        for(String key:List.of("modpackName","automodpackVersion","loader","loaderVersion","mcVersion"))
            canonical.add(key,root.has(key)?root.get(key):JsonNull.INSTANCE);
        JsonArray list=new JsonArray();sorted.values().forEach(list::add);canonical.add("list",list);
        TreeSet<String> deletes=new TreeSet<>();
        if(root.has("nonModpackFilesToDelete"))for(JsonElement item:root.getAsJsonArray("nonModpackFilesToDelete")){
            JsonObject r=item.getAsJsonObject();JsonArray normalized=new JsonArray();
            normalized.add(r.get("file"));normalized.add(r.get("sha1"));deletes.add(normalized.toString());
        }
        JsonArray deletionList=new JsonArray();deletes.forEach(deletionList::add);canonical.add("delete",deletionList);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }
    public static boolean allowed(String fingerprint,String previous){return !fingerprint.equals(previous);}
}
