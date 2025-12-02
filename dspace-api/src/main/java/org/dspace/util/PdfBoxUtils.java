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

  public static InputStream addHiddenWatermark(PDDocument document, String watermarkText) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    PDFont font = PDType1Font.HELVETICA_BOLD;
    int fontSize = 4;

    for (PDPage page : document.getPages()) {

      // Create content stream in "append" mode
      try (PDPageContentStream cs = new PDPageContentStream(
          document, page,
          PDPageContentStream.AppendMode.APPEND, true, true)) {

        // Set opacity to 0 (invisible) using extended graphics state
        PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
        graphicsState.setNonStrokingAlphaConstant(0f); // 0.0 = fully transparent, 1.0 = fully opaque
        cs.setGraphicsStateParameters(graphicsState);

        // Set transparency (alpha)
        cs.setNonStrokingColor(0f, 0f, 0f); // 0 = invisible, 1 = solid

        // Get font metrics for vertical centering
        // Use font descriptor if available, otherwise use estimated values
        float textAscent = 0;
        float textDescent = 0;
        if (font.getFontDescriptor() != null) {
          Float ascent = font.getFontDescriptor().getAscent();
          Float descent = font.getFontDescriptor().getDescent();
          if (ascent != null) {
            textAscent = ascent / 1000 * fontSize;
          }
          if (descent != null) {
            textDescent = Math.abs(descent / 1000 * fontSize);
          }
        }

        // Fallback: if ascent/descent are not available, use estimated values
        // For Helvetica Bold at 12pt: ascent ~8.8pt, descent ~2.2pt
        if (textAscent == 0 && textDescent == 0) {
          textAscent = fontSize * 0.73f; // Approximate ascent ratio for Helvetica
          textDescent = fontSize * 0.18f; // Approximate descent ratio for Helvetica
        }

        // Begin text
        cs.beginText();
        cs.setFont(font, fontSize);

        // To center rotated text properly:
        // 1. Set rotation matrix around center point (this positions text baseline at
        // center)
        // 2. Then offset in the rotated coordinate space to center the text bounding
        // box
        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(0), 0, 0));

        // Apply offset in the rotated coordinate space
        cs.newLineAtOffset(0, 0);

        cs.showText(watermarkText);
        cs.endText();
      }
    }

    document.save(output);
    System.out.println("Watermark applied successfully.");
    return new ByteArrayInputStream(output.toByteArray());
  }
}
