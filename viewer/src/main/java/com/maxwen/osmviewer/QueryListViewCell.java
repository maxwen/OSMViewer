package com.maxwen.osmviewer;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class QueryListViewCell extends ListCell<QueryItem> {

    @FXML
    private VBox text;
    @FXML
    private Label mainText;
    @FXML
    private Label subText;
    @FXML
    private ImageView image;
    @FXML
    private GridPane gridPane;
    private FXMLLoader mLLoader;

    @Override
    protected void updateItem(QueryItem item, boolean empty) {
        super.updateItem(item, empty);

        if(empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (mLLoader == null) {
                mLLoader = new FXMLLoader(getClass().getResource("ListCell.fxml"));
                mLLoader.setController(this);

                try {
                    mLLoader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            mainText.setText(item.getName());
            if (item.getTags().length() != 0) {
                subText.setText(item.getTags());
            } else {
                text.getChildren().remove(subText);
            }
            image.setImage(item.getImage());

            setText(null);
            setGraphic(gridPane);
        }
    }
}
