package pl.allegrolister.web.form;

import java.util.ArrayList;
import java.util.List;

import pl.allegrolister.domain.TemplateSection;

/**
 * Formularz edycji szablonu opisu.
 */
public class TemplateForm {

    private Long id;
    private String name;
    private List<TemplateSection> sections = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<TemplateSection> getSections() { return sections; }
    public void setSections(List<TemplateSection> sections) { this.sections = sections; }
}
