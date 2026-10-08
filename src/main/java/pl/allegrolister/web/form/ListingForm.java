package pl.allegrolister.web.form;

import java.util.ArrayList;
import java.util.List;

/**
 * Formularz wystawiania - lista edytowanych pozycji (wiązanie items[i].pole).
 */
public class ListingForm {

    private List<ItemForm> items = new ArrayList<>();

    public List<ItemForm> getItems() { return items; }
    public void setItems(List<ItemForm> items) { this.items = items; }
}
