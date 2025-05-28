package com.myproject.gdocs2slides;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.*;
import com.myproject.gdocs2slides.model.ContentElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DocsReader {

    public static List<ContentElement> extractContent(Docs docsService, String documentId) throws IOException {
        List<ContentElement> allElements = new ArrayList<>();

        // Fetch the document with all tabs content included
        Document document = docsService.documents().get(documentId).setIncludeTabsContent(true).execute();
        System.out.println("Raw API response - Document: " + (document != null ? document.toString() : "null"));

        if (document == null) {
            throw new IOException("Document is null. Verify the document ID: " + documentId);
        }

        // Process each top-level tab
        List<Tab> tabs = document.getTabs();
        if (tabs != null && !tabs.isEmpty()) {
            for (Tab tab : tabs) {
                processTab(tab, 0, allElements);
            }
        } else {
            System.out.println("No tabs found in the document.");
        }

        // Create a default section if no elements were extracted
        if (allElements.isEmpty()) {
            createDefaultSection(allElements);
        }

        System.out.println("Extracted " + allElements.size() + " content elements across all tabs.");
        return allElements;
    }

    private static void processTab(Tab tab, int level, List<ContentElement> elements) {
        // Create section title for this tab
        String title = tab.getTabProperties().getTitle();
        ContentElement sectionElement = new ContentElement(ContentElement.ElementType.SECTION_TITLE);
        sectionElement.setText(title);
        sectionElement.setSectionLevel(level);
        elements.add(sectionElement);
        System.out.println("Added section title at level " + level + ": " + title);

        // Get inlineObjects for this tab
        DocumentTab documentTab = tab.getDocumentTab();
        Map<String, InlineObject> tabInlineObjects = documentTab.getInlineObjects();
        if (tabInlineObjects == null) {
            System.out.println("No inline objects in tab: " + title);
        }

        // Process the content of this tab
        if (documentTab != null && documentTab.getBody() != null) {
            List<StructuralElement> bodyElements = documentTab.getBody().getContent();
            if (bodyElements != null) {
                double yPosition = 100.0; // Starting Y position for content
                for (StructuralElement element : bodyElements) {
                    yPosition = processStructuralElement(element, tabInlineObjects, level + 1, elements, yPosition);
                }
            }
        }

        // Process child tabs
        List<Tab> childTabs = tab.getChildTabs();
        if (childTabs != null && !childTabs.isEmpty()) {
            for (Tab childTab : childTabs) {
                processTab(childTab, level + 1, elements);
            }
        }
    }

    private static double processStructuralElement(StructuralElement element, Map<String, InlineObject> inlineObjects, int sectionLevel, List<ContentElement> elements, double yPosition) {
        if (element.getParagraph() != null) {
            yPosition = processParagraph(element.getParagraph(), inlineObjects, sectionLevel, elements, yPosition);
        } else if (element.getTable() != null) {
            yPosition = processTable(element.getTable(), sectionLevel, elements, yPosition);
        } else if (element.getSectionBreak() != null) {
            System.out.println("Encountered section break within tab at level " + sectionLevel);
            // Section breaks can be ignored for now or handled as needed
        }
        return yPosition;
    }

    private static double processParagraph(Paragraph paragraph, Map<String, InlineObject> inlineObjects, int sectionLevel, List<ContentElement> elements, double yPosition) {
        String text = extractText(paragraph).trim();
        if (!text.isEmpty()) {
            ContentElement.ElementType type = determineParagraphType(paragraph);
            ContentElement element = new ContentElement(type, text);
            element.setSectionLevel(sectionLevel);
            elements.add(element);
            System.out.println("Added paragraph at level " + sectionLevel + ": " + text);
            yPosition += 20.0; // Increment Y position for next element (approximation for text height)
        }

        // Process any inline images
        for (ParagraphElement pe : paragraph.getElements()) {
            if (pe.getInlineObjectElement() != null) {
                String objectId = pe.getInlineObjectElement().getInlineObjectId();
                yPosition = processImage(objectId, inlineObjects, sectionLevel, elements, yPosition);
            }
        }
        return yPosition;
    }

    private static double processTable(Table table, int sectionLevel, List<ContentElement> elements, double yPosition) {
        ContentElement tableElement = new ContentElement(ContentElement.ElementType.TABLE);
        tableElement.setSectionLevel(sectionLevel);

        for (TableRow row : table.getTableRows()) {
            List<String> rowData = new ArrayList<>();
            for (TableCell cell : row.getTableCells()) {
                rowData.add(extractCellContent(cell));
            }
            tableElement.addTableRow(rowData);
        }

        elements.add(tableElement);
        System.out.println("Added table at level " + sectionLevel + ": " + tableElement.getRows() + "x" + tableElement.getColumns());
        yPosition += tableElement.getRows() * 30.0; // Approximate height based on rows
        return yPosition;
    }

    private static String extractText(Paragraph paragraph) {
        StringBuilder text = new StringBuilder();
        for (ParagraphElement pe : paragraph.getElements()) {
            if (pe.getTextRun() != null && pe.getTextRun().getContent() != null) {
                text.append(pe.getTextRun().getContent());
            }
        }
        return text.toString();
    }

    private static ContentElement.ElementType determineParagraphType(Paragraph paragraph) {
        if (paragraph.getParagraphStyle() == null) {
            return ContentElement.ElementType.PARAGRAPH;
        }

        String style = paragraph.getParagraphStyle().getNamedStyleType();
        return switch (style != null ? style : "") {
            case "HEADING_1" -> ContentElement.ElementType.HEADING_1;
            case "HEADING_2" -> ContentElement.ElementType.HEADING_2;
            case "HEADING_3" -> ContentElement.ElementType.HEADING_3;
            case "TITLE" -> ContentElement.ElementType.DOCUMENT_TITLE;
            default -> ContentElement.ElementType.PARAGRAPH;
        };
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

    private static double processImage(String inlineObjectId, Map<String, InlineObject> inlineObjects, int sectionLevel, List<ContentElement> elements, double yPosition) {
        // Check if inlineObjects is null
        if (inlineObjects == null) {
            System.err.println("Error: inlineObjects map is null. Skipping image with ID: " + inlineObjectId);
            return yPosition;
        }

        // Check if the specific inline object exists
        InlineObject inlineObject = inlineObjects.get(inlineObjectId);
        if (inlineObject == null) {
            System.err.println("Error: Inline object not found for ID: " + inlineObjectId + ". Skipping.");
            return yPosition;
        }

        // Process the image if everything is valid
        InlineObjectProperties inlineObjectProperties = inlineObject.getInlineObjectProperties();
        if (inlineObjectProperties == null || inlineObjectProperties.getEmbeddedObject() == null) {
            System.err.println("Error: No embedded object found for inline object ID: " + inlineObjectId);
            return yPosition;
        }

        EmbeddedObject embeddedObject = inlineObjectProperties.getEmbeddedObject();
        if (embeddedObject.getImageProperties() == null) {
            System.err.println("Error: No image properties found for inline object ID: " + inlineObjectId);
            return yPosition;
        }

        String imageUrl = embeddedObject.getImageProperties().getContentUri();
        Size size = embeddedObject.getSize();
        if (size == null) {
            System.err.println("Warning: Image size not available for inline object ID: " + inlineObjectId);
            return yPosition;
        }

        // Convert size from micros to points (1 point = 72 pixels, 1 micro = 1/1,000,000 inches)
        // Approximation: 1 micro = 1/1,000,000 inches, 1 inch = 72 points, so 1 micro = 72/1,000,000 points
        double width = size.getWidth().getMagnitude() * 72.0 / 1_000_000.0;
        double height = size.getHeight().getMagnitude() * 72.0 / 1_000_000.0;

        // Estimate position: stack vertically for now, starting at x=50
        double xPosition = 50.0; // Fixed X position (adjust based on paragraph alignment if needed)
        // Use the passed yPosition to stack elements vertically

        ContentElement imageElement = new ContentElement(ContentElement.ElementType.IMAGE, null, imageUrl, xPosition, yPosition, width, height);
        imageElement.setSectionLevel(sectionLevel);
        elements.add(imageElement);
        System.out.println("Added image at level " + sectionLevel + ": URL=" + imageUrl + ", Position=(" + xPosition + ", " + yPosition + "), Size=(" + width + ", " + height + ")");

        // Increment yPosition for the next element
        yPosition += height + 10.0; // Add some padding
        return yPosition;
    }

    private static void createDefaultSection(List<ContentElement> elements) {
        ContentElement defaultSection = new ContentElement(ContentElement.ElementType.SECTION_TITLE);
        defaultSection.setText("Main Content");
        defaultSection.setSectionLevel(0);
        elements.add(defaultSection);
        System.out.println("Created default section: Main Content");
    }
}
