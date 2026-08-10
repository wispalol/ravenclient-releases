package org.ravenclient.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import org.ravenclient.auth.Account;
import org.ravenclient.skin.SkinService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The Skins page. Lets the signed-in player:
 *
 * <ul>
 *   <li>preview their current skin (head render via visage.surgeplay.com);</li>
 *   <li>upload a 64x32 PNG from their PC (multipart upload to the Minecraft services API);</li>
 *   <li>search another player by in-game name and copy their skin;</li>
 *   <li>reset back to the default Steve / Alex skin.</li>
 * </ul>
 */
public class SkinManagerUI {

    private final Account account;
    private final ExecutorService pool;
    private final Consumer<String> log;
    private final Runnable onSkinChanged;

    private final SkinService skin = new SkinService();

    private Label ownModel;
    private Label status;
    private ImageView ownAvatar = new ImageView();
    private Label ownName;
    private Button resetButton;

    private TextField searchField;
    private Button searchButton;
    private ImageView resultAvatar = new ImageView();
    private Label resultName;
    private Label resultModel;
    private Button applyButton;
    private VBox resultCard;
    private SkinService.LookupProfile searchedProfile;

    private ComboBox<String> variantCombo;
    private Button uploadButton;

    public SkinManagerUI(Account account, ExecutorService pool, Consumer<String> log, Runnable onSkinChanged) {
        this.account = account;
        this.pool = pool;
        this.log = log;
        this.onSkinChanged = onSkinChanged;
    }

    public Node build() {
        if (account == null || account.minecraftToken() == null || account.minecraftToken().isBlank()) {
            return buildSignedOut();
        }

        Label title = new Label("Skins");
        title.getStyleClass().add("page-title");

        VBox left = buildOwnPanel();
        VBox right = new VBox(14, buildCopyPanel(), buildUploadPanel());

        HBox columns = new HBox(14, left, right);
        columns.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(left, Priority.NEVER);
        HBox.setHgrow(right, Priority.ALWAYS);

        status = new Label("Loading your current skin...");
        status.getStyleClass().add("muted");
        status.setWrapText(true);

        VBox body = new VBox(16, title, columns, status);
        body.setPadding(new Insets(22, 32, 22, 32));

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        loadOwnSkin();
        return new StackPane(scroll);
    }

    private Node buildSignedOut() {
        Label title = new Label("Skins");
        title.getStyleClass().add("page-title");
        Label msg = new Label("Sign in with your Microsoft account to change or upload your Minecraft skin.");
        msg.setWrapText(true);
        msg.getStyleClass().add("muted");
        VBox body = new VBox(14, title, msg);
        body.setPadding(new Insets(22, 32, 22, 32));
        return new StackPane(new ScrollPane(body));
    }

    // --- panels ------------------------------------------------------------

    private VBox buildOwnPanel() {
        VBox card = new VBox(12);
        card.getStyleClass().add("glass-panel");
        card.setPrefWidth(280);

        Label cardTitle = new Label("Your skin");
        cardTitle.getStyleClass().add("side-card-title");

        StackPane ownAvatarBox = new StackPane();
        ownAvatarBox.setMinSize(120, 120);
        ownAvatarBox.setPrefSize(120, 120);
        ownAvatarBox.setMaxSize(120, 120);
        ownAvatarBox.getStyleClass().add("player-avatar-box");

        Label placeholder = new Label("\u263A");
        placeholder.getStyleClass().add("skin-avatar-placeholder");
        placeholder.setMinSize(108, 108);
        placeholder.setPrefSize(108, 108);
        placeholder.setMaxSize(108, 108);
        placeholder.setAlignment(Pos.CENTER);

        ownAvatar.setFitWidth(116);
        ownAvatar.setFitHeight(116);
        ownAvatar.setPreserveRatio(true);
        Rectangle clip = new Rectangle(116, 116);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        ownAvatar.setClip(clip);

        ownAvatarBox.getChildren().addAll(placeholder, ownAvatar);

        ownName = new Label(account != null && account.username() != null ? account.username() : "");
        ownName.getStyleClass().add("player-name");
        ownName.setAlignment(Pos.CENTER);
        ownName.setMaxWidth(Double.MAX_VALUE);

        ownModel = new Label("Loading...");
        ownModel.getStyleClass().add("muted");

        resetButton = new Button("Reset to default");
        resetButton.getStyleClass().add("secondary-button");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setTooltip(new Tooltip("Restore the default Steve / Alex skin"));
        resetButton.setOnAction(e -> resetOwnSkin());

        card.getChildren().addAll(cardTitle, ownAvatarBox, ownName, ownModel, resetButton);
        return card;
    }

