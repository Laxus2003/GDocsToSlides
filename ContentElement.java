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
    private double xPosition; // X coordinate in points
    private double yPosition; // Y coordinate in points
    private double width;     // Width in points
    private double height;    // Height in points

    // Constructors
    public ContentElement(ElementType type) {
        this.type = type;
        this.tableData = new ArrayList<>();
        this.xPosition = 0.0;
        this.yPosition = 0.0;
        this.width = 0.0;
        this.height = 0.0;
    }

    public ContentElement(ElementType type, String text) {
        this(type);
        this.text = text;
    }

    public ContentElement(ElementType type, String text, String imageUrl, double xPosition, double yPosition, double width, double height) {
        this(type);
        this.text = text;
        this.imageUrl = imageUrl;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.width = width;
        this.height = height;
    }

    // Getters and Setters
    public ElementType getType() {
        return type;
    }

    public void setType(ElementType type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getSectionLevel() {
        return sectionLevel;
    }

    public void setSectionLevel(int sectionLevel) {
        this.sectionLevel = sectionLevel;
    }

    public List<List<String>> getTableData() {
        return tableData;
    }

    public void setTableData(List<List<String>> tableData) {
        this.tableData = tableData;
    }

    public void addTableRow(List<String> row) {
        this.tableData.add(row);
    }

    public int getRows() {
        return tableData.size();
    }

    public int getColumns() {
        return tableData.isEmpty() ? 0 : tableData.get(0).size();
    }

    public double getXPosition() {
        return xPosition;
    }

    public void setXPosition(double xPosition) {
        this.xPosition = xPosition;
    }

    public double getYPosition() {
        return yPosition;
    }

    public void setYPosition(double yPosition) {
        this.yPosition = yPosition;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }
}
