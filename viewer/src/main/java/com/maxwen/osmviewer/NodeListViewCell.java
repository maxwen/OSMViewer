package com.maxwen.osmviewer;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class NodeListViewCell extends ListCell<RoutingNode> {

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
    private MainController mController;

    public NodeListViewCell(MainController controller) {
        super();
        mController = controller;
    }

    private ContextMenu buildContextMenu(RoutingNode node) {
        ContextMenu menu = new ContextMenu();
        if (node != null) {
            MenuItem menuItem = new MenuItem(" Remove");
            menuItem.setOnAction(ev -> {
                mController.removeCustomNode(node);
            });
            menuItem.setStyle("-fx-font-size: 20");
            menu.getItems().add(menuItem);
        }
        return menu;
    }

    @Override
    protected void updateItem(RoutingNode node, boolean empty) {
        super.updateItem(node, empty);

        if (empty || node == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (mLLoader == null) {
                mLLoader = new FXMLLoader(getClass().getResource("queryitem.fxml"));
                mLLoader.setController(this);

                try {
                    mLLoader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            mainText.setText(node.getTitle());
            subText.setText(node.getName());
            image.setImage(node.getImage());
            setContextMenu(buildContextMenu(node));

            setText(null);
            setGraphic(gridPane);
        }
    }
}
