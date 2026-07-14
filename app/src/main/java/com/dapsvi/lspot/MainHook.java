package com.dapsvi.lspot;

import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
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
            Class<?> builderClass = lpparam.classLoader.loadClass("okhttp3.OkHttpClient$Builder");
            StringBuilder sb = new StringBuilder();
            sb.append(TAG).append(": Builder methods:");
            for (Method m : builderClass.getDeclaredMethods()) {
                sb.append("\n  ").append(m.getName())
                  .append("(").append(Arrays.toString(m.getParameterTypes())).append(")")
                  .append(" -> ").append(m.getReturnType().getSimpleName());
            }
            XposedBridge.log(sb.toString());
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Builder enum failed: " + e.getMessage());
        }

        try {
            Class<?> ricClass = lpparam.classLoader.loadClass("okhttp3.internal.http.RealInterceptorChain");
            StringBuilder sb = new StringBuilder();
            sb.append(TAG).append(": RIC methods:");
            for (Method m : ricClass.getDeclaredMethods()) {
                sb.append("\n  ").append(m.getName())
                  .append("(").append(Arrays.toString(m.getParameterTypes())).append(")")
                  .append(" -> ").append(m.getReturnType().getSimpleName());
            }
            XposedBridge.log(sb.toString());
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": RIC enum failed: " + e.getMessage());
        }

        boolean hooked = false;

        try {
            Class<?> builderClass = lpparam.classLoader.loadClass("okhttp3.OkHttpClient$Builder");
            final Class<?> interceptorClass = lpparam.classLoader.loadClass("okhttp3.Interceptor");

            for (Method m : builderClass.getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("build") || name.contains("create") || name.contains("new")) {
                    XposedBridge.log(TAG + ": Hooking Builder." + m.getName()
                        + "(" + Arrays.toString(m.getParameterTypes()) + ")");

                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object client = param.getResult();
                            if (client == null) return;

                            Object blocker = Proxy.newProxyInstance(
                                lpparam.classLoader,
                                new Class<?>[]{interceptorClass},
                                (proxy, method, args) -> {
                                    if ("intercept".equals(method.getName()) && args != null && args.length == 1) {
                                        Object chain = args[0];
                                        Method reqMethod = chain.getClass().getMethod("request");
                                        Object request = reqMethod.invoke(chain);
                                        Method urlMethod = request.getClass().getMethod("url");
                                        Object httpUrl = urlMethod.invoke(request);
                                        String url = httpUrl.toString();

                                        for (Pattern p : BLOCKED_PATTERNS) {
                                            if (p.matcher(url).matches()) {
                                                Log.i(TAG, "Blocked: " + url);
                                                return emptyResponse(request, lpparam.classLoader);
                                            }
                                        }

                                        Method proceedMethod = chain.getClass().getMethod("proceed", request.getClass());
                                        return proceedMethod.invoke(chain, request);
                                    }
                                    String n = method.getName();
                                    if (n.equals("toString")) return "SpotifyAdBlocker";
                                    if (n.equals("hashCode")) return System.identityHashCode(proxy);
                                    if (n.equals("equals")) return proxy == args[0];
                                    return null;
                                });

                            try {
                                client.getClass().getMethod("addInterceptor", interceptorClass)
                                    .invoke(client, blocker);
                                XposedBridge.log(TAG + ": Interceptor added via " + m.getName());
                                hooked = true;
                            } catch (NoSuchMethodException nsme) {
                                try {
                                    client.getClass().getMethod("addNetworkInterceptor", interceptorClass)
                                        .invoke(client, blocker);
                                    XposedBridge.log(TAG + ": Network interceptor added via " + m.getName());
                                    hooked = true;
                                } catch (NoSuchMethodException nsme2) {
                                    Log.w(TAG, "No addInterceptor on OkHttpClient");
                                }
                            }
                        }
                    });
                }
            }
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Builder hook failed: " + e.getMessage());
        }

        if (!hooked) {
            try {
                XposedHelpers.findAndHookConstructor(
                    "java.net.URL",
                    lpparam.classLoader,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String url = (String) param.args[0];
                            for (Pattern p : BLOCKED_PATTERNS) {
                                if (p.matcher(url).matches()) {
                                    Log.i(TAG, "Blocked ad URL: " + url);
                                    param.args[0] = "http://127.0.0.1/blocked";
                                    break;
                                }
                            }
                        }
                    }
                );
                XposedBridge.log(TAG + ": Hooked java.net.URL constructor");
                hooked = true;
            } catch (Throwable e) {
                XposedBridge.log(TAG + ": URL hook failed: " + e.getMessage());
            }
        }

        if (hooked) {
            XposedBridge.log(TAG + ": Hook installed successfully");
        } else {
            XposedBridge.log(TAG + ": ALL HOOK METHODS FAILED");
        }
    }

    private static Object emptyResponse(Object request, ClassLoader cl) throws Exception {
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
