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

import java.util.Arrays;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/* La classe SlidesWriter convertit une liste d'éléments de contenu en une présentation Google Slides,
 *  en créant des diapositives avec du texte, des images et des tableaux selon des limites de formatage prédéfinies.
 */
public class SlidesWriter {

    private static final int MAX_LINES_PER_SLIDE = 8;
    private static final int MAX_WORDS_PER_SLIDE = 300;
    private static final double TITLE_FONT_SIZE = 32.0;
    private static final double BODY_FONT_SIZE_DEFAULT = 18.0;
    private static final double BODY_FONT_SIZE_MEDIUM = 14.0;
    private static final double BODY_FONT_SIZE_SMALL = 12.0;

    /*Télécharge une image à partir de l'URL spécifiée en utilisant le transport HTTP et le service Slides.*/
    private static byte[] downloadImage(String imageUrl, HttpTransport httpTransport, Slides slidesService) 
            throws IOException {
        // Création d'une usine de requêtes HTTP pour initialiser les requêtes
        HttpRequestFactory requestFactory = httpTransport.createRequestFactory(new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                slidesService.getRequestFactory().getInitializer().initialize(request);
            }
        });
        // Construction et exécution de la requête GET pour télécharger l'image
        HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(imageUrl));
        HttpResponse response = request.execute();
        System.out.println("Downloaded image from: " + imageUrl + ", size: " + response.getContent().available() 
                +" bytes");
        // Lecture et retour des données de l'image sous forme de tableau de bytes
        return response.getContent().readAllBytes();
    }

    /* Convertit une liste d'éléments de contenu en une présentation Google Slides.*/
    public static String convertToSlides(Slides slidesService, String title, List<ContentElement> contentElements) 
            throws IOException {
        // Vérification que la liste des éléments de contenu n'est pas nulle ou vide
        if (contentElements == null || contentElements.isEmpty()) {
            throw new IllegalArgumentException("Content elements cannot be null or empty");
        }

        // Création d'un horodatage pour le titre de la présentation
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String timestamp = sdf.format(new Date());
        String fullTitle = title + " - " + timestamp;

        // Création d'une nouvelle présentation avec le titre complet
        Presentation presentation = slidesService.presentations().create(new Presentation().setTitle(fullTitle)).execute();
        String presentationId = presentation.getPresentationId();

        // Initialisation des listes pour les requêtes et les identifiants utilisés
        List<Request> requests = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        String lastSectionTitle = "";
        List<ContentElement> currentParagraphs = new ArrayList<>();

        // Parcours des éléments de contenu pour organiser les slides
        for (int i = 0; i < contentElements.size(); i++) {
            ContentElement element = contentElements.get(i);
            if (element.getType() == ContentElement.ElementType.SECTION_TITLE) {
                // Si des paragraphes sont en attente, créer des slides pour eux
                if (!currentParagraphs.isEmpty()) {
                    createSlidesForParagraphs(slidesService, presentationId, usedIds, lastSectionTitle, currentParagraphs);
                    currentParagraphs.clear();
                }
                lastSectionTitle = element.getText();
            } else if (element.getType() == ContentElement.ElementType.PARAGRAPH) {
                // Gestion des paragraphes en fonction de leur longueur
                String[] words = element.getText().split("\\s+");
                int paraWordCount = words.length;
                if (paraWordCount > MAX_WORDS_PER_SLIDE) {
                    // Si le paragraphe est trop long, créer des slides séparés
                    if (!currentParagraphs.isEmpty()) {
                        createSlidesForParagraphs(slidesService, presentationId, usedIds, lastSectionTitle, currentParagraphs);
                        currentParagraphs.clear();
                    }
                    String paraText = element.getText();
                    String[] paraWords = paraText.split("\\s+");
                    StringBuilder chunk = new StringBuilder();
                    int chunkWordCount = 0;
                    // Division du paragraphe en morceaux respectant la limite de mots
                    for (String word : paraWords) {
                        if (chunkWordCount + 1 > MAX_WORDS_PER_SLIDE) {
                            createSlideWithText(slidesService, presentationId, usedIds, lastSectionTitle, chunk.toString());
                            chunk.setLength(0);
                            chunkWordCount = 0;
                        }
                        if (chunk.length() > 0) {
                            chunk.append(" ");
                        }
                        chunk.append(word);
                        chunkWordCount++;
                    }
                    // Création d'un slide pour le dernier morceau, s'il existe
                    if (chunk.length() > 0) {
                        createSlideWithText(slidesService, presentationId, usedIds, lastSectionTitle, chunk.toString());
                    }
                } else {
                    currentParagraphs.add(element);
                }
            } else {
                // Gestion des éléments non textuels (images, tableaux)
                if (!currentParagraphs.isEmpty()) {
                    createSlidesForParagraphs(slidesService, presentationId, usedIds, lastSectionTitle, currentParagraphs);
                    currentParagraphs.clear();
                }
                String currentSlideId = generateUniqueId("slide_", usedIds);
                // Création d'une nouvelle diapositive vierge
                requests.add(new Request()
                    .setCreateSlide(new CreateSlideRequest()
                        .setObjectId(currentSlideId)
                        .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("BLANK"))));
                slidesService.presentations()
                    .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                    .execute();
                requests.clear();

                if (element.getType() == ContentElement.ElementType.IMAGE) {
                    // Insertion d'une image dans la diapositive
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
                                        .setTranslateX(0.0)
                                        .setTranslateY(0.0)
                                        .setUnit("PT")))));
                        System.out.println("Image inserted on its own slide: " + currentSlideId);
                    } catch (IOException e) {
                        System.err.println("Failed to download or insert image: " + imageUrl);
                        e.printStackTrace();
                    }
                } else if (element.getType() == ContentElement.ElementType.TABLE) {
                    // Insertion d'un tableau dans la diapositive
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
                        // Remplissage du tableau avec les données
                        for (int r = 0; r < rows; r++) {
                            for (int c = 0; c < cols; c++) {
                                requests.add(new Request()
                                    .setInsertText(new InsertTextRequest()
                                        .setObjectId(tableId)
                                        .setCellLocation(new TableCellLocation().setRowIndex(r).setColumnIndex(c))
                                        .setText(tableData.get(r).get(c))));
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
                }
                // Exécution des requêtes pour insérer l'élément
                slidesService.presentations()
                    .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
                    .execute();
                requests.clear();
            }
        }

        // Création des slides pour les paragraphes restants
        if (!currentParagraphs.isEmpty()) {
            createSlidesForParagraphs(slidesService, presentationId, usedIds, lastSectionTitle, currentParagraphs);
        }

        // Génération de l'URL de la présentation
        String presentationUrl = "https://docs.google.com/presentation/d/" + presentationId + "/edit";
        System.out.println("Created presentation: " + presentationUrl);
        return presentationUrl;
    }

    /*Crée des diapositives pour une liste de paragraphes, en les divisant selon les limites de lignes et de mots.*/
    private static void createSlidesForParagraphs(Slides slidesService, String presentationId, Set<String> usedIds, String lastSectionTitle, List<ContentElement> paragraphs) throws IOException {
        // Concaténation de tous les paragraphes en un seul texte
        StringBuilder allText = new StringBuilder();
        for (ContentElement para : paragraphs) {
            if (allText.length() > 0) {
                allText.append("\n");
            }
            allText.append(para.getText());
        }
        // Division du texte en lignes
        String[] lines = allText.toString().split("\n");
        List<String> slideLines = new ArrayList<>();
        int slideWordCount = 0;

        // Parcours des lignes pour organiser les diapositives
        for (String line : lines) {
            String[] words = line.split("\\s+");
            int lineWordCount = words.length;
            // Si la diapositive est pleine, créer une nouvelle diapositive
            if (slideLines.size() >= MAX_LINES_PER_SLIDE || slideWordCount + lineWordCount > MAX_WORDS_PER_SLIDE) {
                String chunkText = String.join("\n", slideLines);
                createSlideWithText(slidesService, presentationId, usedIds, lastSectionTitle, chunkText);
                slideLines.clear();
                slideWordCount = 0;
            }
            slideLines.add(line);
            slideWordCount += lineWordCount;
        }
        // Création d'une diapositive pour les lignes restantes
        if (!slideLines.isEmpty()) {
            String chunkText = String.join("\n", slideLines);
            createSlideWithText(slidesService, presentationId, usedIds, lastSectionTitle, chunkText);
        }
    }

    /* Crée une diapositive unique avec un titre et un texte de corps, en ajustant la taille de la police selon la longueur du contenu.*/
    private static void createSlideWithText(Slides slidesService, String presentationId, Set<String> usedIds, String titleText, String bodyText) throws IOException {
        // Génération d'un identifiant unique pour la diapositive
        String slideId = generateUniqueId("slide_", usedIds);
        List<Request> requests = new ArrayList<>();
        // Création d'une diapositive avec une mise en page titre et corps
        requests.add(new Request()
            .setCreateSlide(new CreateSlideRequest()
                .setObjectId(slideId)
                .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("TITLE_AND_BODY"))));
        slidesService.presentations()
            .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
            .execute();
        requests.clear();

        // Recherche des identifiants des placeholders dans la diapositive
        Presentation presentation = slidesService.presentations().get(presentationId).execute();
        String titlePlaceholderId = null;
        String bodyPlaceholderId = null;
        for (Page slide : presentation.getSlides()) {
            if (slide.getObjectId().equals(slideId)) {
                for (PageElement el : slide.getPageElements()) {
                    if (el.getShape() != null && el.getShape().getPlaceholder() != null) {
                        String placeholderType = el.getShape().getPlaceholder().getType();
                        if ("TITLE".equals(placeholderType) || "CENTERED_TITLE".equals(placeholderType)) {
                            titlePlaceholderId = el.getObjectId();
                        } else if ("BODY".equals(placeholderType)) {
                            bodyPlaceholderId = el.getObjectId();
                        }
                    }
                }
            }
        }

        // Insertion du texte du titre, si disponible
        if (titleText != null && !titleText.isEmpty() && titlePlaceholderId != null) {
            requests.add(new Request()
                .setInsertText(new InsertTextRequest()
                    .setObjectId(titlePlaceholderId)
                    .setInsertionIndex(0)
                    .setText(titleText)));
            requests.add(setFontSizeRequest(titlePlaceholderId, TITLE_FONT_SIZE));
        }
        // Insertion du texte du corps, avec ajustement de la taille de police
        if (bodyText != null && !bodyText.isEmpty() && bodyPlaceholderId != null) {
            requests.add(new Request()
                .setInsertText(new InsertTextRequest()
                    .setObjectId(bodyPlaceholderId)
                    .setInsertionIndex(0)
                    .setText(bodyText)));
            double bodyFontSize = bodyText.length() > 800 ? BODY_FONT_SIZE_SMALL :
                                 bodyText.length() > 500 ? BODY_FONT_SIZE_MEDIUM : BODY_FONT_SIZE_DEFAULT;
            requests.add(setFontSizeRequest(bodyPlaceholderId, bodyFontSize));
        }

        // Exécution des requêtes pour insérer le texte
        slidesService.presentations()
            .batchUpdate(presentationId, new BatchUpdatePresentationRequest().setRequests(requests))
            .execute();
    }

    /* Crée une requête pour définir la taille de la police pour le texte dans un objet spécifié.*/
    private static Request setFontSizeRequest(String objectId, double sizePt) {
        return new Request()
            .setUpdateTextStyle(new UpdateTextStyleRequest()
                .setObjectId(objectId)
                .setTextRange(new Range().setType("ALL"))
                .setStyle(new TextStyle()
                    .setFontSize(new Dimension().setMagnitude(sizePt).setUnit("PT")))
                .setFields("fontSize"));
    }

    /*Génère un identifiant unique avec un préfixe, en s'assurant qu'il n'est pas déjà utilisé.*/
    private static String generateUniqueId(String prefix, Set<String> usedIds) {
        String id;
        do {
            id = prefix + UUID.randomUUID().toString();
        } while (usedIds.contains(id));
        usedIds.add(id);
        return id;
    }

    /*Convertit un Google Doc en une présentation Google Slides en utilisant un titre par défaut.*/
    public static String convert(String docId) throws Exception {
        // Initialisation des services Docs et Slides
        Docs docsService = GoogleServiceUtil.getDocsService();
        Slides slidesService = GoogleServiceUtil.getSlidesService();
        // Extraction du contenu du document
        List<ContentElement> content = DocsReader.extractContent(docsService, docId);
        String title = "Converted Google Doc";
        // Conversion en présentation
        return convertToSlides(slidesService, title, content);
    }
}