    private VBox buildCopyPanel() {
        VBox card = new VBox(10);
        card.getStyleClass().add("glass-panel");

        Label cardTitle = new Label("Copy another player's skin");
        cardTitle.getStyleClass().add("side-card-title");

        searchField = new TextField();
        searchField.setPromptText("Enter an in-game name or UUID...");
        searchField.getStyleClass().add("search-box");
        searchButton = new Button("Search");
        searchButton.getStyleClass().add("primary-button");
        searchButton.setOnAction(e -> searchPlayer());

        HBox searchRow = new HBox(10, searchField, searchButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        resultCard = buildResultCard();
        resultCard.setManaged(false);
        resultCard.setVisible(false);

        card.getChildren().addAll(cardTitle, searchRow, resultCard);
        return card;
    }

    private VBox buildResultCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("mod-card");

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane resultAvatarBox = new StackPane();
        resultAvatarBox.setMinSize(56, 56);
        resultAvatarBox.setPrefSize(56, 56);
        resultAvatarBox.setMaxSize(56, 56);
        resultAvatarBox.getStyleClass().add("player-avatar-box");

        Label placeholder = new Label("?");
        placeholder.getStyleClass().add("skin-avatar-placeholder");
        placeholder.setMinSize(48, 48);
        placeholder.setPrefSize(48, 48);
        placeholder.setMaxSize(48, 48);
        placeholder.setAlignment(Pos.CENTER);

        resultAvatar.setFitWidth(54);
        resultAvatar.setFitHeight(54);
        resultAvatar.setPreserveRatio(true);
        Rectangle clip = new Rectangle(54, 54);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        resultAvatar.setClip(clip);

        resultAvatarBox.getChildren().addAll(placeholder, resultAvatar);

        VBox info = new VBox(4);
        resultName = new Label();
        resultName.getStyleClass().add("mod-title");
        resultName.setTextOverrun(OverrunStyle.ELLIPSIS);
        resultName.setMaxWidth(240);
        resultModel = new Label();
        resultModel.getStyleClass().add("mod-author");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        applyButton = new Button("Apply this skin");
        applyButton.getStyleClass().add("mod-install-button");
        applyButton.setOnAction(e -> applySearchedSkin());

        row.getChildren().addAll(resultAvatarBox, info, spacer, applyButton);
        info.getChildren().addAll(resultName, resultModel);
        card.getChildren().add(row);
        return card;
    }

    private VBox buildUploadPanel() {
        VBox card = new VBox(10);
        card.getStyleClass().add("glass-panel");

        Label cardTitle = new Label("Upload from your PC");
        cardTitle.getStyleClass().add("side-card-title");

        Label hint = new Label("Pick a 64x32 (or 64x64) PNG skin file and the model it uses.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");

        variantCombo = new ComboBox<>();
        variantCombo.getItems().addAll(List.of("Classic (Steve)", "Slim (Alex)"));
        variantCombo.setValue("Classic (Steve)");
        variantCombo.getStyleClass().add("modern-combobox");

        uploadButton = new Button("Choose skin file...");
        uploadButton.getStyleClass().add("secondary-button");
        uploadButton.setMaxWidth(Double.MAX_VALUE);
        uploadButton.setOnAction(e -> uploadSkin());

        HBox variantRow = new HBox(10, new Label("Model:"), variantCombo);
        variantRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(cardTitle, hint, variantRow, uploadButton);
        return card;
    }

    // --- actions -----------------------------------------------------------

    private void loadOwnSkin() {
        setStatus("Loading your current skin...");
        loadAvatar(ownAvatar, account.uuid());
        String token = account.minecraftToken();
        pool.execute(() -> {
            try {
                SkinService.OwnProfile profile = skin.ownProfile(token);
                SkinService.SkinInfo active = profile.activeSkin();
                Platform.runLater(() -> {
                    ownName.setText(profile.name());
                    ownModel.setText(active == null || active.url() == null || active.url().isBlank()
                            ? "Default skin"
                            : "Model: " + displayModel(active.variant()));
                    setStatus("Loaded your current skin.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    ownModel.setText("Could not load skin");
                    setStatus("Could not load your skin: " + ex.getMessage());
                    log.accept("Skin load error: " + ex);
                });
            }
        });
    }

    private void searchPlayer() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            setStatus("Enter a player name to search.");
            return;
        }
        setSearching(true);
        setStatus("Looking up \"" + query.trim() + "\"...");
        pool.execute(() -> {
            try {
                SkinService.LookupProfile lookup = skin.lookupName(query);
                SkinService.SkinTexture texture = skin.skinFor(lookup.uuid());
                Platform.runLater(() -> {
                    searchedProfile = lookup;
                    resultName.setText(lookup.name());
                    resultModel.setText("Model: " + (texture.model().startsWith("slim") ? "Slim (Alex)" : "Classic (Steve)"));
                    loadAvatar(resultAvatar, lookup.uuid(), 54);
                    showResultCard(true);
                    setStatus("Found \"" + lookup.name() + "\" - you can apply their skin.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showResultCard(false);
                    searchedProfile = null;
                    setStatus(ex.getMessage());
                    log.accept("Skin lookup error: " + ex);
                });
            } finally {
                Platform.runLater(() -> setSearching(false));
            }
        });
    }

