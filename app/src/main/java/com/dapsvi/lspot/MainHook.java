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
                "okhttp3.OkHttpClient$Builder",
                lpparam.classLoader,
                "build",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object builder = param.thisObject;
                        OkHttpAdBlocker blocker = new OkHttpAdBlocker(lpparam.classLoader);
                        Object interceptor = blocker.createInterceptor();

                        Method addInterceptor = builder.getClass()
                            .getMethod("addInterceptor", lpparam.classLoader.loadClass("okhttp3.Interceptor"));
                        addInterceptor.invoke(builder, interceptor);

                        XposedBridge.log(TAG + ": Injected ad-blocking interceptor");
                    }
                }
            );

            XposedBridge.log(TAG + ": Hook installed successfully");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed: " + e.getMessage());
            XposedBridge.log(TAG + ": " + Log.getStackTraceString(e));
        }
    }

    private static class OkHttpAdBlocker {

        private final ClassLoader cl;

        OkHttpAdBlocker(ClassLoader cl) {
            this.cl = cl;
        }

        Object createInterceptor() throws Exception {
            Class<?> iface = cl.loadClass("okhttp3.Interceptor");
            return java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[]{iface},
                (proxy, method, args) -> {
                    if ("intercept".equals(method.getName()) && args != null && args.length == 1) {
                        return intercept(args[0]);
                    }
                    String name = method.getName();
                    if (name.equals("toString")) return "SpotifyAdBlocker";
                    if (name.equals("hashCode")) return System.identityHashCode(proxy);
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                });
        }

        private Object intercept(Object chain) throws Throwable {
            Method requestMethod = chain.getClass().getMethod("request");
            Object request = requestMethod.invoke(chain);
            Method urlMethod = request.getClass().getMethod("url");
            Object httpUrl = urlMethod.invoke(request);
            String url = httpUrl.toString();

            for (Pattern p : BLOCKED_PATTERNS) {
                if (p.matcher(url).matches()) {
                    Log.i(TAG, "Blocked: " + url);
                    return emptyResponse(request);
                }
            }

            Method proceedMethod = chain.getClass().getMethod("proceed", request.getClass());
            return proceedMethod.invoke(chain, request);
        }

        private Object emptyResponse(Object request) throws Exception {
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
                bodyClass.getMethod("create", Class.forName("okhttp3.MediaType"), byte[].class)
                    .invoke(null, (Object) null, new byte[0]));

            return respBuilderClass.getMethod("build").invoke(builder);
        }
    }
}
