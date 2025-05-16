package com.myproject.gdocs2slides.model;

import java.util.ArrayList;
import java.util.List;

public class ContentElement {
    public enum ElementType {
        DOCUMENT_TITLE,
        SECTION_TITLE,
        PARAGRAPH,
        HEADING_1,
        HEADING_2,
        HEADING_3,
        IMAGE,
        HYPERLINK,
        BULLET_LIST,
        NUMBERED_LIST,
        TABLE,
        QUOTE,
        HORIZONTAL_RULE
    }

    // Core content properties
    private ElementType type;
    private String text;
    private String imageUrl; // Stores Google Docs inline object ID
    private String linkUrl;
    
    // Text formatting
    private boolean bold = false;
    private boolean italic = false;
    private boolean underline = false;
    private String fontFamily = "Arial";
    private int fontSize = 11;
    private String color = "#000000";
    
    // Document structure
    private int sectionLevel = 0;
    private int indentLevel = 0;
    
    // List/Table data
    private final List<String> listItems;
    private final List<List<String>> tableData;
    private int rows = 0;
    private int columns = 0;
    
    // Position tracking
    private int documentPosition = 0;

    // Constructors
    public ContentElement(ElementType type) {
        this.type = type;
        this.listItems = new ArrayList<>();
        this.tableData = new ArrayList<>();
    }

    public ContentElement(ElementType type, String text) {
        this(type);
        this.text = text != null ? text : "";
    }

    public ContentElement(ElementType type, String text, String url) {
        this(type, text);
        if (type == ElementType.IMAGE) {
            this.imageUrl = url;
        } else if (type == ElementType.HYPERLINK) {
            this.linkUrl = url;
        }
    }

    // Getters/Setters
    public ElementType getType() { return type; }
    public void setType(ElementType type) { this.type = type; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text != null ? text : ""; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { 
        this.imageUrl = imageUrl;
    }

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }

    // Section management
    public int getSectionLevel() { return sectionLevel; }
    public void setSectionLevel(int sectionLevel) { 
        this.sectionLevel = sectionLevel; 
    }

    // Table management
    public List<List<String>> getTableData() { return tableData; }
    public int getRows() { return rows; }
    public int getColumns() { return columns; }
    public void addTableRow(List<String> row) {
        if (row != null) {
            tableData.add(row);
            columns = Math.max(columns, row.size());
            rows++;
        }
    }

    // List management
    public List<String> getListItems() { return listItems; }
    public void addListItem(String item) {
        if (item != null) listItems.add(item);
    }

    // Type checks
    public boolean isHeading() {
        return type == ElementType.HEADING_1 || 
               type == ElementType.HEADING_2 || 
               type == ElementType.HEADING_3;
    }

    public boolean isSection() {
        return type == ElementType.SECTION_TITLE;
    }

    public boolean isTextElement() {
        return type == ElementType.PARAGRAPH ||
               type == ElementType.HEADING_1 ||
               type == ElementType.HEADING_2 ||
               type == ElementType.HEADING_3 ||
               type == ElementType.QUOTE;
    }

    public boolean isList() {
        return type == ElementType.BULLET_LIST || 
               type == ElementType.NUMBERED_LIST;
    }

    @Override
    public String toString() {
        switch (type) {
            case IMAGE:
                return String.format("[IMAGE] ID: %s", imageUrl);
            case HYPERLINK:
                return String.format("[LINK] %s → %s", text, linkUrl);
            case TABLE:
                return String.format("[TABLE] %dx%d", rows, columns);
            case BULLET_LIST:
            case NUMBERED_LIST:
                return String.format("[LIST] %d items", listItems.size());
            case SECTION_TITLE:
                return String.format("[SECTION %d] %s", sectionLevel + 1, text);
            default:
                return String.format("[%s] %s", type, text);
        }
    }
}