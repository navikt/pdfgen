package no.nav.pdfgen

import java.net.ServerSocket

fun getRandomPort(): Int = ServerSocket(0).use { it.localPort }
