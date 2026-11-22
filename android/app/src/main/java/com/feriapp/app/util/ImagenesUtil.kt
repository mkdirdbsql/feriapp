package com.feriapp.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

suspend fun prepararParteImagen(
    contexto: Context,
    uri: Uri,
    nombreCampo: String
): MultipartBody.Part? = withContext(Dispatchers.IO) {
    try {
        val contentResolver = contexto.contentResolver
        val inputStreamInfo = contentResolver.openInputStream(uri) ?: return@withContext null

        val opcionesBounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStreamInfo, null, opcionesBounds)
        inputStreamInfo.close()

        val anchoOriginal = opcionesBounds.outWidth
        val altoOriginal = opcionesBounds.outHeight
        if (anchoOriginal <= 0 || altoOriginal <= 0) return@withContext null

        val maxDimension = 1280
        var inSampleSize = 1
        while ((anchoOriginal / inSampleSize) > maxDimension || (altoOriginal / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }

        val opcionesDecodificar = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }

        val inputStreamDecodificar = contentResolver.openInputStream(uri) ?: return@withContext null
        val bitmapOriginal = BitmapFactory.decodeStream(inputStreamDecodificar, null, opcionesDecodificar)
            ?: return@withContext null
        inputStreamDecodificar.close()

        val anchoActual = bitmapOriginal.width
        val altoActual = bitmapOriginal.height
        val maxLado = Math.max(anchoActual, altoActual)

        val bitmapEscalado = if (maxLado > maxDimension) {
            val escala = maxDimension.toFloat() / maxLado
            val nuevoAncho = (anchoActual * escala).toInt()
            val nuevoAlto = (altoActual * escala).toInt()
            Bitmap.createScaledBitmap(bitmapOriginal, nuevoAncho, nuevoAlto, true)
        } else {
            bitmapOriginal
        }

        var calidad = 85
        val archivoSalida = File(contexto.cacheDir, "imagen_puesto_temp.jpg")

        do {
            val fos = FileOutputStream(archivoSalida)
            bitmapEscalado.compress(Bitmap.CompressFormat.JPEG, calidad, fos)
            fos.flush()
            fos.close()
            calidad -= 10
        } while (archivoSalida.length() > 3 * 1024 * 1024 && calidad > 10)

        if (bitmapEscalado != bitmapOriginal) {
            bitmapOriginal.recycle()
        }
        bitmapEscalado.recycle()

        val requestFile = archivoSalida.asRequestBody("image/jpeg".toMediaType())
        MultipartBody.Part.createFormData(nombreCampo, "imagen.jpg", requestFile)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
