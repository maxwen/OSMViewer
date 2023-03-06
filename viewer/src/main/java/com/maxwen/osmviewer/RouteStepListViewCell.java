package com.maxwen.osmviewer;

import com.maxwen.osmviewer.model.RouteStep;
import com.maxwen.osmviewer.model.RoutingNode;
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

public class RouteStepListViewCell extends ListCell<RouteStep> {

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

    public RouteStepListViewCell(MainController controller) {
        super();
        mController = controller;
    }

    @Override
    protected void updateItem(RouteStep node, boolean empty) {
        super.updateItem(node, empty);

        if (empty || node == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (mLLoader == null) {
                mLLoader = new FXMLLoader(getClass().getResource("routestep.fxml"));
                mLLoader.setController(this);

                try {
                    mLLoader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            mainText.setText(node.getName());
            subText.setText(node.getLength() + " km");
            image.setImage(node.getImage());

            setText(null);
            setGraphic(gridPane);
        }
    }
}
