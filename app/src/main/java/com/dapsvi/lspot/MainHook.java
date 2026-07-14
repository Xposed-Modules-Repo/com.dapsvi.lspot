package com.dapsvi.lspot;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;


public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "LSpot";
    private static final String TARGET = "com.spotify.music";

    private static final Pattern[] BLOCKED_PATTERNS = {
        Pattern.compile("https://spclient\\.wg\\.spotify\\.com/ads/.*"),
        Pattern.compile("https://spclient\\.wg\\.spotify\\.com/ad-logic/.*"),
        Pattern.compile("https://spclient\\.wg\\.spotify\\.com/gabo-receiver-service/.*"),
    };

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET)) return;

        XposedBridge.log(TAG + ": Loading for " + lpparam.packageName);

        try {
            XposedHelpers.findAndHookMethod(
                "okhttp3.internal.http.RealInterceptorChain",
                lpparam.classLoader,
                "proceed",
                lpparam.classLoader.loadClass("okhttp3.Request"),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object request = param.args[0];
                        Method urlMethod = request.getClass().getMethod("url");
                        Object httpUrl = urlMethod.invoke(request);
                        String url = httpUrl.toString();

                        for (Pattern p : BLOCKED_PATTERNS) {
                            if (p.matcher(url).matches()) {
                                Log.i(TAG, "Blocked: " + url);
                                param.setResult(emptyResponse(request, lpparam.classLoader));
                                return;
                            }
                        }
                    }
                }
            );

            XposedBridge.log(TAG + ": Hook installed successfully on RealInterceptorChain.proceed()");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook failed: " + e.getMessage());
            XposedBridge.log(TAG + ": " + Log.getStackTraceString(e));
        }
    }

    private static Object emptyResponse(Object request, ClassLoader cl) throws Exception {
        Class<?> responseClass = cl.loadClass("okhttp3.Response");
        Class<?> respBuilderClass = cl.loadClass("okhttp3.Response$Builder");
        Class<?> protocolClass = cl.loadClass("okhttp3.Protocol");
        Class<?> bodyClass = cl.loadClass("okhttp3.ResponseBody");

        Object http11 = protocolClass.getField("HTTP_1_1").get(null);
        Object builder = respBuilderClass.getDeclaredConstructor().newInstance();

        respBuilderClass.getMethod("request", request.getClass()).invoke(builder, request);
        respBuilderClass.getMethod("protocol", protocolClass).invoke(builder, http11);
        respBuilderClass.getMethod("code", int.class).invoke(builder, 200);
        respBuilderClass.getMethod("message", String.class).invoke(builder, "OK");
        respBuilderClass.getMethod("body", bodyClass).invoke(builder,
            bodyClass.getMethod("create",
                cl.loadClass("okhttp3.MediaType"), byte[].class)
                .invoke(null, (Object) null, new byte[0]));

        return respBuilderClass.getMethod("build").invoke(builder);
    }
}
