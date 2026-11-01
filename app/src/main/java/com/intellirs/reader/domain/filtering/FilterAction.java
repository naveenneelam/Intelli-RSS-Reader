package com.intellirs.reader.domain.filtering;

public class FilterAction {
    public FilterActionType type = FilterActionType.MUTE_NOTIFICATION;
    public String value = "";

    public FilterAction() {
    }

    public FilterAction(FilterActionType type, String value) {
        this.type = type;
        this.value = value;
    }
}
