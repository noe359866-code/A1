package com.omnitv.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetPolicyTest {

    private fun assertAllowed(vararg urls: String) =
        urls.forEach { assertTrue("debería aceptarse: $it", NetPolicy.isAllowed(it)) }

    private fun assertRejected(vararg urls: String) =
        urls.forEach { assertFalse("debería rechazarse: $it", NetPolicy.isAllowed(it)) }

    @Test
    fun `https se acepta para cualquier host`() = assertAllowed(
        "https://v3-cinemeta.strem.io/manifest.json",
        "https://example.com",
        "https://192.168.1.20:8443/stream",
        "HTTPS://EXAMPLE.COM/a.m3u8",
        "  https://example.com/con-espacios  "
    )

    @Test
    fun `http se acepta solo en la red local`() = assertAllowed(
        "http://192.168.1.20:8787",
        "http://192.168.0.1/stream/movie/tt1375666.json",
        "http://10.0.0.5:8787/",
        "http://172.16.0.1",
        "http://172.31.255.255:80/v.mp4",
        "http://169.254.10.20/",
        "http://127.0.0.1:8787/health",
        "http://localhost:8787",
        "http://salon-pc.local:8787",
        "http://[::1]:8787/",
        "http://[fd12:3456:789a::1]/",
        "http://[fe80::1]:8787/"
    )

    @Test
    fun `http hacia internet se rechaza`() = assertRejected(
        "http://example.com/manifest.json",
        "http://8.8.8.8/",
        "http://172.15.0.1/",
        "http://172.32.0.1/",
        "http://192.169.1.1/",
        "http://11.0.0.1/",
        "http://100.64.0.1/",
        "http://[2001:db8::1]/"
    )

    @Test
    fun `un host publico no pasa por local con trucos de URL`() = assertRejected(
        "http://192.168.1.5.evil.com/",
        "http://192.168.1.5@evil.com/",
        "http://user:pass@192.168.1.5/",
        "http://evil.com/?host=192.168.1.5",
        "http://evil.com/#192.168.1.5",
        "http://localhost.evil.com/",
        "http://evil.local.com/",
        "http://0xC0.0xA8.1.1/",
        "http://192.168.1/",
        "http://192.168.01.1/",
        "http://3232235777/"
    )

    @Test
    fun `otros esquemas y textos que no son URL se rechazan`() = assertRejected(
        "",
        "   ",
        "no es una url",
        "ftp://192.168.1.20/v.mp4",
        "file:///sdcard/v.mp4",
        "javascript:alert(1)",
        "httpx://example.com",
        "http://",
        "https://",
        "//example.com/sin-esquema",
        "https://user:pass@example.com/"
    )
}
