package com.intellirs.reader.domain.filtering;

public class FilterCondition {
    public FilterField field = FilterField.TITLE;
    public FilterOperator operator = FilterOperator.CONTAINS;
    public String value = "";

    public FilterCondition() {
    }

    public FilterCondition(FilterField field, FilterOperator operator, String value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }
}
