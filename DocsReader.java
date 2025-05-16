package com.myproject.gdocs2slides;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.*;
import com.myproject.gdocs2slides.model.ContentElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocsReader {
    // Pattern to match "Onglet X", "Section X", "Chapitre X", "Part X", "Page X"
    private static final Pattern SECTION_PATTERN = Pattern.compile("(?i)(Onglet|Section|Chapitre|Part|Page)\\s+(\\d+)");
    private static int currentSectionIndex = -1;
    private static ContentElement currentSection = null;

    public static List<ContentElement> extractContent(Docs docsService, String documentId) throws IOException {
        List<ContentElement> elements = new ArrayList<>();

        // Fetch the document
        Document document;
        try {
            document = docsService.documents().get(documentId).execute();
        } catch (IOException e) {
            throw new IOException("Failed to fetch document with ID " + documentId + ": " + e.getMessage(), e);
        }

        // Log the raw document for debugging
        System.out.println("Raw API response - Document: " + (document != null ? document.toString() : "null"));

        // Check if document or body is null
        if (document == null) {
            throw new IOException("Document is null. Verify the document ID: " + documentId);
        }
        if (document.getBody() == null) {
            throw new IOException("Document body is null for ID: " + documentId + ". The document might be empty, inaccessible, or corrupted.");
        }

        Map<String, InlineObject> inlineObjects = document.getInlineObjects();
        List<StructuralElement> bodyElements = document.getBody().getContent();

        System.out.println("Processing " + bodyElements.size() + " structural elements");

        for (int i = 0; i < bodyElements.size(); i++) {
            StructuralElement element = bodyElements.get(i);
            try {
                // Log element type for debugging
                System.out.println("Element " + i + ": Paragraph=" + (element.getParagraph() != null) +
                                   ", Table=" + (element.getTable() != null) +
                                   ", SectionBreak=" + (element.getSectionBreak() != null));

                // Skip completely empty elements
                if (element.getParagraph() == null && element.getTable() == null && element.getSectionBreak() == null) {
                    System.out.println("Element " + i + ": Skipping empty element");
                    continue;
                }

                // Handle section breaks
                if (element.getSectionBreak() != null) {
                    System.out.println("Element " + i + ": Encountered section break");
                    finalizeCurrentSection(elements);
                    // Create a new section if the next element isn't a section title
                    if (i + 1 < bodyElements.size()) {
                        StructuralElement nextElement = bodyElements.get(i + 1);
                        if (nextElement.getParagraph() == null || !isNewSection(nextElement)) {
                            currentSectionIndex++;
                            currentSection = new ContentElement(ContentElement.ElementType.SECTION_TITLE);
                            currentSection.setText("Section " + (currentSectionIndex + 1));
                            currentSection.setSectionLevel(currentSectionIndex);
                            System.out.println("Created new section after break: " + currentSection.getText());
                        }
                    }
                    continue;
                }

                // Section detection
                if (element.getParagraph() != null && isNewSection(element)) {
                    finalizeCurrentSection(elements);
                    createNewSection(element, elements);
                } else {
                    // Process non-section content
                    processContent(element, inlineObjects, elements);
                }

            } catch (Exception e) {
                System.err.println("Error processing element " + i + ": " + e.getMessage());
            }
        }

        // Finalize the last section
        finalizeCurrentSection(elements);

        // Create default section if no sections were detected
        if (elements.isEmpty()) {
            createDefaultSection(elements);
        }

        System.out.println("Extracted " + elements.size() + " content elements.");
        return elements;
    }

    private static boolean isNewSection(StructuralElement element) {
        if (element.getParagraph() == null) return false;
        String text = extractText(element.getParagraph()).trim();
        boolean isSection = SECTION_PATTERN.matcher(text).find();
        System.out.println("Checking section: '" + text + "' -> " + (isSection ? "Section detected" : "Not a section"));
        return isSection;
    }

    private static void createNewSection(StructuralElement element, List<ContentElement> elements) {
        currentSectionIndex++;
        currentSection = new ContentElement(ContentElement.ElementType.SECTION_TITLE);
        
        String text = extractText(element.getParagraph()).trim();
        Matcher matcher = SECTION_PATTERN.matcher(text);
        if (matcher.find()) {
            currentSection.setText(matcher.group(1) + " " + matcher.group(2));
        } else {
            currentSection.setText("Section " + (currentSectionIndex + 1));
        }
        
        currentSection.setSectionLevel(currentSectionIndex);
        System.out.println("Created new section: " + currentSection.getText());
    }

    private static void finalizeCurrentSection(List<ContentElement> elements) {
        if (currentSection != null) {
            elements.add(currentSection);
            System.out.println("Finalized section: " + currentSection.getText());
            currentSection = null; // Reset for the next section
        }
    }

    private static void createDefaultSection(List<ContentElement> elements) {
        currentSection = new ContentElement(ContentElement.ElementType.SECTION_TITLE);
        currentSection.setText("Main Content");
        currentSection.setSectionLevel(0);
        elements.add(currentSection);
        System.out.println("Created default section: Main Content");
    }

    private static void processContent(StructuralElement element,
                                      Map<String, InlineObject> inlineObjects,
                                      List<ContentElement> elements) {
        if (element.getParagraph() != null) {
            processParagraph(element.getParagraph(), inlineObjects, elements);
        } else if (element.getTable() != null) {
            processTable(element.getTable(), elements);
        }
    }

    private static void processParagraph(Paragraph paragraph, 
                                        Map<String, InlineObject> inlineObjects,
                                        List<ContentElement> elements) {
        String text = extractText(paragraph).trim();
        System.out.println("Processing paragraph with raw text: '" + text + "'");
        if (!text.isEmpty()) {
            ContentElement.ElementType type = determineParagraphType(paragraph);
            ContentElement element = new ContentElement(type, text);
            element.setSectionLevel(currentSectionIndex);
            elements.add(element);
            System.out.println("Added paragraph: " + text);
        }

        for (ParagraphElement pe : paragraph.getElements()) {
            if (pe.getInlineObjectElement() != null) {
                String objectId = pe.getInlineObjectElement().getInlineObjectId();
                processImage(objectId, inlineObjects, elements);
            }
        }
    }

    private static String extractText(Paragraph paragraph) {
        StringBuilder text = new StringBuilder();
        for (ParagraphElement pe : paragraph.getElements()) {
            if (pe.getTextRun() != null && pe.getTextRun().getContent() != null) {
                String content = pe.getTextRun().getContent();
                text.append(content);
            }
        }
        return text.toString();
    }

    private static ContentElement.ElementType determineParagraphType(Paragraph paragraph) {
        if (paragraph.getParagraphStyle() == null) 
            return ContentElement.ElementType.PARAGRAPH;
        
        String style = paragraph.getParagraphStyle().getNamedStyleType();
        return switch (style != null ? style : "") {
            case "HEADING_1" -> ContentElement.ElementType.HEADING_1;
            case "HEADING_2" -> ContentElement.ElementType.HEADING_2;
            case "HEADING_3" -> ContentElement.ElementType.HEADING_3;
            case "TITLE" -> ContentElement.ElementType.DOCUMENT_TITLE;
            default -> ContentElement.ElementType.PARAGRAPH;
        };
    }

    private static void processTable(Table table, List<ContentElement> elements) {
        ContentElement tableElement = new ContentElement(ContentElement.ElementType.TABLE);
        tableElement.setSectionLevel(currentSectionIndex);

        for (TableRow row : table.getTableRows()) {
            List<String> rowData = new ArrayList<>();
            for (TableCell cell : row.getTableCells()) {
                rowData.add(extractCellContent(cell));
            }
            tableElement.addTableRow(rowData);
        }
        
        elements.add(tableElement);
        System.out.println("Added table: " + tableElement.getRows() + "x" + tableElement.getColumns());
    }

    private static String extractCellContent(TableCell cell) {
        StringBuilder content = new StringBuilder();
        for (StructuralElement se : cell.getContent()) {
            if (se.getParagraph() != null) {
                content.append(extractText(se.getParagraph())).append(" | ");
            }
        }
        return content.toString().replaceAll(" \\| $", "");
    }

    private static void processImage(String inlineObjectId,
                                    Map<String, InlineObject> inlineObjects,
                                    List<ContentElement> elements) {
        try {
            InlineObject inlineObject = inlineObjects.get(inlineObjectId);
            if (inlineObject == null) {
                System.err.println("Inline object not found: " + inlineObjectId);
                return;
            }

            ContentElement imageElement = new ContentElement(ContentElement.ElementType.IMAGE);
            imageElement.setImageUrl(inlineObjectId);
            imageElement.setSectionLevel(currentSectionIndex);
            elements.add(imageElement);
            System.out.println("Added image: ID=" + inlineObjectId);
        } catch (Exception e) {
            System.err.println("Error processing image " + inlineObjectId + ": " + e.getMessage());
        }
    }
}