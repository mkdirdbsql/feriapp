package com.feriapp.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

fun crearUriFotoCamara(contexto: Context): Uri {
    val carpetaFotos = File(contexto.cacheDir, "fotos").apply { mkdirs() }
    val archivo = File.createTempFile("foto_", ".jpg", carpetaFotos)
    return FileProvider.getUriForFile(
        contexto,
        "${contexto.packageName}.fileprovider",
        archivo
    )
}

fun limpiarArchivosTemporalesImagenes(contexto: Context) {
    try {
        contexto.cacheDir.listFiles { _, nombre ->
            nombre.startsWith("imagen_") && nombre.endsWith(".jpg")
        }?.forEach { it.delete() }

        val carpetaFotos = File(contexto.cacheDir, "fotos")
        if (carpetaFotos.exists()) {
            carpetaFotos.listFiles()?.forEach { it.delete() }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun prepararParteImagen(
    contexto: Context,
    uri: Uri,
    nombreCampo: String
): MultipartBody.Part? = withContext(Dispatchers.IO) {
    try {
        val contentResolver = contexto.contentResolver

        var gradosRotacion = 0
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exifInterface = ExifInterface(stream)
                val orientacion = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                gradosRotacion = when (orientacion) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
        val bitmapDecodificado = BitmapFactory.decodeStream(inputStreamDecodificar, null, opcionesDecodificar)
            ?: return@withContext null
        inputStreamDecodificar.close()

        val bitmapRotado = if (gradosRotacion != 0) {
            val matrix = Matrix().apply { postRotate(gradosRotacion.toFloat()) }
            val rot = Bitmap.createBitmap(bitmapDecodificado, 0, 0, bitmapDecodificado.width, bitmapDecodificado.height, matrix, true)
            if (rot != bitmapDecodificado) {
                bitmapDecodificado.recycle()
            }
            rot
        } else {
            bitmapDecodificado
        }

        val anchoActual = bitmapRotado.width
        val altoActual = bitmapRotado.height
        val maxLado = Math.max(anchoActual, altoActual)

        val bitmapEscalado = if (maxLado > maxDimension) {
            val escala = maxDimension.toFloat() / maxLado
            val nuevoAncho = (anchoActual * escala).toInt()
            val nuevoAlto = (altoActual * escala).toInt()
            val esc = Bitmap.createScaledBitmap(bitmapRotado, nuevoAncho, nuevoAlto, true)
            if (esc != bitmapRotado) {
                bitmapRotado.recycle()
            }
            esc
        } else {
            bitmapRotado
        }

        val archivoSalida = File.createTempFile("imagen_", ".jpg", contexto.cacheDir)
        var calidad = 85

        do {
            val fos = FileOutputStream(archivoSalida)
            bitmapEscalado.compress(Bitmap.CompressFormat.JPEG, calidad, fos)
            fos.flush()
            fos.close()
            calidad -= 10
        } while (archivoSalida.length() > 3 * 1024 * 1024 && calidad > 10)

        bitmapEscalado.recycle()

        val requestFile = archivoSalida.asRequestBody("image/jpeg".toMediaType())
        MultipartBody.Part.createFormData(nombreCampo, "imagen.jpg", requestFile)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
