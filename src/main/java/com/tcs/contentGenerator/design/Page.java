package com.tcs.contentGenerator.design;

import java.util.List;

/** One page of the issue: its components in z-order (list order = paint order). */
public record Page(String id, List<Component> components) {

    public Page {
        components = components == null ? List.of() : List.copyOf(components);
    }
}
