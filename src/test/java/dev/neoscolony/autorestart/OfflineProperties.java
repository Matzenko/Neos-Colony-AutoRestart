package dev.neoscolony.autorestart;
import java.util.*;
import org.spongepowered.asm.service.*;
public final class OfflineProperties implements IGlobalPropertyService{
    private static final Map<IPropertyKey,Object> MAP=new HashMap<>();
    record Key(String name)implements IPropertyKey{}
    public IPropertyKey resolveKey(String n){return new Key(n);}
    @SuppressWarnings("unchecked") public <T>T getProperty(IPropertyKey k){return (T)MAP.get(k);}
    public void setProperty(IPropertyKey k,Object v){MAP.put(k,v);}
    public <T>T getProperty(IPropertyKey k,T fallback){T v=getProperty(k);return v==null?fallback:v;}
    public String getPropertyString(IPropertyKey k,String fallback){Object v=MAP.get(k);return v==null?fallback:v.toString();}
}
