package com.attendance.app.print;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Renders an attendance sheet to a PDF. Text is drawn with an embedded CJK
 * font when one can be found, so Chinese names render instead of tofu.
 */
public final class PdfExporter {

    private PdfExporter() {
    }

    private static final String[] CJK_CANDIDATES = {
            "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/noto-cjk/NotoSansCJK-Medium.ttc,0",
            "/usr/share/fonts/truetype/arphic/uming.ttc,0",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0",
            "C:/Windows/Fonts/msyh.ttc,0",
            "C:/Windows/Fonts/simhei.ttf",
            "/System/Library/Fonts/PingFang.ttc,0",
            "/Library/Fonts/Arial Unicode.ttf"
    };

    public static void export(File file, String title, List<String> headers,
                              List<String[]> rows, String footer) throws Exception {
        BaseFont base = findBaseFont();
        Font titleFont = new Font(base, 15, Font.BOLD);
        Font headFont = new Font(base, 7.5f, Font.BOLD, Color.WHITE);
        Font cellFont = new Font(base, 7.5f);

        Document document = new Document(PageSize.A4.rotate(), 22, 22, 20, 20);
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        Paragraph heading = new Paragraph(title, titleFont);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingAfter(8);
        document.add(heading);

        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, headFont));
            cell.setBackgroundColor(new Color(0x25, 0x63, 0xEB));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(3);
            table.addCell(cell);
        }
        int nameColumns = 2;
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            for (int c = 0; c < row.length; c++) {
                PdfPCell cell = new PdfPCell(new Paragraph(row[c] == null ? "" : row[c], cellFont));
                cell.setPadding(3);
                if (c < nameColumns) {
                    cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                } else {
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                }
                if (i % 2 == 1) {
                    cell.setBackgroundColor(new Color(0xF1, 0xF5, 0xF9));
                }
                table.addCell(cell);
            }
        }
        document.add(table);

        if (footer != null && !footer.isEmpty()) {
            Paragraph tail = new Paragraph(footer, cellFont);
            tail.setSpacingBefore(8);
            document.add(tail);
        }
        document.close();
    }

    private static BaseFont findBaseFont() {
        for (String candidate : CJK_CANDIDATES) {
            if (candidate.startsWith("/") || candidate.startsWith("C:")) {
                String path = candidate.contains(",") ? candidate.substring(0, candidate.lastIndexOf(',')) : candidate;
                if (!new File(path).exists()) {
                    continue;
                }
            }
            try {
                return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
                // try the next candidate
            }
        }
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("No usable PDF font found", e);
        }
    }
}