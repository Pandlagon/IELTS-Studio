package com.ieltsstudio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TranslateRequest {

    @NotBlank
    private String passage;

    @NotBlank
    private String selectedText;
}
