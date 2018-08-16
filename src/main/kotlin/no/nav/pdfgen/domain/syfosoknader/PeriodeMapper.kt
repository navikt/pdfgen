package no.nav.pdfgen.domain.syfosoknader

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

import java.io.IOException

object PeriodeMapper {
    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())

    internal fun jsonTilPeriode(json: String): Periode {
        try {
            val periode = objectMapper.readValue(json, Periode::class.java)
            if (periode.tom == null || periode.fom == null
                    || periode.fom.isAfter(periode.tom)) {
                throw IllegalArgumentException()
            }
            return periode
        } catch (exception: JsonParseException) {
            throw IllegalArgumentException(exception)
        } catch (exception: JsonMappingException) {
            throw IllegalArgumentException(exception)
        } catch (iOException: IOException) {
            throw RuntimeException(iOException)
        }

    }
}