    private void applySearchedSkin() {
        if (searchedProfile == null) return;
        setApplyBusy(true);
        setStatus("Applying " + searchedProfile.name() + "'s skin to your account...");
        String token = account.minecraftToken();
        pool.execute(() -> {
            try {
                skin.copySkin(token, searchedProfile.name());
                Platform.runLater(() -> {
                    setStatus("Skin applied! It will show in-game on your next launch.");
                    log.accept("Applied skin copied from " + searchedProfile.name() + ".");
                    onSkinChanged.run();
                    loadOwnSkin();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("Could not apply skin: " + ex.getMessage());
                    log.accept("Skin apply error: " + ex);
                });
            } finally {
                Platform.runLater(() -> setApplyBusy(false));
            }
        });
    }

    private void uploadSkin() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a skin image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG skins (*.png)", "*.png"));
        File picked = chooser.showOpenDialog(null);
        if (picked == null) return;

        String problem = validateSkin(picked);
        if (problem != null) {
            setStatus(problem);
            return;
        }

        String variant = variantCombo.getValue() == null || variantCombo.getValue().startsWith("Slim")
                ? "slim" : "classic";
        uploadButton.setDisable(true);
        uploadButton.setText("Uploading...");
        setStatus("Uploading " + picked.getName() + "...");
        String token = account.minecraftToken();
        pool.execute(() -> {
            try {
                skin.setSkinFile(token, picked.toPath(), variant);
                Platform.runLater(() -> {
                    setStatus("Skin uploaded! It will show in-game on your next launch.");
                    log.accept("Uploaded skin " + picked.getName() + " (" + variant + ").");
                    onSkinChanged.run();
                    loadOwnSkin();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("Upload failed: " + ex.getMessage());
                    log.accept("Skin upload error: " + ex);
                });
            } finally {
                Platform.runLater(() -> {
                    uploadButton.setDisable(false);
                    uploadButton.setText("Choose skin file...");
                });
            }
        });
    }

    private void resetOwnSkin() {
        resetButton.setDisable(true);
        setStatus("Resetting your skin to the default...");
        String token = account.minecraftToken();
        pool.execute(() -> {
            try {
                skin.resetSkin(token);
                Platform.runLater(() -> {
                    setStatus("Skin reset to the default. It will show in-game on your next launch.");
                    log.accept("Reset skin to default.");
                    onSkinChanged.run();
                    loadOwnSkin();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Could not reset skin: " + ex.getMessage()));
            } finally {
                Platform.runLater(() -> resetButton.setDisable(false));
            }
        });
    }

    // --- helpers -----------------------------------------------------------

    private void loadAvatar(ImageView view, String uuid) {
        loadAvatar(view, uuid, 116);
    }

    private void loadAvatar(ImageView view, String uuid, int size) {
        String clean = uuid == null ? "" : uuid.replace("-", "");
        if (clean.isBlank()) return;
        String url = "https://visage.surgeplay.com/head/" + clean;
        pool.execute(() -> {
            try {
                Image img = new Image(url, size, size, true, true);
                Platform.runLater(() -> {
                    if (img.getWidth() > 0) view.setImage(img);
                });
            } catch (Exception ignored) {
                // keep the placeholder
            }
        });
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }

    private void setSearching(boolean value) {
        searchButton.setDisable(value);
        searchField.setDisable(value);
    }

    private void setApplyBusy(boolean value) {
        applyButton.setDisable(value);
    }

    private void showResultCard(boolean show) {
        resultCard.setVisible(show);
        resultCard.setManaged(show);
    }

    private static String validateSkin(File file) {
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            return "Choose a PNG image.";
        }
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) return "That file is not a readable PNG image.";
            int w = img.getWidth();
            int h = img.getHeight();
            if (w != 64 || (h != 32 && h != 64)) {
                return "Skin must be 64x32 (or 64x64) pixels - this one is " + w + "x" + h + ".";
            }
            return null;
        } catch (IOException e) {
            return "Could not read that image: " + e.getMessage();
        }
    }

    private static String displayModel(String variant) {
        if (variant == null) return "Classic (Steve)";
        String v = variant.toUpperCase(Locale.ROOT);
        return v.contains("SLIM") ? "Slim (Alex)" : "Classic (Steve)";
    }
}
