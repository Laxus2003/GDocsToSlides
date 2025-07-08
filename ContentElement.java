package com.myproject.gdocs2slides.model;

import java.util.ArrayList;
import java.util.List;

public class ContentElement {
    public enum ElementType {
        SECTION_TITLE,
        DOCUMENT_TITLE,
        HEADING_1,
        HEADING_2,
        HEADING_3,
        PARAGRAPH,
        TABLE,
        IMAGE
    }

    private ElementType type;
    private String text;
    private String imageUrl;
    private int sectionLevel;
    private List<List<String>> tableData;
    private double xPosition; // Coordonnée X en points
    private double yPosition; // Coordonnée Y en points
    private double width;     // Largeur en points
    private double height;    // Hauteur en points

    /* Construit un élément de contenu avec le type spécifié.*/
    public ContentElement(ElementType type) {
        // Initialisation des attributs avec des valeurs par défaut
        this.type = type;
        this.tableData = new ArrayList<>();
        this.xPosition = 0.0;
        this.yPosition = 0.0;
        this.width = 0.0;
        this.height = 0.0;
    }

    /* Construit un élément de contenu avec le type et le texte spécifiés.*/
    public ContentElement(ElementType type, String text) {
        // Appel du constructeur de base pour initialiser les attributs par défaut
        this(type);
        // Définition du texte de l'élément
        this.text = text;
    }

    /* Construit un élément de contenu avec toutes les propriétés spécifiées.*/
    public ContentElement(ElementType type, String text, String imageUrl, double xPosition, double yPosition, double width, double height) {
        // Appel du constructeur de base pour initialiser les attributs par défaut
        this(type);
        // Définition des propriétés spécifiques de l'élément
        this.text = text;
        this.imageUrl = imageUrl;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.width = width;
        this.height = height;
    }
    /*getters and setters */

    /* Retourne le type de l'élément de contenu.*/
    public ElementType getType() {
        return type;
    }

    /* Définit le type de l'élément de contenu.*/
    public void setType(ElementType type) {
        this.type = type;
    }

    /* Retourne le texte de l'élément de contenu.*/
    public String getText() {
        return text;
    }

    /* Définit le texte de l'élément de contenu.*/
    public void setText(String text) {
        this.text = text;
    }

    /* Retourne l'URL de l'image associée à l'élément.*/
    public String getImageUrl() {
        return imageUrl;
    }

    /* Définit l'URL de l'image associée à l'élément.*/
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /* Retourne le niveau de section de l'élément.*/
    public int getSectionLevel() {
        return sectionLevel;
    }

    /* Définit le niveau de section de l'élément.*/
    public void setSectionLevel(int sectionLevel) {
        this.sectionLevel = sectionLevel;
    }

    /* Retourne les données du tableau associées à l'élément.*/
    public List<List<String>> getTableData() {
        return tableData;
    }

    /* Définit les données du tableau associées à l'élément.*/
    public void setTableData(List<List<String>> tableData) {
        this.tableData = tableData;
    }

    /* Ajoute une ligne de données au tableau de l'élément.*/
    public void addTableRow(List<String> row) {
        // Ajout de la ligne spécifiée à la liste des données du tableau
        this.tableData.add(row);
    }

    /* Retourne le nombre de lignes dans le tableau de l'élément.*/
    public int getRows() {
        // Retourne la taille de la liste des données du tableau
        return tableData.size();
    }

    /* Retourne le nombre de colonnes dans le tableau de l'élément.*/
    public int getColumns() {
        // Retourne la taille de la première ligne si le tableau n'est pas vide, sinon 0
        return tableData.isEmpty() ? 0 : tableData.get(0).size();
    }

    /* Retourne la position X de l'élément en points.*/
    public double getXPosition() {
        return xPosition;
    }

    /* Définit la position X de l'élément en points.*/
    public void setXPosition(double xPosition) {
        this.xPosition = xPosition;
    }

    /* Retourne la position Y de l'élément en points.*/
    public double getYPosition() {
        return yPosition;
    }

    /* Définit la position Y de l'élément en points.*/
    public void setYPosition(double yPosition) {
        this.yPosition = yPosition;
    }

    /* Retourne la largeur de l'élément en points.*/
    public double getWidth() {
        return width;
    }

    /* Définit la largeur de l'élément en points.*/
    public void setWidth(double width) {
        this.width = width;
    }

    /* Retourne la hauteur de l'élément en points.*/
    public double getHeight() {
        return height;
    }

    /* Définit la hauteur de l'élément en points.*/
    public void setHeight(double height) {
        this.height = height;
    }
}
