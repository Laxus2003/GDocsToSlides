package com.myproject.gdocs2slides;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.*;
import com.myproject.gdocs2slides.model.ContentElement;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SlidesWriter {

    // Define the maximum words per slide
    private static final int MAX_WORDS_PER_SLIDE = 600;

    // Font size thresholds
    private static final int TITLE_LENGTH_THRESHOLD = 50;
    private static final double TITLE_FONT_SIZE_DEFAULT = 32.0;
    private static final double TITLE_FONT_SIZE_SMALL = 24.0;
    private static final double BODY_FONT_SIZE_DEFAULT = 18.0;
    private static final double BODY_FONT_SIZE_MEDIUM = 14.0;
    private static final double BODY_FONT_SIZE_SMALL = 12.0;

    // Method to download an image from a URL using authenticated credentials
    private static byte[] downloadImage(String imageUrl, HttpTransport httpTransport, Slides slidesService) throws IOException {
        HttpRequestFactory requestFactory = httpTransport.createRequestFactory(new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                slidesService.getRequestFactory().getInitializer().initialize(request);
            }
        });

        HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(imageUrl));
        HttpResponse response = request.execute();
        System.out.println("Downloaded image from: " + imageUrl + ", size: " + response.getContent().available() + " bytes");
        return response.getContent().readAllBytes();
    }

    public static String convertToSlides(Slides slidesService, String title, List<ContentElement> contentElements) throws IOException {
        if (contentElements == null || contentElements.isEmpty()) {
            throw new IllegalArgumentException("Content elements cannot be null or empty");
        }

        // Add timestamp to title 
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String timestamp = sdf.format(new Date());
        String fullTitle = title + " - " + timestamp;
        Presentation presentation = slidesService.presentations().create(new Presentation().setTitle(fullTitle)).execute();
        String presentationId = presentation.getPresentationId();

        List<Request> requests = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        String currentSlideId = null;
        String titlePlaceholderId = null;
        String bodyPlaceholderId = null;
        String lastSectionTitle = "";

        for (ContentElement element : contentElements) {
            if (element.getType() == ContentElement.ElementType.SECTION_TITLE) {
                lastSectionTitle = element.getText();
                // Create a new slide for section title
                currentSlideId = generateUniqueId("slide_", usedIds);
                requests.add(new Request()
                    .setCreateSlide(new CreateSlideRequest()
                        .setObjectId(currentSlideId)
                        .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("TITLE_AND_BODY"))));

                // Execute the slide creation
                slidesService.presentations()
                    .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                    .execute();
                requests.clear();

                // Retrieve placeholder IDs
                Presentation updatedPresentation = slidesService.presentations().get(presentationId).execute();
                for (Page slide : updatedPresentation.getSlides()) {
                    if (slide.getObjectId().equals(currentSlideId)) {
                        for (PageElement el : slide.getPageElements()) {
                            if (el.getShape() != null && el.getShape().getPlaceholder() != null) {
                                String type = el.getShape().getPlaceholder().getType();
                                if ("TITLE".equals(type) || "CENTERED_TITLE".equals(type)) {
                                    titlePlaceholderId = el.getObjectId();
                                } else if ("BODY".equals(type)) {
                                    bodyPlaceholderId = el.getObjectId();
                                }
                            }
                        }
                    }
                }

                // Add the section title
                if (titlePlaceholderId != null) {
                    String titleText = element.getText();
                    requests.add(new Request()
                        .setInsertText(new InsertTextRequest()
                            .setObjectId(titlePlaceholderId)
                            .setInsertionIndex(0)
                            .setText(titleText)));
                    // Adjust title font size based on length
                    double titleFontSize = titleText.length() > TITLE_LENGTH_THRESHOLD ? TITLE_FONT_SIZE_SMALL : TITLE_FONT_SIZE_DEFAULT;
                    requests.add(setFontSizeRequest(titlePlaceholderId, titleFontSize));
                }
                // Execute title insertion
                slidesService.presentations()
                    .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                    .execute();
                requests.clear();
            } else if (element.getType() == ContentElement.ElementType.PARAGRAPH) {
                String[] words = element.getText().split("\\s+");
                int wordCount = words.length;
                int startIndex = 0;

                while (startIndex < wordCount) {
                    int endIndex = Math.min(startIndex + MAX_WORDS_PER_SLIDE, wordCount);
                    StringBuilder slideTextBuilder = new StringBuilder();
                    for (int i = startIndex; i < endIndex; i++) {
                        slideTextBuilder.append(words[i]).append(" ");
                    }
                    String slideText = slideTextBuilder.toString().trim();

                    // Create a new slide for the paragraph chunk
                    currentSlideId = generateUniqueId("slide_", usedIds);
                    requests.add(new Request()
                        .setCreateSlide(new CreateSlideRequest()
                            .setObjectId(currentSlideId)
                            .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("TITLE_AND_BODY"))));

                    // Execute the slide creation
                    slidesService.presentations()
                        .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                        .execute();
                    requests.clear();

                    // Retrieve placeholder IDs
                    Presentation updatedPresentation = slidesService.presentations().get(presentationId).execute();
                    for (Page slide : updatedPresentation.getSlides()) {
                        if (slide.getObjectId().equals(currentSlideId)) {
                            for (PageElement el : slide.getPageElements()) {
                                if (el.getShape() != null && el.getShape().getPlaceholder() != null) {
                                    String type = el.getShape().getPlaceholder().getType();
                                    if ("TITLE".equals(type) || "CENTERED_TITLE".equals(type)) {
                                        titlePlaceholderId = el.getObjectId();
                                    } else if ("BODY".equals(type)) {
                                        bodyPlaceholderId = el.getObjectId();
                                    }
                                }
                            }
                        }
                    }

                    // Add the title (with "Continued" if not the first chunk)
                    String titleText = startIndex == 0 ? lastSectionTitle : "(Continued) " + lastSectionTitle;
                    if (titlePlaceholderId != null) {
                        requests.add(new Request()
                            .setInsertText(new InsertTextRequest()
                                .setObjectId(titlePlaceholderId)
                                .setInsertionIndex(0)
                                .setText(titleText)));
                        // Adjust title font size based on length
                        double titleFontSize = titleText.length() > TITLE_LENGTH_THRESHOLD ? TITLE_FONT_SIZE_SMALL : TITLE_FONT_SIZE_DEFAULT;
                        requests.add(setFontSizeRequest(titlePlaceholderId, titleFontSize));
                    }

                    // Add the paragraph text
                    if (bodyPlaceholderId != null) {
                        requests.add(new Request()
                            .setInsertText(new InsertTextRequest()
                                .setObjectId(bodyPlaceholderId)
                                .setInsertionIndex(0)
                                .setText(slideText)));
                        // Set font size for body text
                        double bodyFontSize = slideText.length() > 800 ? BODY_FONT_SIZE_SMALL :
                                             slideText.length() > 500 ? BODY_FONT_SIZE_MEDIUM : BODY_FONT_SIZE_DEFAULT;
                        requests.add(setFontSizeRequest(bodyPlaceholderId, bodyFontSize));
                    }

                    // Execute the requests for this slide
                    slidesService.presentations()
                        .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                        .execute();
                    requests.clear();

                    // Update for the next chunk
                    startIndex = endIndex;
                }
            } else if (element.getType() == ContentElement.ElementType.TABLE) {
                // Create a new slide for the table
                currentSlideId = generateUniqueId("slide_", usedIds);
                requests.add(new Request()
                    .setCreateSlide(new CreateSlideRequest()
                        .setObjectId(currentSlideId)
                        .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("BLANK"))));

                // Execute the slide creation
                slidesService.presentations()
                    .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                    .execute();
                requests.clear();

                // Add the table to the slide
                List<List<String>> tableData = element.getTableData();
                if (tableData != null && !tableData.isEmpty()) {
                    int rows = tableData.size();
                    int cols = tableData.get(0).size();
                    String tableId = generateUniqueId("table_", usedIds);
                    requests.add(new Request()
                        .setCreateTable(new CreateTableRequest()
                            .setObjectId(tableId)
                            .setElementProperties(new PageElementProperties()
                                .setPageObjectId(currentSlideId))
                            .setRows(rows)
                            .setColumns(cols)));

                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            requests.add(new Request()
                                .setInsertText(new InsertTextRequest()
                                    .setObjectId(tableId)
                                    .setCellLocation(new TableCellLocation().setRowIndex(r).setColumnIndex(c))
                                    .setText(tableData.get(r).get(c))));
                            // Adjust font size for table cells
                            requests.add(new Request()
                                .setUpdateTextStyle(new UpdateTextStyleRequest()
                                    .setObjectId(tableId)
                                    .setCellLocation(new TableCellLocation().setRowIndex(r).setColumnIndex(c))
                                    .setTextRange(new Range().setType("ALL"))
                                    .setStyle(new TextStyle()
                                        .setFontSize(new Dimension().setMagnitude(BODY_FONT_SIZE_SMALL).setUnit("PT")))
                                    .setFields("fontSize")));
                        }
                    }
                }

                // Execute the requests for this slide
                slidesService.presentations()
                    .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                    .execute();
                requests.clear();
            } else if (element.getType() == ContentElement.ElementType.IMAGE) {
                // Create a new slide for the image
                currentSlideId = generateUniqueId("slide_", usedIds);
                requests.add(new Request()
                    .setCreateSlide(new CreateSlideRequest()
                        .setObjectId(currentSlideId)
                        .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("BLANK"))));

                // Execute the slide creation
                slidesService.presentations()
                    .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                    .execute();
                requests.clear();

                // Add the image to the slide
                String imageUrl = element.getImageUrl();
                System.out.println("Processing image: " + imageUrl);
                try {
                    byte[] imageData = downloadImage(imageUrl, new NetHttpTransport(), slidesService);
                    String imageId = generateUniqueId("image_", usedIds);
                    requests.add(new Request()
                        .setCreateImage(new CreateImageRequest()
                            .setObjectId(imageId)
                            .setUrl(imageUrl)
                            .setElementProperties(new PageElementProperties()
                                .setPageObjectId(currentSlideId)
                                .setTransform(new AffineTransform()
                                    .setScaleX(1.0)
                                    .setScaleY(1.0)
                                    .setTranslateX(100.0)
                                    .setTranslateY(100.0)
                                    .setUnit("PT")))));
                    System.out.println("Image inserted on its own slide: " + currentSlideId);
                } catch (IOException e) {
                    System.err.println("Failed to download or insert image: " + imageUrl);
                    e.printStackTrace();
                }

                // Execute the requests for this slide
                slidesService.presentations()
                    .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                    .execute();
                requests.clear();
            }
        }

        String presentationUrl = "https://docs.google.com/presentation/d/" + presentationId + "/edit";
        System.out.println("Created presentation: " + presentationUrl);
        return presentationUrl;
    }

    private static Request setFontSizeRequest(String objectId, double sizePt) {
        return new Request()
            .setUpdateTextStyle(new UpdateTextStyleRequest()
                .setObjectId(objectId)
                .setTextRange(new Range().setType("ALL"))
                .setStyle(new TextStyle()
                    .setFontSize(new Dimension().setMagnitude(sizePt).setUnit("PT")))
                .setFields("fontSize"));
    }

    private static String generateUniqueId(String prefix, Set<String> usedIds) {
        String id;
        do {
            id = prefix + UUID.randomUUID().toString();
        } while (usedIds.contains(id));
        usedIds.add(id);
        return id;
    }

    public static String convert(String docId) throws Exception {
        Docs docsService = GoogleServiceUtil.getDocsService();
        Slides slidesService = GoogleServiceUtil.getSlidesService();

        List<ContentElement> content = DocsReader.extractContent(docsService, docId);
        String title = "Converted Google Doc";
        return convertToSlides(slidesService, title, content);
    }
}
