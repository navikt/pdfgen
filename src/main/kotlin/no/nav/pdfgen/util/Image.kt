package no.nav.pdfgen.util

import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject

data class ImageSize(val width: Float, val height: Float)

fun toPortait(image: BufferedImage): BufferedImage {
    if (image.height >= image.width) {
        return image
    }

    val rotateTransform =
        AffineTransform.getRotateInstance(
            Math.toRadians(90.0),
            (image.height / 2f).toDouble(),
            (image.height / 2f).toDouble()
        )

    return AffineTransformOp(rotateTransform, AffineTransformOp.TYPE_BILINEAR)
        .filter(image, BufferedImage(image.height, image.width, image.type))
}

fun scale(image: PDImageXObject, page: PDPage): ImageSize {
    var width = image.width.toFloat()
    var height = image.height.toFloat()

    if (width > page.cropBox.width) {
        width = page.cropBox.width
        height = width * image.height.toFloat() / image.width.toFloat()
    }

    if (height > page.cropBox.height) {
        height = page.cropBox.height
        width = height * image.width.toFloat() / image.height.toFloat()
    }

    return ImageSize(width, height)
}
