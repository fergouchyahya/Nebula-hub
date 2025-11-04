package nebula.ui.core;

public interface Step {
    String id();

    String title();

    String descriptionHtml(); // explication riche (HTML simple)

    void perform() throws Exception; // action de l’étape

    default void rollback() throws Exception {
    } // optionnel
}
