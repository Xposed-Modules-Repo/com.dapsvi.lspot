package com.dapsvi.lspot;

import android.util.Log;

import java.lang.reflect.Method;
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
            String[] methodNames = {"build", "get", "call", "create"};
            final Class<?> interceptorClass = lpparam.classLoader.loadClass("okhttp3.Interceptor");

            for (String name : methodNames) {
                if (hooked) break;
                try {
                    XposedHelpers.findAndHookMethod(
                        "okhttp3.OkHttpClient$Builder",
                        lpparam.classLoader,
                        name,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                Object client = param.getResult();
                                if (client == null) return;

                                Object blocker = java.lang.reflect.Proxy.newProxyInstance(
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
                                    XposedBridge.log(TAG + ": Interceptor added");
                                } catch (NoSuchMethodException e) {
                                    try {
                                        client.getClass().getMethod("addNetworkInterceptor", interceptorClass)
                                            .invoke(client, blocker);
                                        XposedBridge.log(TAG + ": Net interceptor added");
                                    } catch (NoSuchMethodException e2) {
                                        Log.w(TAG, "No addInterceptor on OkHttpClient");
                                    }
                                }
                            }
                        }
                    );
                    XposedBridge.log(TAG + ": Hooked Builder." + name);
                    hooked = true;
                } catch (Throwable ignored) {}
            }

            if (!hooked) {
                Class<?> builderClass = lpparam.classLoader.loadClass("okhttp3.OkHttpClient$Builder");
                for (Method m : builderClass.getDeclaredMethods()) {
                    if (hooked) break;
                    if (m.getParameterCount() > 0) continue;
                    try {
                        XposedHelpers.findAndHookMethod(
                            "okhttp3.OkHttpClient$Builder",
                            lpparam.classLoader,
                            m.getName(),
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object client = param.getResult();
                                    if (client == null) return;
                                    Object blocker = java.lang.reflect.Proxy.newProxyInstance(
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
                                        client.getClass().getMethod("addInterceptor", interceptorClass).invoke(client, blocker);
                                    } catch (NoSuchMethodException e) {
                                        try {
                                            client.getClass().getMethod("addNetworkInterceptor", interceptorClass).invoke(client, blocker);
                                        } catch (NoSuchMethodException e2) {}
                                    }
                                }
                            }
                        );
                        XposedBridge.log(TAG + ": Hooked Builder." + m.getName());
                        hooked = true;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Builder hook failed: " + e.getMessage());
        }

        if (!hooked) {
            try {
                Class<?> ricClass = lpparam.classLoader.loadClass("okhttp3.internal.http.RealInterceptorChain");
                Class<?> requestClass = lpparam.classLoader.loadClass("okhttp3.Request");
                String ricMethod = null;
                for (Method m : ricClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1
                        && m.getParameterTypes()[0].equals(requestClass)
                        && m.getReturnType().getName().equals("okhttp3.Response")
                        && !m.getName().startsWith("lambda$")) {
                        ricMethod = m.getName();
                        break;
                    }
                }

                if (ricMethod != null) {
                    XposedHelpers.findAndHookMethod(
                        "okhttp3.internal.http.RealInterceptorChain",
                        lpparam.classLoader,
                        ricMethod,
                        requestClass,
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
                    XposedBridge.log(TAG + ": Hooked RealInterceptorChain." + ricMethod + "() (dynamic)");
                    hooked = true;
                } else {
                    XposedBridge.log(TAG + ": No matching method found on RealInterceptorChain");
                }
            } catch (Throwable e) {
                XposedBridge.log(TAG + ": RIC dynamic hook failed: " + e.getMessage());
            }
        }

        if (!hooked) {
            try {
                Class<?> urlClass = lpparam.classLoader.loadClass("java.net.URL");
                Method hookConstructors = XposedBridge.class.getMethod(
                    "hookAllConstructors", Class.class, XC_MethodHook.class);
                hookConstructors.invoke(null, urlClass, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String url = param.args[0] != null ? param.args[0].toString() : null;
                        if (url == null) return;
                        for (Pattern p : BLOCKED_PATTERNS) {
                            if (p.matcher(url).matches()) {
                                Log.i(TAG, "URL blocked: " + url);
                                param.args[0] = "http://127.0.0.1/blocked";
                                break;
                            }
                        }
                    }
                });
                XposedBridge.log(TAG + ": Hooked java.net.URL constructors (JVM level)");
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
