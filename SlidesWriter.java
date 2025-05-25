package com.myproject.gdocs2slides;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.*;
import com.myproject.gdocs2slides.model.ContentElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SlidesWriter {

    // Method to download an image from a URL using authenticated credentials
    private static byte[] downloadImage(String imageUrl, HttpTransport httpTransport, Slides slidesService) throws IOException {
        HttpRequestFactory requestFactory = httpTransport.createRequestFactory(new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                slidesService.getRequestFactory().getInitializer().initialize(request);
            }
        });

        HttpRequest request = requestFactory.buildGetRequest(new com.google.api.client.http.GenericUrl(imageUrl));
        HttpResponse response = request.execute();
        System.out.println("Downloaded image from: " + imageUrl + ", size: " + response.getContent().available() + " bytes");
        return response.getContent().readAllBytes();
    }

    public static String convertToSlides(Slides slidesService, String title, List<ContentElement> contentElements) throws IOException {
        if (contentElements == null || contentElements.isEmpty()) {
            throw new IllegalArgumentException("Content elements cannot be null or empty");
        }

        // Create a new presentation
        Presentation presentation = new Presentation().setTitle(title);
        presentation = slidesService.presentations().create(presentation).execute();
        String presentationId = presentation.getPresentationId();

        List<com.google.api.services.slides.v1.model.Request> requests = new ArrayList<>();
        String currentSlideId = null;
        String titlePlaceholderId = null;
        String bodyPlaceholderId = null;

        for (ContentElement element : contentElements) {
            if (element.getType() == ContentElement.ElementType.SECTION_TITLE) {
                // Create a new slide for the section
                currentSlideId = "slide_" + UUID.randomUUID().toString();
                com.google.api.services.slides.v1.model.Request createSlideRequest = new com.google.api.services.slides.v1.model.Request()
                    .setCreateSlide(new CreateSlideRequest()
                        .setObjectId(currentSlideId)
                        .setSlideLayoutReference(new LayoutReference()
                            .setPredefinedLayout("TITLE_AND_BODY")));
                requests.add(createSlideRequest);

                // Execute the batch update to create the slide so we can get its placeholders
                if (!requests.isEmpty()) {
                    BatchUpdatePresentationRequest batchRequest = new BatchUpdatePresentationRequest().setRequests(requests);
                    slidesService.presentations().batchUpdate(presentationId, batchRequest).execute();
                    requests.clear();
                }

                // Fetch the slide to get the placeholder IDs
                Presentation updatedPresentation = slidesService.presentations().get(presentationId).execute();
                Page slide = null;
                for (Page page : updatedPresentation.getSlides()) {
                    if (page.getObjectId().equals(currentSlideId)) {
                        slide = page;
                        break;
                    }
                }

                if (slide != null) {
                    // Find the title and body placeholders
                    for (PageElement elementOnSlide : slide.getPageElements()) {
                        if (elementOnSlide.getShape() != null && elementOnSlide.getShape().getPlaceholder() != null) {
                            Placeholder placeholder = elementOnSlide.getShape().getPlaceholder();
                            if (placeholder.getType().equals("TITLE") || placeholder.getType().equals("CENTERED_TITLE")) {
                                titlePlaceholderId = elementOnSlide.getObjectId();
                            } else if (placeholder.getType().equals("BODY")) {
                                bodyPlaceholderId = elementOnSlide.getObjectId();
                            }
                        }
                    }
                }

                // Add section title to the placeholder
                if (titlePlaceholderId != null) {
                    requests.add(new com.google.api.services.slides.v1.model.Request()
                        .setInsertText(new InsertTextRequest()
                            .setObjectId(titlePlaceholderId)
                            .setInsertionIndex(0)
                            .setText(element.getText())));
                } else {
                    System.err.println("Warning: Title placeholder not found for slide " + currentSlideId);
                }
            } else if (currentSlideId != null && bodyPlaceholderId != null) {
                // Add content to the current slide's body placeholder
                if (element.getType() == ContentElement.ElementType.PARAGRAPH) {
                    requests.add(new com.google.api.services.slides.v1.model.Request()
                        .setInsertText(new InsertTextRequest()
                            .setObjectId(bodyPlaceholderId)
                            .setInsertionIndex(0)
                            .setText(element.getText() + "\n")));
                } else if (element.getType() == ContentElement.ElementType.TABLE) {
                    List<List<String>> tableData = element.getTableData();
                    if (tableData != null && !tableData.isEmpty()) {
                        int rows = tableData.size();
                        int cols = tableData.get(0).size();
                        String tableId = "table_" + UUID.randomUUID().toString();
                        requests.add(new com.google.api.services.slides.v1.model.Request()
                            .setCreateTable(new CreateTableRequest()
                                .setObjectId(tableId)
                                .setElementProperties(new PageElementProperties()
                                    .setPageObjectId(currentSlideId))
                                .setRows(rows)
                                .setColumns(cols)));

                        // Populate table cells
                        for (int r = 0; r < rows; r++) {
                            for (int c = 0; c < cols; c++) {
                                requests.add(new com.google.api.services.slides.v1.model.Request()
                                    .setInsertText(new InsertTextRequest()
                                        .setObjectId(tableId)
                                        .setCellLocation(new TableCellLocation().setRowIndex(r).setColumnIndex(c))
                                        .setText(tableData.get(r).get(c))));
                            }
                        }
                    }
                } else if (element.getType() == ContentElement.ElementType.IMAGE) {
                    String imageUrl = element.getImageUrl();
                    System.out.println("Processing image: " + imageUrl);
                    try {
                        // Download the image
                        byte[] imageData = downloadImage(imageUrl, new NetHttpTransport(), slidesService);

                        // Generate a unique image ID
                        String imageId = "image_" + UUID.randomUUID().toString();

                        // Insert the image into the current slide with transform
                        requests.add(new com.google.api.services.slides.v1.model.Request()
                            .setCreateImage(new CreateImageRequest()
                                .setObjectId(imageId)
                                .setUrl(imageUrl)
                                .setElementProperties(new PageElementProperties()
                                    .setPageObjectId(currentSlideId)
                                    .setTransform(new AffineTransform()
                                        .setScaleX(0.5) // Revert to working scale
                                        .setScaleY(0.5)
                                        .setTranslateX(100.0) // Revert to working position
                                        .setTranslateY(100.0)
                                        .setUnit("PT")))));
                        System.out.println("Image request added for slide " + currentSlideId);
                    } catch (IOException e) {
                        System.err.println("Failed to download or insert image: " + imageUrl);
                        e.printStackTrace();
                    }
                }
            }
        }

        // Execute batch update for remaining requests
        if (!requests.isEmpty()) {
            BatchUpdatePresentationRequest batchRequest = new BatchUpdatePresentationRequest().setRequests(requests);
            slidesService.presentations().batchUpdate(presentationId, batchRequest).execute();
        }

        String presentationUrl = "https://docs.google.com/presentation/d/" + presentationId + "/edit";
        System.out.println("Created presentation: " + presentationUrl);
        return presentationUrl;
    }

    public static String convert(String docId) throws Exception {
        // Initialize services
        Docs docsService = GoogleServiceUtil.getDocsService();
        Slides slidesService = GoogleServiceUtil.getSlidesService();

        // Extract content from the Google Doc
        List<ContentElement> content = DocsReader.extractContent(docsService, docId);

        // Convert the content to a Slides presentation
        String title = "Converted Google Doc";
        String presentationUrl = convertToSlides(slidesService, title, content);

        return presentationUrl;
    }
}
