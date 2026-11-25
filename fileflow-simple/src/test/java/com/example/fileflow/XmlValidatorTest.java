package com.example.fileflow;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XmlValidatorTest {
    private final XmlValidator validator = new XmlValidator();

    XmlValidatorTest() throws SAXException {
    }

    @Test
    void acceptsXmlMatchingXsd() {
        String xml = "<?xml version=\"1.0\"?><document><title>Title</title><content>Content</content></document>";
        assertDoesNotThrow(() -> validator.validate(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsXmlMissingRequiredElement() {
        String xml = "<?xml version=\"1.0\"?><document><title>Title</title></document>";
        assertThrows(SAXException.class, () ->
                validator.validate(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsXmlWithDoctype() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE document [<!ELEMENT document ANY>]><document><title>Title</title><content>Content</content></document>";
        assertThrows(Exception.class, () ->
                validator.validate(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }
}
