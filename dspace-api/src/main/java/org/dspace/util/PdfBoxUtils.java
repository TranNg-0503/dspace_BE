package org.dspace.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

public class PdfBoxUtils {

  public static InputStream addWatermark(
        PDDocument document,
        String publisherName) throws Exception {

    ByteArrayOutputStream output = new ByteArrayOutputStream();

    if (publisherName == null || publisherName.trim().isEmpty()) {
        publisherName = "HCMUTE";
    }

    String watermarkText = publisherName;

    PDFont font = PDType0Font.load(
        document,
        new File("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));

    for (PDPage page : document.getPages()) {

      // Create content stream in "append" mode
      try (PDPageContentStream cs = new PDPageContentStream(
          document, page,
          PDPageContentStream.AppendMode.APPEND, true, true)) {

        // Set opacity to 0.3 using extended graphics state
        PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
        graphicsState.setNonStrokingAlphaConstant(0.18f);
        cs.setGraphicsStateParameters(graphicsState);

        // Set transparency (alpha)
        cs.setNonStrokingColor(0f, 0f, 0f); // 0 = invisible, 1 = solid

       

        // Kích thước trang
        PDRectangle mediaBox = page.getMediaBox();
        float pageWidth = mediaBox.getWidth();
        float pageHeight = mediaBox.getHeight();

        // Font nhỏ hơn
        int fontSize = 22;
        if (publisherName.length() > 22)
          fontSize = 18;
      else
          fontSize = 22;

        // Góc xoay
        double angle = Math.toRadians(30);

        // Chiều rộng và chiều cao text
        float textWidth =
                font.getStringWidth(watermarkText) / 1000 * fontSize;

        float textHeight =
                font.getFontDescriptor()
                    .getCapHeight() / 1000 * fontSize;

        // ===== TÍNH TOÁN ĐỂ KHÔNG BỊ CẮT =====

        // Chừa lề trái
        float marginLeft = 35;

        // Chiều cao mà text chiếm sau khi xoay
        float rotatedHeight =
                (float)(textWidth * Math.sin(angle)
                      + textHeight * Math.cos(angle));

        // Chừa lề trên
        float marginTop = 35;

        // Điểm bắt đầu
        float x = marginLeft;
        float y = pageHeight - rotatedHeight - marginTop;

        // ===== VẼ WATERMARK =====
        cs.beginText();
        cs.setFont(font, fontSize);

        cs.setTextMatrix(
                Matrix.getRotateInstance(
                        angle,
                        x,
                        y));

        cs.showText(watermarkText);

        cs.endText();
      }
    }

    document.save(output);
    System.out.println("Watermark applied successfully.");
    return new ByteArrayInputStream(output.toByteArray());
  }
  public static InputStream addHiddenWatermark(
          PDDocument document,
          String hiddenEmail,          // watermark ẩn
          String visibleTimestamp,      // watermark hiện
          String visibleTimestamp2
  ) throws Exception {

      ByteArrayOutputStream output = new ByteArrayOutputStream();

      PDFont hiddenFont = PDType0Font.load(
        document,
        new File("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));  // font watermark ẩn
      int hiddenFontSize = 1;                           // giữ nguyên size cũ

      PDFont visibleFont = PDType0Font.load(
        document,
        new File("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));  // font watermark hiện
      int visibleFontSize = 7;                         // size hiện

      for (PDPage page : document.getPages()) {

          PDRectangle mediaBox = page.getMediaBox();
          float pageWidth = mediaBox.getWidth();
          float marginBottom = 30;

          // ===========================
          // TÍNH TÂM CHO TIMESTAMP
          // ===========================
          float textWidth = visibleFont.getStringWidth(visibleTimestamp) / 1000 * visibleFontSize;
          float textWidth2 = visibleFont.getStringWidth(visibleTimestamp2) / 1000 * visibleFontSize;

          float centerX = (pageWidth - textWidth) / 2;

          // 1) VẼ TIMESTAMP HIỆN
          try (PDPageContentStream cs = new PDPageContentStream(
              document, page,
              PDPageContentStream.AppendMode.APPEND, true, true)) {

          float timestampWidth =
                  visibleFont.getStringWidth(visibleTimestamp)
                          / 1000 * visibleFontSize;

          float timestampWidth2 =
                  visibleFont.getStringWidth(visibleTimestamp2)
                          / 1000 * visibleFontSize;

          float timestampCenterX =
                  (pageWidth - timestampWidth) / 2;

          float timestampCenterX2 =
                  (pageWidth - timestampWidth2) / 2;

          PDExtendedGraphicsState visibleState =
                  new PDExtendedGraphicsState();
          visibleState.setNonStrokingAlphaConstant(1f);

          cs.setGraphicsStateParameters(visibleState);

          cs.beginText();
          cs.setFont(visibleFont, visibleFontSize);

          // Dòng 1
          cs.newLineAtOffset(timestampCenterX, marginBottom + 8);
          cs.showText(visibleTimestamp);

          // Dòng 2 (xuống dưới 10 đơn vị)
          cs.newLineAtOffset(
                  timestampCenterX2 - timestampCenterX,
                  -10);

          cs.showText(visibleTimestamp2);

          cs.endText();
      }

          // 2) VẼ WATERMARK ẨN
          try (PDPageContentStream csHidden = new PDPageContentStream(
                  document, page,
                  PDPageContentStream.AppendMode.APPEND, true, true)) {

              float hiddenWidth = hiddenFont.getStringWidth(hiddenEmail) / 1000 * hiddenFontSize;
              float hiddenCenterX = (pageWidth - hiddenWidth) / 2;

              PDExtendedGraphicsState hiddenState = new PDExtendedGraphicsState();
              hiddenState.setNonStrokingAlphaConstant(0.01f); // vô hình
              csHidden.setGraphicsStateParameters(hiddenState);

              csHidden.setNonStrokingColor(0f, 0f, 0f); // màu đen nhưng alpha=0 → vô hình

              csHidden.beginText();
              csHidden.setFont(hiddenFont, hiddenFontSize);
              csHidden.newLineAtOffset(hiddenCenterX, marginBottom + 8);
              csHidden.showText(hiddenEmail);
              csHidden.endText();
          }

      }

      document.save(output);
      System.out.println("Watermark applied successfully.");
      return new ByteArrayInputStream(output.toByteArray());
  }
}
