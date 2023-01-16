package com.maxwen.osmviewer;

import com.maxwen.osmviewer.shared.Config;
import com.maxwen.osmviewer.shared.LogUtils;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
    private MainController mController;
    private Scene mScene;

    @Override
    public void start(Stage primaryStage) throws Exception {
        LogUtils.log("start");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("osmviewer.fxml"));
        Parent root = loader.load();

        mController = loader.getController();
        mController.setStage(primaryStage);
        primaryStage.setTitle("OSM");
        mScene = new Scene(root, 1280, 720);
        PerspectiveCamera camera = new PerspectiveCamera();
        mScene.setCamera(camera);
        // to get sizes in setScene
        root.layout();
        mController.setScene(mScene);
        primaryStage.setScene(mScene);
        primaryStage.show();
        primaryStage.getIcons().add(new Image("/images/launcher.png"));

        mController.loadMapData();
    }

    @Override
    public void init() throws Exception {
        super.init();
        LogUtils.log("init");
        QueryController.getInstance().connectAll();
    }

    @Override
    public void stop() {
        LogUtils.log("stop");
        mController.stop();
        QueryController.getInstance().disconnectAll();
        Config.getInstance().save();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
