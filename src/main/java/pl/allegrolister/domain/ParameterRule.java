package pl.allegrolister.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reguła uzupełniania parametru Allegro. Przykłady:
 * <ul>
 *   <li>parametr "Kolor" = cecha "Kolor" z mapowaniem wartości "red=czerwony"</li>
 *   <li>parametr "Stan" = stała "Nowy"</li>
 *   <li>parametr "Kolor" = "odcienie czerwieni", gdy nazwa zawiera "czerw*"</li>
 * </ul>
 */
@Entity
@Table(name = "parameter_rule")
public class ParameterRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kategoria Allegro, której dotyczy reguła; puste = wszystkie kategorie. */
    private String allegroCategoryId;

    /** Nazwa parametru Allegro (np. "Kolor"). */
    private String parameterName;

    /** ID parametru Allegro (opcjonalne - dokładniejsze niż nazwa). */
    private String parameterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type")
    private RuleType ruleType = RuleType.FROM_FEATURE;

    /** Dla FROM_FEATURE: nazwa cechy produktu albo pole specjalne (@ean, @sku, @producent, @nazwa, @waga, @kategoria). */
    private String featureName;

    /** Dla CONSTANT i CONSTANT_IF_CONTAINS: wartość parametru. */
    @Column(name = "rule_value", length = 1000)
    private String value;

    @Enumerated(EnumType.STRING)
    private MatchField matchField = MatchField.NAME;

    /** Fraza do wyszukania; * = dowolny ciąg znaków (np. "czerw*"). */
    private String phrase;

    /** Mapowanie wartości: "red=czerwony;blue=niebieski". */
    @Column(length = 4000)
    private String valueMap;

    /** Niższa liczba = wyższy priorytet. */
    private int priority = 100;

    private boolean enabled = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAllegroCategoryId() { return allegroCategoryId; }
    public void setAllegroCategoryId(String allegroCategoryId) { this.allegroCategoryId = allegroCategoryId; }
    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }
    public String getParameterId() { return parameterId; }
    public void setParameterId(String parameterId) { this.parameterId = parameterId; }
    public RuleType getRuleType() { return ruleType; }
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }
    public String getFeatureName() { return featureName; }
    public void setFeatureName(String featureName) { this.featureName = featureName; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public MatchField getMatchField() { return matchField; }
    public void setMatchField(MatchField matchField) { this.matchField = matchField; }
    public String getPhrase() { return phrase; }
    public void setPhrase(String phrase) { this.phrase = phrase; }
    public String getValueMap() { return valueMap; }
    public void setValueMap(String valueMap) { this.valueMap = valueMap; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
