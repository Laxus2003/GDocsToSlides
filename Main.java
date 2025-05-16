package com.myproject.gdocs2slides;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.slides.v1.Slides;
import com.myproject.gdocs2slides.model.ContentElement;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            // Initialize services
            Docs docsService = GoogleServiceUtil.getDocsService();
            Slides slidesService = GoogleServiceUtil.getSlidesService();
            
            // Test document ID - Replace with your actual ID
            String docId = "1Gvd71U6xRidFl5JwhmAPlEHNldKAfdV8s-zcOlg4tC4";
            
            System.out.println("⏳ Starting document analysis for ID: " + docId);
            
            // Process document
            List<ContentElement> content = DocsReader.extractContent(docsService, docId);
            
            // Convert to slides
            String presentationUrl = SlidesWriter.convertToSlides(slidesService, "Converted Google Doc", content);
            
            // Print results
            System.out.println("\n📄 Document Structure Breakdown:");
            int currentSection = -1;
            int elementCount = 0;
            
            for (ContentElement element : content) {
                if (element.getType() == ContentElement.ElementType.SECTION_TITLE) {
                    currentSection = element.getSectionLevel();
                    System.out.printf("\n💠 SECTION %d: %s\n", 
                                   currentSection + 1, 
                                   element.getText());
                } else {
                    System.out.printf("  │ %03d. %s\n", 
                                    ++elementCount, 
                                    formatElement(element));
                    
                    // Show table details if present
                    if (element.getType() == ContentElement.ElementType.TABLE) {
                        System.out.println("  │     Table Content:");
                        for (List<String> row : element.getTableData()) {
                            System.out.println("  │     - " + String.join(" | ", row));
                        }
                    }
                }
            }
            
            System.out.println("\n✅ Analysis complete! Found:");
            System.out.println("   - Total sections: " + (currentSection + 1));
            System.out.println("   - Total content elements: " + elementCount);
            
        } catch (Exception e) {
            System.err.println("\n❌ Error processing document:");
            e.printStackTrace();
            System.err.println("\nTroubleshooting Tips:");
            System.err.println("1. Verify your Google Docs ID is correct");
            System.err.println("2. Check credentials.json exists in resources");
            System.exit(1);
        }
    }

    private static String formatElement(ContentElement element) {
        switch (element.getType()) {
            case IMAGE:
                return "🖼️ Image (ID: " + element.getImageUrl() + ")";
            case HEADING_1:
                return "📌 " + element.getText();
            case HEADING_2:
                return "  ▪️ " + element.getText();
            case HEADING_3:
                return "    ◦ " + element.getText();
            case TABLE:
                return "📊 Table (" + element.getRows() + "x" + element.getColumns() + ")";
            default:
                return element.getText();
        }
    }
}