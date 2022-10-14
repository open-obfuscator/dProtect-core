package dprotect.util;
import dprotect.util.ObfuscationProcessable;

// Class that is used to wrap obfuscation info in Clazz, Method, ...
public class ObfuscationInfo implements ObfuscationProcessable
{
    public Object info;

    @Override
    public Object getObfuscationInfo()
    {
        return info;
    }


    @Override
    public void setObfuscationInfo(Object obfuscationInfo)
    {
        this.info = obfuscationInfo;
    }
}
