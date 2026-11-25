package com.example.fileflow;
import org.springframework.stereotype.Component;
import javax.xml.XMLConstants;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.*;
import org.xml.sax.*;
import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class XmlValidator {
 private static final Logger log = LoggerFactory.getLogger(XmlValidator.class);
 private final Schema schema;

 public XmlValidator() throws SAXException {
  log.debug("Loading XML validation schema");
  try {
   SchemaFactory f=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
   f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
   f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");
   f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
   java.net.URL schemaUrl=getClass().getResource("/schema/document.xsd");
   if (schemaUrl == null) {
    SAXException e=new SAXException("XSD schema not found at /schema/document.xsd");
    log.error("Failed to load XML validation schema: {}", e.getMessage(), e);
    throw e;
   }
   schema=f.newSchema(schemaUrl);
   log.info("XML validation schema loaded");
  } catch (SAXException e) {
   log.error("Failed to load XML validation schema: {}", e.getMessage(), e);
   throw e;
  }
 }

 public void validate(InputStream in) throws Exception {
  if (in == null) {
   IllegalArgumentException e=new IllegalArgumentException("XML input is required");
   log.error("XML input validation failed: {}", e.getMessage());
   throw e;
  }
  try {
   log.info("Validating XML input");
   var factory=org.xml.sax.helpers.XMLReaderFactory.createXMLReader();
   factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
   factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
   factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
   factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
   Validator v=schema.newValidator();
   v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");
   v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
   v.validate(new SAXSource(factory,new InputSource(in)));
   log.debug("XML input validation completed");
  } catch (Exception e) {
   log.error("XML input validation failed: {}", e.getMessage(), e);
   throw e;
  }
 }
}
