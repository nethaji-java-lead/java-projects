package com.example.fileflow;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;

@Component
public class PdfGenerator {
    public void generate(InputStream xml, OutputStream out) throws Exception {
        // Stream XML: keep only a short printable preview, not the full 50 MB document.
        StringBuilder preview = new StringBuilder(120);
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.newSAXParser().parse(xml, new DefaultHandler() {
            @Override public void characters(char[] ch, int start, int length) {
                for (int i = start; i < start + length && preview.length() < 100; i++) {
                    char c = ch[i];
                    preview.append(c >= 32 && c <= 126 ? c : ' ');
                }
            }
        });
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(); doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 750);
                stream.showText(preview.toString().replaceAll("\\s+", " ").trim());
                stream.endText();
            }
            doc.save(out);
        }
    }
}
