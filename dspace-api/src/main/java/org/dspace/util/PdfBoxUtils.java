package org.dspace.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;



public class PdfBoxUtils {

  public static InputStream addWatermark(PDDocument document) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    String watermarkText = "HCMUTE - CONFIDENTIAL";
    PDFont font = PDType1Font.HELVETICA_BOLD;
    int fontSize = 60;

    for (PDPage page : document.getPages()) {

      // Create content stream in "append" mode
      try (PDPageContentStream cs = new PDPageContentStream(
          document, page,
          PDPageContentStream.AppendMode.APPEND, true, true)) {

        // Set opacity to 0.3 using extended graphics state
        PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
        graphicsState.setNonStrokingAlphaConstant(0.3f); // 0.0 = fully transparent, 1.0 = fully opaque
        cs.setGraphicsStateParameters(graphicsState);

        // Set transparency (alpha)
        cs.setNonStrokingColor(0f, 0f, 0f); // 0 = invisible, 1 = solid

        // Example: rotate around center of page
        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();

        // Calculate text width and height
        float textWidth = font.getStringWidth(watermarkText) / 1000 * fontSize;
        float textHeight = font.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * fontSize;

        // Calculate center position
        float centerX = pageWidth / 2;
        float centerY = pageHeight / 2;

        // Begin text
        cs.beginText();
        cs.setFont(font, fontSize);

        // Rotate around center of page, then offset to center the text
        // The rotation happens around (centerX, centerY)
        // Then we offset by half the text width and height to truly center it
        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), centerX, centerY));

        // Offset to center the text (accounting for rotation)
        // For rotated text, we need to offset by -textWidth/2 and -textHeight/4
        // (textHeight/4 is approximate for vertical centering with Helvetica)
        cs.newLineAtOffset(-textWidth / 2, -textHeight / 4);

        cs.showText(watermarkText);
        cs.endText();
      }
    }

    document.save(output);
    System.out.println("Watermark applied successfully.");
    return new ByteArrayInputStream(output.toByteArray());
  }

}
