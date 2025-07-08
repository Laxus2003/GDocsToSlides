package com.myproject.gdocs2slides;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.*;
import com.myproject.gdocs2slides.model.ContentElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DocsReader {

    /**
     * Extrait tous les éléments de contenu (texte, images, tableaux) à partir d'un document Google Docs.
     * @param docsService le service Google Docs initialisé
     * @param documentId l'identifiant du document à analyser
     * @return une liste d'éléments de contenu prêts à être utilisés
     */
    public static List<ContentElement> extractContent(Docs docsService, String documentId) throws IOException {
        List<ContentElement> allElements = new ArrayList<>();

        // Récupérer le document en incluant le contenu des onglets
        Document document = docsService.documents().get(documentId).setIncludeTabsContent(true).execute();
        System.out.println("Réponse brute de l'API - Document: " + (document != null ? document.toString() : "null"));

        if (document == null) {
            throw new IOException("Le document est nul. Vérifiez l'ID du document: " + documentId);
        }

        // Traiter chaque onglet principal
        List<Tab> tabs = document.getTabs();
        if (tabs != null && !tabs.isEmpty()) {
            for (Tab tab : tabs) {
                processTab(tab, 0, allElements); // Appel récursif sur chaque onglet
            }
        } else {
            System.out.println("Aucun onglet trouvé dans le document.");
        }

        // Créer une section par défaut si aucun élément n'est extrait
        if (allElements.isEmpty()) {
            createDefaultSection(allElements);
        }

        System.out.println("Extraction de " + allElements.size() + " éléments de contenu à travers tous les onglets.");
        return allElements;
    }

    /**
     * Traite un onglet donné et extrait récursivement le contenu qu'il contient (textes, images, sous-onglets).
     */
    private static void processTab(Tab tab, int level, List<ContentElement> elements) {
        // Ajouter un titre de section basé sur le titre de l'onglet
        String title = tab.getTabProperties().getTitle();
        ContentElement sectionElement = new ContentElement(ContentElement.ElementType.SECTION_TITLE);
        sectionElement.setText(title);
        sectionElement.setSectionLevel(level);
        elements.add(sectionElement);
        System.out.println("Titre de section ajouté au niveau " + level + ": " + title);

        // Récupérer les objets inline de cet onglet
        DocumentTab documentTab = tab.getDocumentTab();
        Map<String, InlineObject> tabInlineObjects = documentTab.getInlineObjects();
        if (tabInlineObjects == null) {
            System.out.println("Aucun objet inline dans l'onglet: " + title);
        }

        // Traiter le contenu de l'onglet (texte, images, tableaux)
        if (documentTab != null && documentTab.getBody() != null) {
            List<StructuralElement> bodyElements = documentTab.getBody().getContent();
            if (bodyElements != null) {
                double yPosition = 100.0; // Position Y initiale
                for (StructuralElement element : bodyElements) {
                    yPosition = processStructuralElement(element, tabInlineObjects, level + 1, elements, yPosition);
                }
            }
        }

        // Traiter les sous-onglets (onglets enfants)
        List<Tab> childTabs = tab.getChildTabs();
        if (childTabs != null && !childTabs.isEmpty()) {
            for (Tab childTab : childTabs) {
                processTab(childTab, level + 1, elements); // Appel récursif
            }
        }
    }

    /**
     * Traite un élément structurel de type paragraphe, tableau ou saut de section.
     */
    private static double processStructuralElement(StructuralElement element, Map<String, InlineObject> inlineObjects, int sectionLevel, List<ContentElement> elements, double yPosition) {
        // Vérifie et traite les différents types d'éléments structurels
        if (element.getParagraph() != null) {
            yPosition = processParagraph(element.getParagraph(), inlineObjects, sectionLevel, elements, yPosition);
        } else if (element.getTable() != null) {
            yPosition = processTable(element.getTable(), sectionLevel, elements, yPosition);
        } else if (element.getSectionBreak() != null) {
            System.out.println("Saut de section rencontré dans l'onglet au niveau " + sectionLevel);
        }
        return yPosition;
    }

    /**
     * Analyse un paragraphe, extrait son texte et traite les images inline.
     */
    private static double processParagraph(Paragraph paragraph, Map<String, InlineObject> inlineObjects, int sectionLevel, List<ContentElement> elements, double yPosition) {
        // Extraire le texte du paragraphe
        String text = extractText(paragraph).trim();
        if (!text.isEmpty()) {
            // Déterminer le type (titre, sous-titre, texte normal)
            ContentElement.ElementType type = determineParagraphType(paragraph);
            ContentElement element = new ContentElement(type, text);
            element.setSectionLevel(sectionLevel);
            elements.add(element);
            System.out.println("Paragraphe ajouté au niveau " + sectionLevel + ": " + text);
            yPosition += 20.0; // Ajustement de la hauteur pour espacement vertical
        }

        // Traiter les images inline (intégrées dans le paragraphe)
        for (ParagraphElement pe : paragraph.getElements()) {
            if (pe.getInlineObjectElement() != null) {
                String objectId = pe.getInlineObjectElement().getInlineObjectId();
                yPosition = processImage(objectId, inlineObjects, sectionLevel, elements, yPosition);
            }
        }
        return yPosition;
    }

    /**
     * Extrait les données d'un tableau ligne par ligne, cellule par cellule.
     */
    private static double processTable(Table table, int sectionLevel, List<ContentElement> elements, double yPosition) {
        // Créer un élément de contenu pour représenter le tableau
        ContentElement tableElement = new ContentElement(ContentElement.ElementType.TABLE);
        tableElement.setSectionLevel(sectionLevel);

        // Parcourir chaque ligne du tableau
        for (TableRow row : table.getTableRows()) {
            List<String> rowData = new ArrayList<>();
            for (TableCell cell : row.getTableCells()) {
                rowData.add(extractCellContent(cell)); // Ajouter contenu cellule à la ligne
            }
            tableElement.addTableRow(rowData);
        }

        elements.add(tableElement);
        System.out.println("Tableau ajouté au niveau " + sectionLevel + ": " + tableElement.getRows() + "x" + tableElement.getColumns());
        yPosition += tableElement.getRows() * 30.0; // Estimation de la hauteur verticale
        return yPosition;
    }

    /**
     * Extrait tout le texte d'un paragraphe en concaténant les segments.
     */
    private static String extractText(Paragraph paragraph) {
        StringBuilder text = new StringBuilder();
        for (ParagraphElement pe : paragraph.getElements()) {
            if (pe.getTextRun() != null && pe.getTextRun().getContent() != null) {
                text.append(pe.getTextRun().getContent());
            }
        }
        return text.toString();
    }

    /**
     * Détermine le type d'un paragraphe selon son style (titre, sous-titre, normal...)
     */
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

    /**
     * Extrait et concatène le texte contenu dans une cellule de tableau.
     */
    private static String extractCellContent(TableCell cell) {
        StringBuilder content = new StringBuilder();
        for (StructuralElement se : cell.getContent()) {
            if (se.getParagraph() != null) {
                content.append(extractText(se.getParagraph())).append(" | ");
            }
        }
        return content.toString().replaceAll(" \\| $", "");
    }

    /**
     * Traite un objet image inline et l'ajoute comme élément de contenu avec ses dimensions et sa position.
     */
    private static double processImage(String inlineObjectId, Map<String, InlineObject> inlineObjects, int sectionLevel, List<ContentElement> elements, double yPosition) {
        // Vérifier si la map des objets inline est valide
        if (inlineObjects == null) {
            System.err.println("Erreur : la map des objets inline est nulle. Image ignorée ID : " + inlineObjectId);
            return yPosition;
        }

        InlineObject inlineObject = inlineObjects.get(inlineObjectId);
        if (inlineObject == null) {
            System.err.println("Erreur : objet inline introuvable ID : " + inlineObjectId);
            return yPosition;
        }

        InlineObjectProperties inlineObjectProperties = inlineObject.getInlineObjectProperties();
        if (inlineObjectProperties == null || inlineObjectProperties.getEmbeddedObject() == null) {
            System.err.println("Erreur : aucun objet intégré trouvé ID : " + inlineObjectId);
            return yPosition;
        }

        EmbeddedObject embeddedObject = inlineObjectProperties.getEmbeddedObject();
        if (embeddedObject.getImageProperties() == null) {
            System.err.println("Erreur : aucune propriété d'image trouvée ID : " + inlineObjectId);
            return yPosition;
        }

        String imageUrl = embeddedObject.getImageProperties().getContentUri();
        Size size = embeddedObject.getSize();
        if (size == null) {
            System.err.println("Avertissement : taille de l'image non disponible ID : " + inlineObjectId);
            return yPosition;
        }

        // Convertir la taille en points
        double width = size.getWidth().getMagnitude() * 72.0 / 1_000_000.0;
        double height = size.getHeight().getMagnitude() * 72.0 / 1_000_000.0;
        double xPosition = 50.0; // Position horizontale fixe

        // Créer l'objet image avec position et taille
        ContentElement imageElement = new ContentElement(ContentElement.ElementType.IMAGE, null, imageUrl, xPosition, yPosition, width, height);
        imageElement.setSectionLevel(sectionLevel);
        elements.add(imageElement);
        System.out.println("Image ajoutée au niveau " + sectionLevel + ": URL=" + imageUrl + ", Position=(" + xPosition + ", " + yPosition + "), Taille=(" + width + ", " + height + ")");

        yPosition += height + 10.0; // Ajouter un espacement après l'image
        return yPosition;
    }

    /**
     * Ajoute une section par défaut si aucun autre contenu n'a été extrait du document.
     */
    private static void createDefaultSection(List<ContentElement> elements) {
        ContentElement defaultSection = new ContentElement(ContentElement.ElementType.SECTION_TITLE);
        defaultSection.setText("Contenu principal");
        defaultSection.setSectionLevel(0);
        elements.add(defaultSection);
        System.out.println("Section par défaut créée : Contenu principal");
    }
}
