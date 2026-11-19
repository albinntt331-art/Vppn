package com.albinvpn.app.vpn

import android.content.Context
import android.util.Log
import java.io.File

interface VpnCoreEngine {
    val name: String
    fun isAvailable(): Boolean
    fun start(
        context: Context,
        configJson: String,
        tunFd: Int,
        protectFd: (Int) -> Boolean,
        onLog: (String) -> Unit
    ): Boolean
    fun stop()
}

class XrayCoreEngine : VpnCoreEngine {
    override val name = "Xray-core (libv2ray)"
    private var controller: Any? = null

    override fun isAvailable(): Boolean = runCatching {
        Class.forName("libv2ray.Libv2ray")
        true
    }.getOrDefault(false)

    private fun ensureAssetsCopied(context: Context) {
        val assetNames = listOf("geoip.dat", "geosite.dat")
        assetNames.forEach { fileName ->
            val outFile = File(context.filesDir, fileName)
            if (!outFile.exists()) {
                runCatching {
                    context.assets.open(fileName).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    override fun start(
        context: Context,
        configJson: String,
        tunFd: Int,
        protectFd: (Int) -> Boolean,
        onLog: (String) -> Unit
    ): Boolean {
        return runCatching {
            ensureAssetsCopied(context)
            val libClass = Class.forName("libv2ray.Libv2ray")
            libClass.getMethod("initCoreEnv", String::class.java, String::class.java)
                .invoke(null, context.filesDir.absolutePath, "")

            val handlerClass = Class.forName("libv2ray.CoreCallbackHandler")
            val proxyHandler = java.lang.reflect.Proxy.newProxyInstance(
                handlerClass.classLoader,
                arrayOf(handlerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onEmitStatus", "emitStatus" -> {
                        val msg = args?.getOrNull(1)?.toString().orEmpty()
                        if (msg.isNotBlank()) onLog("[Xray] $msg")
                    }
                    "protect" -> {
                        val fd = (args?.getOrNull(0) as? Number)?.toInt() ?: -1
                        if (fd >= 0) return@newProxyInstance protectFd(fd)
                    }
                }
                when (method.returnType) {
                    java.lang.Long.TYPE -> 0L
                    java.lang.Integer.TYPE -> 0
                    java.lang.Boolean.TYPE -> true
                    else -> null
                }
            }
            controller = libClass.getMethod("newCoreController", handlerClass).invoke(null, proxyHandler)
            val startMethod = controller!!.javaClass.methods.firstOrNull {
                it.name == "startLoop" && it.parameterTypes.size == 2
            } ?: error("startLoop(String, int) not found in libv2ray")

            startMethod.invoke(controller, configJson, tunFd)
            onLog("[Xray] Туннель запущен (tunFd=$tunFd)")
            true
        }.onFailure { err ->
            val rootCause = err.cause ?: err
            Log.e("Albin-vpn", "Xray start error", rootCause)
            onLog("[Xray ERROR] ${rootCause.message ?: rootCause.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    override fun stop() {
        runCatching { controller?.javaClass?.getMethod("stopLoop")?.invoke(controller) }
        controller = null
    }
}
