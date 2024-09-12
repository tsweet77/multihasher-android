package com.anthroteacher.sha3;

public class NativeLib {

    // Used to load the 'hash_algorithm' library on application startup.
    static {
        System.loadLibrary("sha3");
    }

    public static NativeLib InitInstance(){
        try {
            System.loadLibrary("sha3");
        } catch (Exception e){

        }

        return new NativeLib();
    }

    /**
     * A native method that is implemented by the 'hash_algorithm' native library,
     * which is packaged with this application.
     */
    public native String CalcHash(String plain_text, int repeat, int level, int truncate);
}