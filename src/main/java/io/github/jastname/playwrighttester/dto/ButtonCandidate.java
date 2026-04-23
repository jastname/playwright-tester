package io.github.jastname.playwrighttester.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ButtonCandidate {
    private String text;
    private String tag;
    private String id;
    private String name;
    private String type;
    private String selector;
}